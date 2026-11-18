package dev.sablelife.testing;

import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.sablelife.SableLife;
import dev.sablelife.SableLifeServerConfig;
import dev.sablelife.heart.*;
import dev.sablelife.module.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("sablelife_redstone_preview_tests") @PrefixGameTestTemplate(false)
public final class OutputFaceLimitGameTests {
    private record Fixture(HeartBlockEntity core, List<ModuleAnchor> marks) {}
    private static ModConfigSpec.IntValue limit() {
        return (ModConfigSpec.IntValue) SableLifeServerConfig.SPEC.getValues().get("maximumRedstoneOutputFaces");
    }
    private static Fixture fixture(GameTestHelper h) {
        var level=h.getLevel();var start=h.absolutePos(new BlockPos(3,40,3));var blocks=new ArrayList<BlockPos>();
        for(int i=0;i<11;i++){var p=start.north(i);level.setBlockAndUpdate(p,i==0?SableLife.REDSTONE_CORE.get().defaultBlockState():Blocks.IRON_BLOCK.defaultBlockState());blocks.add(p);}
        var body=SubLevelAssemblyHelper.assembleBlocks(level,start,blocks,new BoundingBox3i(start.getX(),start.getY(),start.getZ()-10,start.getX(),start.getY(),start.getZ()));
        var core=(HeartBlockEntity)level.getBlockEntity(body.getPlot().getCenterBlock());
        h.assertTrue(core.wireless().add()==0,"Could not create the fixture channel");var marks=new ArrayList<ModuleAnchor>();
        for(int i=0;i<11;i++)for(var face:Direction.values())marks.add(ModuleAnchor.create(core,core.getBlockPos().north(i),face));
        return new Fixture(core,List.copyOf(marks));
    }
    private static ModuleNetworking.Action packet(GameTestHelper h,int menu,int revision,UUID id,String operation,String key,int index) {
        var request=new ModuleNetworking.Action(menu,revision,id,operation,key,index,"");
        var bytes=new RegistryFriendlyByteBuf(Unpooled.buffer(),h.getLevel().registryAccess());
        try{ModuleNetworking.Action.CODEC.encode(bytes,request);return ModuleNetworking.Action.CODEC.decode(bytes);}finally{bytes.release();}
    }
    private static boolean moduleMark(GameTestHelper h,ServerPlayer player,HeartBlockEntity core,VirtualModule module,int index) {
        var menu=new HeartDashboardMenu(1,player.getInventory(),core);
        var p=packet(h,1,core.modules().revision(),module.id(),"mark",ModuleRedstone.OUTPUT_FACE,index);
        return menu.action(player,p.revision(),p.module(),p.operation(),p.key(),p.value(),p.text());
    }
    private static boolean channelMark(GameTestHelper h,ServerPlayer player,HeartBlockEntity core,int index) {
        var menu=new HeartWirelessMenu(2,player.getInventory(),core,0,0);
        var p=packet(h,2,core.wireless().faceRevision(),new UUID(0,0),"wire_face","mark",index);
        return menu.outputAction(player,p.revision(),p.key(),p.value(),p.text());
    }
    @GameTest(template="combat",batch="output_face_limit_config")
    public static void OldConfigDefaultsTo16AndExplicitLimitsSurviveReload(GameTestHelper h) {
        var config=new TomlParser().parse("maximumModulesPerCreature = 128\n");
        SableLifeServerConfig.LOADER_SPEC.correct(config);
        h.assertTrue(config.getInt("maximumRedstoneOutputFaces")==16&&config.getInt("maximumModulesPerCreature")==128,"Wrong default or changed old settings");
        config.set("maximumRedstoneOutputFaces",64);
        var restored=new TomlParser().parse(new TomlWriter().writeToString(config));
        SableLifeServerConfig.LOADER_SPEC.correct(restored);
        SableLifeServerConfig.LOADER_SPEC.correct(restored);
        h.assertTrue(restored.getInt("maximumRedstoneOutputFaces")==64,"Explicit maximum was reset on reload");
        restored.set("maximumRedstoneOutputFaces",1);h.assertTrue(SableLifeServerConfig.LOADER_SPEC.isCorrect(restored),"Minimum rejected");
        for(int invalid:new int[]{0,65}){restored.set("maximumRedstoneOutputFaces",invalid);h.assertTrue(!SableLifeServerConfig.LOADER_SPEC.isCorrect(restored),"Out-of-range maximum accepted");SableLifeServerConfig.LOADER_SPEC.correct(restored);}
        h.succeed();
    }
    @GameTest(template="combat",batch="output_face_limit_authoring")
    public static void BothEditorsEnforce16AndRecheckPendingMarks(GameTestHelper h) {
        var setting=limit();int previous=setting.get();var f=fixture(h);var c=f.core();var player=TestActors.creative(h.getLevel());
        player.setPos(f.marks().getFirst().world(c,false));
        try {
            setting.set(16);var channel=c.wireless().channel(0);var module=c.modules().add("wireless_redstone");
            for(int i=0;i<16;i++)h.assertTrue(channel.setOutputFace(i,f.marks().get(i)),"Default refused face "+(i+1));
            module.anchors(f.marks().subList(0,16));
            h.assertTrue(new HeartDashboardMenu(1,player.getInventory(),c).snapshot().getInt("OutputFaceLimit")==16
                    &&new HeartWirelessMenu(2,player.getInventory(),c,0,0).outputFaceLimit()==16,"Editor did not publish server maximum");
            h.assertTrue(!moduleMark(h,player,c,module,16)&&!channelMark(h,player,c,16)&&!channel.setOutputFace(16,f.marks().get(16)),"Default accepted a 17th face");
            setting.set(32);h.assertTrue(moduleMark(h,player,c,module,16),"Raised module maximum did not allow marking");
            setting.set(16);var target=f.marks().get(16);
            h.assertTrue(!MarkingSessions.select(player,target.position(target.body(c)),target.face())&&module.redstoneFaceCount()==16,"Pending module mark bypassed lowered maximum");
            MarkingSessions.cancel(player);
            setting.set(32);h.assertTrue(channelMark(h,player,c,16),"Raised channel maximum did not allow marking");
            setting.set(16);h.assertTrue(!MarkingSessions.select(player,target.position(target.body(c)),target.face())&&channel.outputFaces().size()==16,"Pending channel mark bypassed lowered maximum");
            MarkingSessions.cancel(player);
            setting.set(32);h.assertTrue(moduleMark(h,player,c,module,16)&&MarkingSessions.select(player,target.position(target.body(c)),target.face()),"Raised module maximum did not accept face 17");
            h.assertTrue(channelMark(h,player,c,16)&&MarkingSessions.select(player,target.position(target.body(c)),target.face()),"Raised channel maximum did not accept face 17");
            setting.set(16);
            h.assertTrue(moduleMark(h,player,c,module,16)&&MarkingSessions.select(player,target.position(target.body(c)),target.face()),"Could not remark an existing module face above the limit");
            h.assertTrue(channelMark(h,player,c,16)&&MarkingSessions.select(player,target.position(target.body(c)),target.face()),"Could not remark an existing channel face above the limit");
            h.assertTrue(!moduleMark(h,player,c,module,17)&&!channelMark(h,player,c,17),"Over-limit existing records allowed additions");
        } finally {MarkingSessions.cancel(player);setting.set(previous);}
        h.succeed();
    }
    @GameTest(template="combat",batch="output_face_limit_legacy")
    public static void Legacy64ChannelFacesAndNamesKeepWorkingAt16(GameTestHelper h) {
        var setting=limit();int previous=setting.get();var f=fixture(h);var c=f.core();
        try {
            setting.set(64);var channel=c.wireless().channel(0);channel.set(0,1);
            for(int i=0;i<64;i++)h.assertTrue(channel.setOutputFace(i,f.marks().get(i)),"Configured 64 refused a face");
            h.assertTrue(channel.renameOutputFace(63,"Face 64 · 旧标记"),"Legacy name refused");
            var saved=c.wireless().save(h.getLevel().registryAccess());
            setting.set(16);c.wireless().load(saved,h.getLevel().registryAccess());
            var loaded=c.wireless().channel(0);
            h.assertTrue(loaded.outputFaces().equals(f.marks().subList(0,64))&&loaded.outputFaceName(63).equals("Face 64 · 旧标记"),"Old faces or names were truncated/reordered on load");
            h.assertTrue(loaded.setOutputFace(63,f.marks().get(63))&&!loaded.setOutputFace(64,f.marks().get(64)),"Wrong legacy edit/add behavior");
            var signals=new HashMap<ModuleSignals.Face,Integer>();c.wireless().collectOutputFaces(signals);
            h.assertTrue(signals.size()==64&&signals.values().stream().allMatch(v->v>0),"Old channel faces stopped publishing after lowering the limit");
            h.assertTrue(loaded.removeOutputFace(63)&&loaded.outputFaces().size()==63,"Could not remove an old over-limit face");
            var roundTrip=WirelessChannel.load(loaded.save(h.getLevel().registryAccess()),h.getLevel().registryAccess());
            h.assertTrue(roundTrip.outputFaces().size()==63,"Second save/reload lost retained faces");
        } finally {setting.set(previous);}
        h.succeed();
    }
}
