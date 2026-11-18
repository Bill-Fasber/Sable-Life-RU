package dev.sablelife.client;
import dev.sablelife.SableLife;
import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
@EventBusSubscriber(modid=SableLife.MOD_ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class ArmorRuleScreen extends AbstractContainerScreen<ArmorRuleMenu> {
 private final List<Button> groupRows=new ArrayList<>();private List<ArmorRules.Row> groups=List.of();
 private final List<ModifierEditor> modifiers=new ArrayList<>(3);
 private net.minecraft.nbt.ListTag lastConfig;private UUID selected;private int page,rows,column;private EditBox channel;private Button add,remove,up,down,enabled,source,when,apply,prev,next;
 private record ModifierEditor(ArmorRules.HitType type,String label,String valueKey,String overrideKey,EditBox input,Button apply,Button toggle){}
 public ArmorRuleScreen(ArmorRuleMenu menu,Inventory inv,Component title){super(menu,inv,title);}
 @SubscribeEvent public static void register(RegisterMenuScreensEvent event){event.register(SableLife.ARMOR_RULE_MENU.get(),ArmorRuleScreen::new);}
 private Component tr(String k){return Component.translatable("armor_rules.sablelife."+k);}
 private Button button(Component label,int x,int y,int w,Runnable action){return addRenderableWidget(LifeUiStyle.button(label,leftPos+x,topPos+y,w,18,action));}
 private ArmorRules.Row current(){return groups.stream().filter(g->g.id().equals(selected)).findFirst().orElse(null);}
 private void send(String key,double value){PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,menu.snapshot().getInt("Revision"),selected==null?new UUID(0,0):selected,"armor_rule_edit",key,value,""));}
 @Override protected void init(){imageWidth=Math.max(280,Math.min(410,width-12));imageHeight=Math.max(228,Math.min(340,height-12));super.init();groupRows.clear();modifiers.clear();column=imageWidth/2;rows=Math.max(2,(imageHeight-112)/22);
  button(tr("back"),imageWidth-65,8,55,()->minecraft.gameMode.handleInventoryButtonClick(menu.containerId,0));
  for(int i=0;i<rows;i++){final int row=i;groupRows.add(button(Component.empty(),10,40+i*22,column-22,()->{int at=page*rows+row;if(at<groups.size()){selected=groups.get(at).id();channel.setFocused(false);modifiers.forEach(editor->editor.input().setFocused(false));sync();}}));}
  prev=button(Component.literal("<"),10,imageHeight-62,24,()->{page--;sync();});next=button(Component.literal(">"),column-36,imageHeight-62,24,()->{page++;sync();});
  add=button(tr("add"),10,imageHeight-38,column-70,()->send("add",0));up=button(Component.literal("↑"),column-56,imageHeight-38,20,()->send("up",0));down=button(Component.literal("↓"),column-33,imageHeight-38,20,()->send("down",0));
  int first=imageHeight<270?34:50;
  enabled=button(Component.empty(),column+8,first,imageWidth-column-18,()->{var g=current();if(g!=null)send("enabled",g.enabled()?0:1);});
  source=button(Component.empty(),column+8,first+20,imageWidth-column-18,()->cycle(1));LifeUiStyle.reverse(source,()->cycle(-1));
  channel=addRenderableWidget(new EditBox(font,leftPos+column+30,topPos+first+52,imageWidth-column-74,18,tr("channel")));channel.setMaxLength(5);channel.setFilter(s->s.matches("[0-9]{0,5}"));
  apply=button(Component.literal("✓"),imageWidth-36,first+52,26,this::applyChannel);
  when=button(Component.empty(),column+8,first+72,imageWidth-column-18,()->{var g=current();if(g!=null)send("when",1-g.when());});
  addModifier(ArmorRules.HitType.OTHER,"modifier","modifier",null,first+104);
  addModifier(ArmorRules.HitType.MELEE,"melee_modifier","meleeModifier","meleeOverride",first+134);
  addModifier(ArmorRules.HitType.PROJECTILE,"projectile_modifier","projectileModifier","projectileOverride",first+164);
  remove=button(tr("delete"),imageWidth-125,8,55,()->send("delete",0));
  for(var b:List.of(enabled,source,when,apply))b.setTooltip(Tooltip.create(tr("help")));sync();
 }
 private void addModifier(ArmorRules.HitType type,String label,String valueKey,String overrideKey,int y){
  int x=column+30;
  Button toggle=null;
  if(overrideKey!=null){
   toggle=button(Component.empty(),column+8,y,18,()->{var row=current();if(row!=null)send(overrideKey,row.overrides(type)?0:1);});
   toggle.setTooltip(Tooltip.create(tr("override_help")));
  }
  var input=addRenderableWidget(new EditBox(font,leftPos+x,topPos+y,imageWidth-x-44,18,tr(label)));
  input.setMaxLength(24);
  input.setHint(tr("inherit"));
  var confirm=button(Component.literal("✓"),imageWidth-36,y,26,()->applyModifier(valueKey,input));
  modifiers.add(new ModifierEditor(type,label,valueKey,overrideKey,input,confirm,toggle));
 }
 private void cycle(int delta){var g=current();if(g!=null)send("source",Math.floorMod(g.source()+delta,2));}
 private void applyChannel(){try{int n=Integer.parseInt(channel.getValue());if(n>=0&&n<=65535){send("channel",n);channel.setFocused(false);}}catch(NumberFormatException e){channel.setTextColor(0xFF7777);}}
 private void sync(){var data=menu.snapshot();var before=groups;var saved=data.getList("Groups",10);boolean hadConfig=lastConfig!=null,changed=!saved.equals(lastConfig);
  if(changed){var config=new ArmorRules();config.load(saved);groups=config.rows();lastConfig=saved.copy();}
  if(selected==null&&data.hasUUID("Selected"))selected=data.getUUID("Selected");
  if(hadConfig&&changed&&groups.size()>before.size()){selected=groups.getLast().id();page=(groups.size()-1)/rows;}
  if(current()==null)selected=groups.isEmpty()?null:groups.getFirst().id();
  page=Math.clamp(page,0,Math.max(0,(groups.size()-1)/rows));
  for(int i=0;i<rows;i++){var button=groupRows.get(i);int at=page*rows+i;button.visible=at<groups.size();if(button.visible)button.setMessage(Component.literal(groups.get(at).id().equals(selected)?"> ":"").append(Component.translatable("armor_rules.sablelife.group",at+1)).append(" · "+groups.get(at).channel()+" · "+String.format(Locale.ROOT,"%+.0f%%",groups.get(at).modifier())));}
  var g=current();boolean editable=data.getBoolean("Ready")&&data.getBoolean("Editable"),has=g!=null;
  add.active=editable&&groups.size()<ArmorRules.LIMIT;remove.active=enabled.active=source.active=when.active=editable&&has;
  up.active=editable&&has&&groups.indexOf(g)>0;down.active=editable&&has&&groups.indexOf(g)+1<groups.size();
  apply.active=channel.active=editable&&has;channel.setEditable(channel.active);
  prev.active=page>0;next.active=(page+1)*rows<groups.size();
  when.setMessage(tr("when_"+(has?g.when():0)));enabled.setMessage(tr(has&&g.enabled()?"enabled":"disabled"));source.setMessage(tr("source_"+(has?g.source():0)));
  for(var editor:modifiers){
   boolean override=has&&(editor.overrideKey()==null||g.overrides(editor.type()));
   var input=editor.input();
   editor.apply().active=input.active=editable&&override;
   input.setEditable(input.active);
   if(editor.toggle()!=null){
    editor.toggle().active=editable&&has;
    editor.toggle().setMessage(Component.literal(override?"✓":""));
   }
   if(!input.active&&input.isFocused())input.setFocused(false);
   if(!input.isFocused())input.setValue(override?formatModifier(g.modifier(editor.type())):"");
   if(survivalTooltip==null)survivalTooltip=Tooltip.create(tr("survival_help"));
   if(modifierTooltip==null)modifierTooltip=Tooltip.create(tr("modifier_help"));
   var tooltip=data.getBoolean("SurvivalCore")?survivalTooltip:modifierTooltip;
   if(input.getTooltip()!=tooltip){input.setTooltip(tooltip);editor.apply().setTooltip(tooltip);}
  }
  if(!channel.isFocused())channel.setValue(has?Integer.toString(g.channel()):"0");
 }
 private double minimumModifier(){var data=menu.snapshot();return data.contains("MinimumModifier")?data.getDouble("MinimumModifier"):-100;}
 private Tooltip survivalTooltip,modifierTooltip;
 private static String formatModifier(double value){String text=Double.toString(value);return text.endsWith(".0")?text.substring(0,text.length()-2):text;}
 private void applyModifier(String key,EditBox input){
  if(!input.active)return;
  try{double n=Double.parseDouble(input.getValue());if(Double.isFinite(n)&&n>=minimumModifier()&&n<=1000){send(key,n);input.setFocused(false);return;}}catch(NumberFormatException ignored){}
  input.setTextColor(0xFF7777);
 }
 @Override protected void containerTick(){super.containerTick();sync();}
 @Override public boolean keyPressed(int key,int scan,int mods){
  for(var editor:modifiers)if(editor.input().isFocused()){
   if(key==257){applyModifier(editor.valueKey(),editor.input());return true;}
   if(key!=256)return editor.input().keyPressed(key,scan,mods);
  }
  if(key==257&&channel.isFocused()){applyChannel();return true;}if(key!=256&&channel.isFocused())return channel.keyPressed(key,scan,mods);return super.keyPressed(key,scan,mods);
 }
 @Override public boolean mouseScrolled(double x,double y,double h,double v){
  for(var editor:modifiers){
   var input=editor.input();
   if(v==0||!input.active||!input.isMouseOver(x,y))continue;
   double n;try{n=Double.parseDouble(input.getValue());}catch(NumberFormatException e){n=0;}
   if(!Double.isFinite(n))n=0;
   input.setValue(formatModifier(Math.clamp(n+Math.signum(v)*5,minimumModifier(),1000)));applyModifier(editor.valueKey(),input);return true;
  }
  if(v!=0&&channel.active&&channel.isMouseOver(x,y)){int n;try{n=Integer.parseInt(channel.getValue());}catch(NumberFormatException e){n=0;}channel.setValue(Integer.toString(Math.clamp(n+(int)Math.signum(v),0,65535)));applyChannel();return true;}return super.mouseScrolled(x,y,h,v);
 }
 @Override protected void renderBg(GuiGraphics g,float partial,int x,int y){g.fill(leftPos-2,topPos-2,leftPos+imageWidth+2,topPos+imageHeight+2,0xFF19151D);g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF9B614E);g.fill(leftPos+3,topPos+3,leftPos+imageWidth-3,topPos+imageHeight-3,0xFF242632);g.fill(leftPos+column-2,topPos+32,leftPos+column,topPos+imageHeight-7,0xFFAA79C8);}
 @Override protected void renderLabels(GuiGraphics g,int x,int y){g.drawString(font,tr("title"),10,13,0xE6D8EA,false);g.drawString(font,tr("channel"),column+8,channel.getY()-topPos-11,0xD9C4ED,false);g.drawCenteredString(font,(page+1)+" / "+Math.max(1,(groups.size()+rows-1)/rows),column/2,imageHeight-57,0xD9C4ED);for(var editor:modifiers)g.drawString(font,tr(editor.label()),column+8,editor.input().getY()-topPos-10,0xD9C4ED,false);}

 @Override public void render(GuiGraphics g,int x,int y,float partial){sync();renderBackground(g,x,y,partial);super.render(g,x,y,partial);renderTooltip(g,x,y);}
}
