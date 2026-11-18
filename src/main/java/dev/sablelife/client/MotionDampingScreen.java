package dev.sablelife.client;

import dev.sablelife.module.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Edits ordinary stable module fields through the existing authorized dashboard protocol. */
public final class MotionDampingScreen extends Screen {
    private final Screen parent;private final HeartDashboardMenu menu;private final UUID id;
    private final List<EditBox> strengths=new ArrayList<>(),speeds=new ArrayList<>();
    private final List<Button> applies=new ArrayList<>();
    private VirtualModule module;private boolean editable;private int x,y,w,h,rowHeight,labelWidth,inputWidth,strengthX,speedX;
    private static final int rows=6;
    private final LinkedHashMap<String,Double> pending=new LinkedHashMap<>();
    private String waiting;private double waitingValue;private int waitingRevision,waitingAt,ticks;
    private boolean closing;
    public MotionDampingScreen(Screen parent,HeartDashboardMenu menu,UUID id){super(tr("title"));this.parent=parent;this.menu=menu;this.id=id;}
    private static Component tr(String key){return Component.translatable("damping.sablelife."+key);}
    private Button button(Component label,int bx,int by,int bw,Runnable action){return addRenderableWidget(LifeUiStyle.button(label,bx,by,bw,18,action));}
    @Override protected void init(){
        inputWidth=Math.max(52,font.width("128.000")+10);
        labelWidth=12;
        for(String direction:List.of("north","south","east","west","forward","backward","right","left","up","down"))
            labelWidth=Math.max(labelWidth,(int)Math.ceil(font.width(tr(direction))*LifeUiStyle.scale())+6);
        w=20+labelWidth+inputWidth*2+18+22;
        rowHeight=Math.clamp((height-78)/rows,18,22);h=62+rows*rowHeight;
        x=(width-w)/2;y=(height-h)/2;strengths.clear();speeds.clear();applies.clear();
        strengthX=x+10+labelWidth+6;speedX=strengthX+inputWidth+6;
        button(Component.translatable("dashboard.sablelife.back"),x+w-54,y+8,44,this::onClose);
        for(int row=0;row<rows;row++){
            final int i=row;int by=y+56+row*rowHeight;
            strengths.add(box(strengthX,by+1,inputWidth));speeds.add(box(speedX,by+1,inputWidth));
            applies.add(button(Component.literal("✓"),speedX+inputWidth+6,by,22,()->apply(i)));
        }
        sync();
    }
    private EditBox box(int bx,int by,int bw){var box=addRenderableWidget(new EditBox(font,bx,by,bw,16,Component.empty()));box.setMaxLength(9);box.setFilter(s->s.matches("[0-9]{0,4}(\\.[0-9]{0,3})?"));return box;}
    private void sync(){
        module=null;var snapshot=menu.snapshot();editable=snapshot.getBoolean("Editable");
        for(var item:snapshot.getList("Modules",10)){var candidate=VirtualModule.load((net.minecraft.nbt.CompoundTag)item);if(candidate.id().equals(id)&&candidate.type().equals("motion_damping"))module=candidate;}
        for(int i=0;i<rows;i++){
            int index=i;boolean exists=module!=null;
            var strength=strengths.get(i);var speed=speeds.get(i);var apply=applies.get(i);
            strength.visible=speed.visible=apply.visible=exists;strength.active=speed.active=apply.active=exists&&editable;
            strength.setEditable(exists&&editable);speed.setEditable(exists&&editable);
            if(exists){String d=ModuleMotionDamping.DIRECTIONS.get(index);
                if(!strength.isFocused())strength.setValue(String.format(Locale.ROOT,"%.3f",pending.getOrDefault("damp"+d,module.value("damp"+d))));
                if(!speed.isFocused())speed.setValue(String.format(Locale.ROOT,"%.3f",pending.getOrDefault("damp"+d+"Speed",module.value("damp"+d+"Speed"))));
                strength.setTooltip(Tooltip.create(tr("strength_help")));speed.setTooltip(Tooltip.create(tr("speed_help")));
            }
        }
        pump();
    }
    private void send(String key,double value){pending.put(key,value);pump();}
    private void pump(){
        if(module==null||!editable){pending.clear();waiting=null;return;}
        int revision=menu.snapshot().getInt("Revision");
        if(waiting!=null){
            if(module.value(waiting)==waitingValue){pending.remove(waiting,waitingValue);waiting=null;}
            else if(revision!=waitingRevision||ticks-waitingAt>=20)waiting=null;
            else return;
        }
        while(!pending.isEmpty()){
            var next=pending.entrySet().iterator().next();if(module.value(next.getKey())==next.getValue()){pending.remove(next.getKey());continue;}
            waiting=next.getKey();waitingValue=next.getValue();waitingRevision=revision;waitingAt=ticks;
            PacketDistributor.sendToServer(new ModuleNetworking.Action(menu.containerId,revision,id,"edit",waiting,waitingValue,""));return;
        }
    }
    private void apply(int row){
        if(!editable||module==null||row>=rows)return;String d=ModuleMotionDamping.DIRECTIONS.get(row);
        commit(strengths.get(row),"damp"+d,100);commit(speeds.get(row),"damp"+d+"Speed",128);
        strengths.get(row).setFocused(false);speeds.get(row).setFocused(false);
    }
    private void commit(EditBox box,String key,double max){
        if(module==null||!editable)return;
        try{double value=Double.parseDouble(box.getValue());if(!Double.isFinite(value)||value<0||value>max)throw new NumberFormatException();
            if(value!=module.value(key))send(key,value);box.setTextColor(0xE0E0E0);
        }catch(NumberFormatException invalid){box.setTextColor(0xFF7777);}
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){
        if(key==257||key==335){for(int i=0;i<rows;i++){
            String d=ModuleMotionDamping.DIRECTIONS.get(i);
            if(strengths.get(i).isFocused()){commit(strengths.get(i),"damp"+d,100);strengths.get(i).setFocused(false);return true;}
            if(speeds.get(i).isFocused()){commit(speeds.get(i),"damp"+d+"Speed",128);speeds.get(i).setFocused(false);return true;}
        }}return super.keyPressed(key,scan,modifiers);
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        for(int i=0;i<rows;i++){
            String d=ModuleMotionDamping.DIRECTIONS.get(i);
            if(speeds.get(i).isFocused()&&!speeds.get(i).isMouseOver(mx,my))commit(speeds.get(i),"damp"+d+"Speed",128);
            if(strengths.get(i).isFocused()&&!strengths.get(i).isMouseOver(mx,my))commit(strengths.get(i),"damp"+d,100);
        }return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(vertical!=0&&editable&&module!=null)for(int i=0;i<rows;i++)for(var box:List.of(strengths.get(i),speeds.get(i)))if(box.active&&box.isMouseOver(mx,my)){
            boolean strength=box==strengths.get(i);String key="damp"+ModuleMotionDamping.DIRECTIONS.get(i)+(strength?"":"Speed");
            double value;try{value=Double.parseDouble(box.getValue());}catch(NumberFormatException invalid){value=module.value(key);}
            box.setValue(String.format(Locale.ROOT,"%.3f",Math.clamp(value+Math.signum(vertical)*(strength?1:.1),0,strength?100:128)));commit(box,key,strength?100:128);box.setFocused(true);return true;
        }return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    private void text(GuiGraphics g,Component label,int bx,int by,int max){float scale=Math.min(LifeUiStyle.scale(),max/(float)Math.max(1,font.width(label)));g.pose().pushPose();g.pose().translate(bx,by,0);g.pose().scale(scale,scale,1);g.drawString(font,label,0,0,0xD9C4ED,false);g.pose().popPose();}
    @Override public void tick(){ticks++;sync();if(closing&&pending.isEmpty()&&waiting==null)minecraft.setScreen(parent);}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){
        super.renderBackground(g,mx,my,partial);g.fill(x-2,y-2,x+w+2,y+h+2,0xFF92634D);g.fill(x,y,x+w,y+h,0xFF242432);
        text(g,tr("title"),x+10,y+13,w-74);
        text(g,tr("strength_short"),strengthX,y+34,inputWidth);text(g,tr("threshold_short"),speedX,y+34,inputWidth);
        text(g,tr("speed_unit"),speedX,y+44,inputWidth);
        for(int i=0;i<rows;i++){String[] world={"north","south","east","west","up","down"};String key=module!=null&&module.value("dampFrame")==0?world[i]:ModuleMotionDamping.DIRECTIONS.get(i).toLowerCase(Locale.ROOT);text(g,tr(key),x+10,y+56+i*rowHeight+Math.round((18-font.lineHeight*LifeUiStyle.scale())/2),labelWidth);}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        super.render(g,mx,my,partial);
        for(var box:strengths)if(box.isMouseOver(mx,my))g.renderTooltip(font,tr("strength_help"),mx,my);
        for(var box:speeds)if(box.isMouseOver(mx,my))g.renderTooltip(font,tr("speed_help"),mx,my);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){
        for(int i=0;i<rows;i++){
            String d=ModuleMotionDamping.DIRECTIONS.get(i);
            if(strengths.get(i).isFocused())commit(strengths.get(i),"damp"+d,100);
            if(speeds.get(i).isFocused())commit(speeds.get(i),"damp"+d+"Speed",128);
        }
        closing=true;if(pending.isEmpty()&&waiting==null)minecraft.setScreen(parent);
    }
}
