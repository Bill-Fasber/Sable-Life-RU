package dev.sablelife.client;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartWirelessMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import java.util.ArrayList;
import java.util.List;

/** Channel sidebar, frequency sampler and waveform editor; no chest grid controls. */
@EventBusSubscriber(modid = SableLife.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HeartWirelessScreen extends AbstractContainerScreen<HeartWirelessMenu> {
    @SubscribeEvent public static void register(RegisterMenuScreensEvent event) {
        event.register(SableLife.WIRELESS_MENU.get(), HeartWirelessScreen::new);
    }
    private int tickCount;
    private final List<Button> channelButtons = new ArrayList<>(), editButtons = new ArrayList<>();
    private final List<EditBox> inputs = new ArrayList<>();
    private int page, lastSelected = -1;
    private int detailX, detailWidth, inputX, fieldPitch, sidebarPitch, requestedRows;
    private boolean showTimeline;
    private int deleteUntil, deleteChannel = -1, deleteCount;
    private Button enabled, loop, master, picker, mode, add, previous, next, remove, nameApply, back;
    private EditBox nameInput, latchReset;
    private Button latch, latchResetApply, outputEntry;
    private boolean outputEditor, openedInitialView;
    private int facePage, faceRows;
    private Button addFace, facePrevious, faceNext;
    private final List<Button> markFaces=new ArrayList<>(), previewFaces=new ArrayList<>(), removeFaces=new ArrayList<>();

    private final List<EditBox> faceNames = new ArrayList<>();
    private final List<Button> saveFaceNames = new ArrayList<>();
    private int faceNameWidth, faceStatusX, faceStatusWidth;
    private int displayedFacePage = -1, displayedFaceRevision;
    private static final int FACE_NAME_X = 30;

    private void setOutputEditor(boolean open) {
        outputEditor=open;
        rebuildWidgets();
    }

    private void sendFace(String operation,int index) {
        sendFace(operation,index,"");
    }

    private void sendFace(String operation,int index,String text) {
        var snapshot=menu.outputSnapshot();
        if (snapshot.getInt("Selected") != menu.selected()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.sablelife.module.ModuleNetworking.Action(
                menu.containerId,snapshot.getInt("FaceRevision"),new java.util.UUID(0,0),"wire_face",operation,
                menu.selected()*128+index,text));
    }

    private void renameFace(int row) {
        var input = faceNames.get(row);
        if (!input.visible || !input.active) return;
        String name = input.getValue();
        if (name.codePointCount(0,name.length()) > dev.sablelife.heart.WirelessChannel.NAME_LIMIT) {
            input.setTextColor(0xFF7777);
            return;
        }
        sendFace("rename",facePage*faceRows+row,name);
        input.setFocused(false);
    }

    private void initOutputEditor() {
        menu.layoutSlots(-10000,-10000,-10000);
        markFaces.clear(); previewFaces.clear(); removeFaces.clear();
        faceNames.clear(); saveFaceNames.clear();
        displayedFacePage=-1;
        faceRows=Math.clamp((imageHeight-90)/22,1,8);
        button(tr("back"),imageWidth-62,8,52,()->setOutputEditor(false));
        int controlWidth=Math.clamp(imageWidth/6,48,76);
        int controls=imageWidth-36-2*controlWidth;
        faceStatusWidth=Math.clamp((controls-FACE_NAME_X)/3,46,120);
        faceStatusX=controls-faceStatusWidth-4;
        faceNameWidth=faceStatusX-FACE_NAME_X-26;
        for (int i=0;i<faceRows;i++) {
            int row=i,y=36+i*22;
            var name = addRenderableWidget(new EditBox(font,leftPos+FACE_NAME_X,topPos+y,faceNameWidth,18,tr("face_name")));
            name.setHint(tr("face_name"));
            name.setTooltip(Tooltip.create(tr("face_name_help")));
            name.setMaxLength(dev.sablelife.heart.WirelessChannel.NAME_LIMIT*2);
            faceNames.add(name);
            saveFaceNames.add(button(Component.literal("✓"),FACE_NAME_X+faceNameWidth+4,y,18,()->renameFace(row)));
            markFaces.add(button(tr("face_mark"),controls,y,controlWidth,()->sendFace("mark",facePage*faceRows+row)));
            previewFaces.add(button(tr("face_preview"),controls+controlWidth+4,y,controlWidth,()->sendFace("preview",facePage*faceRows+row)));
            var removeFace=button(Component.literal("×").withStyle(net.minecraft.ChatFormatting.RED),imageWidth-28,y,18,
                    ()->sendFace("remove",facePage*faceRows+row));
            removeFace.setTooltip(Tooltip.create(Component.translatable("dashboard.sablelife.remove_output_face")));
            removeFaces.add(removeFace);
        }
        int pagerY=imageHeight-51;
        facePrevious=button(Component.literal("‹"),10,pagerY,28,()->{facePage--;syncFaces();});
        faceNext=button(Component.literal("›"),imageWidth-38,pagerY,28,()->{facePage++;syncFaces();});
        addFace=button(tr("face_add"),10,imageHeight-28,120,()->sendFace("mark",outputFaces().size()));
        syncFaces();
    }

    private net.minecraft.nbt.ListTag outputFaces() { return menu.outputSnapshot().getList("OutputFaces",10); }

    private void syncFaces() {
        var faces=outputFaces();
        facePage=Math.clamp(facePage,0,Math.max(0,(faces.size()-1)/faceRows));
        boolean editable=menu.value(HeartWirelessMenu.EDITABLE)!=0 && menu.count()>0;
        int revision=menu.outputSnapshot().getInt("FaceRevision");
        boolean rowsChanged=displayedFacePage!=facePage||displayedFaceRevision!=revision;
        displayedFacePage=facePage;
        displayedFaceRevision=revision;
        for (int row=0;row<faceRows;row++) {
            int index=facePage*faceRows+row;
            boolean exists=index<faces.size();
            var mark=markFaces.get(row); var preview=previewFaces.get(row); var remove=removeFaces.get(row);
            mark.visible=preview.visible=remove.visible=exists;
            mark.active=remove.active=editable && exists;
            preview.active=exists && faces.getCompound(index).getString("Status").equals("valid");
            var name=faceNames.get(row);
            var save=saveFaceNames.get(row);
            name.visible=save.visible=exists;
            name.active=exists&&editable;
            name.setEditable(name.active);
            if (rowsChanged||!name.active) name.setFocused(false);
            String saved=exists?faces.getCompound(index).getString("Name"):"";
            if (!name.isFocused() && !name.getValue().equals(saved)) {
                name.setValue(saved);
                name.setTextColor(0xE0E0E0);
            }
            save.active=name.active&&!name.getValue().equals(saved);
        }
        facePrevious.active=facePage>0;
        faceNext.active=(facePage+1)*faceRows<faces.size();
        addFace.active=editable && faces.size()<menu.outputFaceLimit();
    }
    public HeartWirelessScreen(HeartWirelessMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 360; imageHeight = 234;
    }
    private Component tr(String key) { return Component.translatable("wireless.sablelife." + key); }
    private Button button(Component label, int x, int y, int width, Runnable action) {
        return addRenderableWidget(LifeUiStyle.button(label, leftPos + x, topPos + y, width, 18, action));
    }
    @Override protected void init() {
        imageWidth = Math.max(300, Math.min(480, width - 14)); imageHeight = Math.max(200, height - 14);
        detailX = Math.min(155, Math.max(103, Math.round(imageWidth * .32f))); detailWidth = imageWidth - detailX - 10;
        inputX = detailX + Math.round(detailWidth * .45f); showTimeline = imageHeight >= 300;
        fieldPitch = Math.clamp((imageHeight - (showTimeline ? 89 : 55) - 96) / 6, 13, 30);
        sidebarPitch = Math.max(14, (int)Math.ceil(font.lineHeight * LifeUiStyle.scale()) + 8);
        requestedRows = Math.clamp((imageHeight - 51 - 34 - 5) / sidebarPitch, 1, HeartWirelessMenu.PAGE_SIZE);
        menu.layoutSlots(detailX + 10, detailX + 41, 60);
        super.init(); channelButtons.clear(); editButtons.clear(); inputs.clear();
        if (outputEditor) { initOutputEditor(); return; }
        for (int i = 0; i < HeartWirelessMenu.PAGE_SIZE; i++) {
            int row = i;
            var control = button(Component.empty(), 10, 34 + i * sidebarPitch, detailX - 20,
                    () -> send(0, 6, page * menu.pageSize() + row)); control.setHeight(sidebarPitch - 2); channelButtons.add(control);
        }
        previous = button(Component.literal("‹"), 10, imageHeight - 51, 28, () -> send(0, 12, page - 1));
        next = button(Component.literal("›"), detailX - 38, imageHeight - 51, 28, () -> send(0, 12, page + 1));
        add = button(tr("add"), 10, imageHeight - 28, detailX - 20, () -> send(0, 10, 0));
        back = button(tr("back"), imageWidth - 62, 8, 52, () -> send(0, 8, 0));
        var density = button(Component.translatable("dashboard.sablelife.density_" + LifeUiStyle.density()), imageWidth - 106, 8, 40, () -> changeDensity(1));
        LifeUiStyle.reverse(density, () -> changeDensity(-1));
        density.setTooltip(Tooltip.create(Component.translatable("dashboard.sablelife.density_help")));
        master = button(Component.empty(), 10, 8, detailX - 20,
                () -> send(0, 7, menu.value(HeartWirelessMenu.MASTER) == 0 ? 1 : 0));
        nameInput = addRenderableWidget(new EditBox(font, leftPos + detailX + 58, topPos + 34, detailWidth - 82, 18, tr("name")));
        nameInput.setMaxLength(dev.sablelife.heart.WirelessChannel.NAME_LIMIT * 2);
        nameInput.setHint(tr("name")); nameInput.setTooltip(Tooltip.create(tr("name_help")));
        nameApply = addRenderableWidget(LifeUiStyle.button(Component.literal("✓"), leftPos + imageWidth - 30, topPos + 34, 20, 18, this::rename));
        int entryStart=detailX+68, entryWidth=(imageWidth-14-entryStart)/2;
        picker = button(tr("choose_items"),entryStart,60,entryWidth,()->send(0,13,0));
        outputEntry = button(tr("output_faces"),entryStart+entryWidth+4,60,entryWidth,()->{facePage=0;setOutputEditor(true);});
        picker.setTooltip(Tooltip.create(tr("sample_help")));
        outputEntry.setTooltip(Tooltip.create(tr("face_help")));
        for (int field = 1; field <= 4; field++) {
            final int parameter = field;
            int y = 96 + (field - 1) * fieldPitch;
            var input = new EditBox(font, leftPos + inputX, topPos + y, imageWidth - inputX - 68, fieldPitch - 2, tr("field_" + field));
            input.setFilter(s -> s.matches("[0-9]{0,4}(\\.[0-9]{0,2})?"));
            input.setMaxLength(7); addRenderableWidget(input); inputs.add(input);
            editButtons.add(button(Component.literal("−"), imageWidth - 64, y, 16, () -> adjust(parameter, -1)));
            editButtons.add(button(Component.literal("+"), imageWidth - 46, y, 16, () -> adjust(parameter, 1)));
            editButtons.add(button(Component.literal("✓"), imageWidth - 28, y, 18, () -> apply(parameter)));
        }
        for (var control : editButtons) control.setHeight(fieldPitch - 2);
        latch=button(Component.empty(),inputX,96+4*fieldPitch,imageWidth-inputX-10,()->send(menu.selected(),15,menu.setting(menu.selected(),7)==0?1:0));latch.setHeight(fieldPitch-2);
        LifeUiStyle.reverse(latch,latch::onPress);latch.setTooltip(Tooltip.create(tr("latch_help")));
        latchReset=addRenderableWidget(new EditBox(font,leftPos+inputX,topPos+96+5*fieldPitch,imageWidth-inputX-34,fieldPitch-2,tr("latch_reset")));
        latchReset.setFilter(v->v.matches("[0-9]{0,5}"));latchReset.setMaxLength(5);latchReset.setTooltip(Tooltip.create(tr("latch_help")));
        latchResetApply=button(Component.literal("✓"),imageWidth-28,96+5*fieldPitch,18,this::applyLatchReset);latchResetApply.setHeight(fieldPitch-2);

        int third = (detailWidth - 8) / 3;
        enabled = button(Component.empty(), detailX, imageHeight - 28, third, () -> toggle(0));
        mode = button(Component.empty(), detailX, imageHeight - 52, detailWidth, () -> send(menu.selected(), 9, (menu.setting(menu.selected(), 6) + 1) % 5));
        LifeUiStyle.reverse(mode, () -> send(menu.selected(), 9, Math.floorMod(menu.setting(menu.selected(), 6) - 1, 5)));
        LifeUiStyle.reverse(master, master::onPress); LifeUiStyle.reverse(enabled, enabled::onPress); LifeUiStyle.reverse(picker, picker::onPress);
        mode.setTooltip(Tooltip.create(tr("mode_help")));
        loop = button(Component.empty(), detailX + third + 4, imageHeight - 28, third, () -> toggle(5));
        LifeUiStyle.reverse(loop, loop::onPress);
        remove = button(tr("delete"), detailX + (third + 4) * 2, imageHeight - 28, detailWidth - (third + 4) * 2, () -> {
            if (deleteUntil > tickCount && deleteChannel == menu.selected() && deleteCount == menu.count()) {
                send(0, 11, menu.selected()); deleteUntil = 0;
            } else {
                deleteUntil = tickCount + 60; deleteChannel = menu.selected(); deleteCount = menu.count();
            }
        });
        remove.setTooltip(Tooltip.create(tr("delete_help")));
        editButtons.add(enabled); editButtons.add(loop);
        sync(); send(0, 14, requestedRows);
    }
    private void changeDensity(int direction) { LifeUiStyle.cycle(direction); rebuildWidgets(); }
    private void send(int channel, int field, int raw) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, field << 16 | Math.clamp(raw, 0, 65535));
    }
    private void applyLatchReset(){
        if(!latchReset.active)return;
        try{int value=Integer.parseInt(latchReset.getValue());if(value<0||value>menu.count()||value==menu.selected()+1){latchReset.setTextColor(0xFF7777);return;}
            send(menu.selected(),16,value);latchReset.setTextColor(0xE0E0E0);latchReset.setFocused(false);
        }catch(NumberFormatException ignored){latchReset.setTextColor(0xFF7777);}
    }
    private void rename() {
        if (!nameInput.active) return;
        if (nameInput.getValue().codePointCount(0, nameInput.getValue().length()) > dev.sablelife.heart.WirelessChannel.NAME_LIMIT) { nameInput.setTextColor(0xFF7777); return; }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new dev.sablelife.module.ModuleNetworking.Action(menu.containerId, 0, new java.util.UUID(0, 0), "wireless_name", "", menu.selected(), nameInput.getValue()));
        nameInput.setTextColor(0xE0E0E0); nameInput.setFocused(false);
    }
    private void toggle(int field) { send(menu.selected(), field, menu.setting(menu.selected(), field) == 0 ? 1 : 0); }
    private void adjust(int field, int direction) {
        int step = field == 4 ? 1 : hasShiftDown() ? 100 : 20;
        send(menu.selected(), field, Math.max(0, menu.setting(menu.selected(), field) + direction * step));
    }
    private void apply(int field) {
        EditBox input = inputs.get(field - 1);
        try {
            double number = Double.parseDouble(input.getValue());
            double limit = field == 4 ? 15 : 600;
            if (!Double.isFinite(number) || number < 0 || number > limit) { input.setTextColor(0xFF7777); return; }
            send(menu.selected(), field, (int) Math.round(number * (field == 4 ? 1 : 20)));
            input.setTextColor(0xE0E0E0); input.setFocused(false);
        } catch (NumberFormatException invalid) { input.setTextColor(0xFF7777); }
    }
    private void sync() {
        boolean editable = menu.value(HeartWirelessMenu.EDITABLE) != 0;
        page = menu.page();
        int selected = menu.selected(); boolean exists = menu.count() > 0;
        if (selected != deleteChannel || menu.count() != deleteCount) deleteUntil = 0;
        remove.setMessage(tr(deleteUntil > tickCount ? "confirm_delete" : "delete"));
        previous.active = page > 0; next.active = (page + 1) * menu.pageSize() < menu.count();
        add.active = editable && menu.count() < ((menu.value(HeartWirelessMenu.CHANNEL_LIMIT)&0xffff)==0?dev.sablelife.heart.HeartWireless.CHANNELS:menu.value(HeartWirelessMenu.CHANNEL_LIMIT)&0xffff);
        for (int i = 0; i < HeartWirelessMenu.PAGE_SIZE; i++) {
            int channel = page * menu.pageSize() + i, output = menu.rowPower(i);
            channelButtons.get(i).visible = i < Math.min(requestedRows, menu.pageSize()) && channel < menu.count();
            var label = Component.literal((channel == selected ? "▶ " : "") + (channel + 1) + ".  ")
                    .append(tr(menu.setting(channel, 0) == 0 ? "disabled" : output > 0 ? "powered" : "ready"));
            var name = menu.channelName(channel); if (!name.isEmpty()) label.append("  ").append(Component.literal(name));
            channelButtons.get(i).setMessage(label); channelButtons.get(i).setTooltip(name.isEmpty() ? null : Tooltip.create(label));
        }
        for (int field = 1; field <= 4; field++) {
            var input = inputs.get(field - 1);
            if (lastSelected != selected || !input.isFocused()) {
                int value = menu.setting(selected, field);
                input.setValue(field == 4 ? Integer.toString(value) : String.format(java.util.Locale.ROOT, "%.2f", value / 20.0));
            }
            input.visible = exists; input.setEditable(exists && editable && (menu.setting(selected, 6) == 0 || menu.setting(selected, 6) == 2));
            input.active = exists && editable && (menu.setting(selected, 6) == 0 || menu.setting(selected, 6) == 2); input.setTextColor(input.active ? 0xE0E0E0 : 0x666666);
            if (!input.active) input.setFocused(false);
        }
        for (Button control : editButtons) { control.active = editable && exists; control.visible = exists; }
        boolean receiving = menu.setting(selected, 6) == 1;
        latch.visible=latchReset.visible=latchResetApply.visible=exists;
        latch.active=exists&&editable&&!receiving;latch.setMessage(tr(menu.setting(selected,7)==0?"latch_off":"latch_on"));
        latchReset.active=exists&&editable&&!receiving&&menu.setting(selected,7)!=0;latchReset.setEditable(latchReset.active);latchResetApply.active=latchReset.active;
        if(lastSelected!=selected||!latchReset.active)latchReset.setFocused(false);
        if(!latchReset.isFocused())latchReset.setValue(Integer.toString(menu.setting(selected,8)));
        latchReset.setTextColor(latchReset.active?0xE0E0E0:0x666666);

        boolean automatic = receiving || menu.setting(selected, 6) >= 3;
        for (int i = 0; i < 12; i++) editButtons.get(i).active = editable && exists && !automatic;
        loop.active = editable && exists && !automatic;
        mode.active = editable && exists; mode.visible = exists; mode.setMessage(tr(switch (menu.setting(selected, 6)) { case 1 -> "mode_receive"; case 2 -> "mode_output"; case 3 -> "mode_program"; case 4 -> "mode_program_output"; default -> "mode_repeat"; }));
        remove.visible = exists; remove.active = editable && exists;
        enabled.setMessage(tr(menu.setting(selected, 0) == 0 ? "enable_short" : "disable_short"));
        loop.setMessage(tr(menu.setting(selected, 5) == 0 ? "once" : "loop"));
        master.setMessage(tr(menu.value(HeartWirelessMenu.MASTER) == 0 ? "master_off" : "master_on"));
        master.active = editable;
        picker.visible = outputEntry.visible = exists;
        picker.setMessage(tr("choose_items"));
        nameInput.visible = exists; nameApply.visible = exists;
        nameInput.active = editable && exists; nameInput.setEditable(nameInput.active); nameApply.active = nameInput.active;
        if (lastSelected != selected) nameInput.setFocused(false);
        if (!nameInput.isFocused()) nameInput.setValue(menu.channelName(selected));
        lastSelected = selected;
    }
    @Override protected void containerTick() {
        super.containerTick(); tickCount++;
        var snapshot=menu.outputSnapshot();
        if (!openedInitialView && snapshot.contains("OutputRow")) {
            openedInitialView=true;
            int row=snapshot.getInt("OutputRow");
            if (row>=0) {
                setOutputEditor(true);
                facePage=row/faceRows;
                syncFaces();
                return;
            }
        }
        if (outputEditor) syncFaces(); else sync();
    }
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if(outputEditor)return super.mouseScrolled(mouseX,mouseY,horizontal,vertical);
        if(vertical!=0&&latchReset.active&&latchReset.isMouseOver(mouseX,mouseY)){
            int v;try{v=Integer.parseInt(latchReset.getValue());}catch(NumberFormatException ignored){v=menu.setting(menu.selected(),8);}
            int nextValue=Math.clamp(v+(int)Math.signum(vertical),0,menu.count());if(nextValue==menu.selected()+1)nextValue=Math.clamp(nextValue+(int)Math.signum(vertical),0,menu.count());
            latchReset.setValue(Integer.toString(nextValue));applyLatchReset();latchReset.setFocused(true);return true;
        }
        if (vertical != 0) for (int i = 0; i < inputs.size(); i++) {
            var input = inputs.get(i);
            if (!input.visible || !input.active || !input.isMouseOver(mouseX, mouseY)) continue;
            int field = i + 1;
            double value;
            try { value = Double.parseDouble(input.getValue()); }
            catch (NumberFormatException ignored) { value = menu.setting(menu.selected(), field) / (field == 4 ? 1. : 20.); }
            double minimum = field == 2 ? .05 : field == 4 ? 1 : 0;
            double nextValue = Math.clamp(Math.round((value + Math.signum(vertical) * (field == 4 ? 1 : .1)) * 100) / 100., minimum, field == 4 ? 15 : 600);
            input.setValue(field == 4 ? Integer.toString((int) nextValue) : String.format(java.util.Locale.ROOT, "%.2f", nextValue));
            apply(field); input.setFocused(true); return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (outputEditor) {
            if (key==256) { setOutputEditor(false); return true; }
            for (int i=0;i<faceNames.size();i++) {
                var name=faceNames.get(i);
                if (name.visible&&name.active&&name.isFocused()) {
                    if (key==257||key==335) renameFace(i);
                    else name.keyPressed(key,scan,modifiers);
                    return true;
                }
            }
            return super.keyPressed(key,scan,modifiers);
        }
        if(latchReset.visible&&latchReset.active&&latchReset.isFocused()){
            if(key==257||key==335){applyLatchReset();return true;}if(key!=256){latchReset.keyPressed(key,scan,modifiers);return true;}
        }
        if (nameInput.visible && nameInput.active && nameInput.isFocused()) {
            if (key == 257 || key == 335) { rename(); return true; }
            if (key != 256) { nameInput.keyPressed(key, scan, modifiers); return true; }
        }
        if (key == 257 || key == 335) for (int i = 0; i < inputs.size(); i++) if (inputs.get(i).isFocused()) { apply(i + 1); return true; }
        if (key != 256) for (EditBox input : inputs) if (input.visible && input.active && input.isFocused()) {
            input.keyPressed(key, scan, modifiers); return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF19151D);
        g.fill(x, y, x + imageWidth, y + imageHeight, 0xFF9B614E);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF242632);
        if (outputEditor) return;
        g.fill(x + 5, y + 5, x + detailX - 8, y + imageHeight - 5, 0xFF363140);
        g.fill(x + detailX - 6, y + 5, x + detailX - 4, y + imageHeight - 5, 0xFF9770B5);
        g.fill(x + detailX - 3, y + 56, x + imageWidth - 10, y + 88, 0xFF37313D);
        if (menu.count()==0) return;
        frame(g, x + detailX + 9, y + 59, 0xFFBE5356);
        frame(g, x + detailX + 40, y + 59, 0xFF5686C9);
        if (showTimeline && menu.count() > 0 && menu.setting(menu.selected(), 6) < 3) waveform(g);
    }
    private void frame(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 18, y + 18, color); g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF4A4555);
    }
    private void waveform(GuiGraphics g) {
        int channel = menu.selected(), delay = menu.setting(channel, 1), on = menu.setting(channel, 2), off = menu.setting(channel, 3);
        int horizon = Math.max(100, delay + (on + off) * 2);
        if (menu.setting(channel, 6) == 1) return;
        int x = leftPos + detailX, y = topPos + imageHeight - 68, width = detailWidth;
        g.fill(x, y, x + width, y + 7, 0xFF151B24);
        for (int pixel = 0; pixel < width; pixel++) {
            long t = (long) pixel * horizon / width;
            boolean active = t >= delay && (menu.setting(channel,7)!=0 || (menu.setting(channel, 5) != 0 ? (t - delay) % Math.max(1, on + off) < on : t - delay < on));
            if (active) g.fill(x + pixel, y + 1, x + pixel + 1, y + 6, 0xFFAA79C8);
        }
        if (menu.value(HeartWirelessMenu.ALIVE) != 0 && menu.elapsed() <= horizon) {
            int cursor = (int) (menu.elapsed() * width / horizon);
            g.fill(x + cursor, y, x + cursor + 1, y + 7, 0xFF87F3B1);
        }
    }
    private void text(GuiGraphics g, Component label, int x, int y, int available, int color) {
        float scale = Math.min(LifeUiStyle.scale(), available / (float)Math.max(1, font.width(label)));
        g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(scale, scale, 1);
        g.drawString(font, label, 0, 0, color, false); g.pose().popPose();
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (outputEditor) {
            var faces=outputFaces();
            text(g, Component.translatable("wireless.sablelife.face_title",menu.selected()+1,faces.size(),
                    menu.outputFaceLimit()),10,12,imageWidth-80,0xF2DDD1);
            if (faces.isEmpty()) g.drawWordWrap(font,tr("face_empty"),10,42,imageWidth-20,0xD9C4ED);
            for (int row=0;row<faceRows;row++) {
                int index=facePage*faceRows+row;
                if (index>=faces.size()) break;
                var face=faces.getCompound(index);
                text(g,Component.literal((index+1)+"."),10,42+row*22,18,0xD9C4ED);
                var status=tr("face_"+face.getString("Face")).copy().append(" · ").append(tr("face_"+face.getString("Status")));
                text(g,status,faceStatusX,42+row*22,faceStatusWidth,0xD9C4ED);
            }
            LifeUiStyle.drawPageNumber(g,font,Component.literal((facePage+1)+" / "+Math.max(1,(faces.size()+faceRows-1)/faceRows)),
                    facePrevious,faceNext,leftPos,topPos);
            return;
        }
        text(g, tr("title"), detailX, 12, detailWidth - 100, 0xF2DDD1);
        if (menu.count() > 0) text(g, Component.translatable("wireless.sablelife.channel", menu.selected() + 1), detailX, 39, 54, 0xD9C4ED);
        var pages = Component.literal((page + 1) + " / " + Math.max(1, (menu.count() + menu.pageSize() - 1) / menu.pageSize()));
        LifeUiStyle.drawPageNumber(g,font,pages,previous,next,leftPos,topPos);
        if (menu.count() == 0) { g.drawWordWrap(font, tr("empty"), detailX, 96, detailWidth, 0xD9C4ED); return; }

        for (int field = 1; field <= 4; field++) text(g, tr("field_" + field), detailX, 96 + (field - 1) * fieldPitch + 3, inputX - detailX - 6, 0xE6D8EA);
        text(g,tr("latch"),detailX,96+4*fieldPitch+2,inputX-detailX-6,0xE6D8EA);
        text(g,tr("latch_reset"),detailX,96+5*fieldPitch+2,inputX-detailX-6,0xE6D8EA);
        if (showTimeline) text(g, menu.setting(menu.selected(), 6) == 1 ? Component.translatable("wireless.sablelife.receive_power", menu.value(HeartWirelessMenu.SELECTED_INPUT)) : tr(menu.setting(menu.selected(), 6) >= 3 ? "program_timeline" : "timeline"), detailX, imageHeight - 83, detailWidth, 0xBDA5D0);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (outputEditor) syncFaces(); else sync();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick); renderTooltip(graphics, mouseX, mouseY);
    }
}
