package dev.sablelife.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.sablelife.SableLife;
import dev.sablelife.attack.ContactBox;
import dev.sablelife.item.InspectionSpyglass;
import dev.sablelife.module.InspectionSnapshot;
import dev.sablelife.module.MarkOutline;
import dev.sablelife.module.InspectionSnapshot.Piece;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaterniond;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

@EventBusSubscriber(modid = SableLife.MOD_ID, value = Dist.CLIENT)
public final class InspectionClient {
    static final int MAX_SELECTED = 32;
    private static final double LINE_DISTANCE = 96;
    private static final double LABEL_DISTANCE = 48;
    private static final float LABEL_SCALE = .025F;
    private static final int CIRCLE_STEPS = 24;
    private static final MultiBufferSource.BufferSource labels = MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    private static final Map<UUID, Overlay> selected = new LinkedHashMap<>();
    private static ClientLevel world;
    private static UUID core;
    private static boolean held;
    private static PendingSnapshot pending;

    private record Mesh(Piece piece, List<Vec3> edges, Vec3 center, double radius) {}
    private record Overlay(InspectionSnapshot.Row row, List<Mesh> meshes, int color, Component label) {}
    private record Label(Vec3 position, Component text) {}
    private record LabelBounds(float left, float right, float bottom, float top) {}

    private static final class PendingSnapshot {
        final ClientLevel world;
        final UUID core;
        final List<InspectionSnapshot.Row> rows = new ArrayList<>();
        int nextBatch;

        PendingSnapshot(ClientLevel world, UUID core) {
            this.world = world;
            this.core = core;
        }
    }

    public static void receive(InspectionSnapshot packet) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (packet.batch() == 0) pending = new PendingSnapshot(mc.level, packet.core());
        if (pending == null || pending.world != mc.level || !pending.core.equals(packet.core())
                || pending.nextBatch != packet.batch() || pending.rows.size() + packet.rows().size() > InspectionSnapshot.MAX_ROWS) {
            pending = null;
            return;
        }
        pending.rows.addAll(packet.rows());
        pending.nextBatch++;
        if (!packet.last()) return;
        var rows = List.copyOf(pending.rows);
        pending = null;
        if (world != mc.level || !packet.core().equals(core)) {
            selected.clear();
            world = mc.level;
            core = packet.core();
        }
        if (!packet.available()) {
            selected.clear();
            if (mc.screen instanceof InspectionScreen screen) screen.onClose();
            return;
        }
        selected.keySet().retainAll(rows.stream().map(InspectionSnapshot.Row::id).toList());
        for (var row : rows) {
            var existing = selected.get(row.id());
            if (existing != null && !existing.row.equals(row)) selected.put(row.id(), overlay(row));
        }
        if (packet.open() && matchesHeldTool(mc)) {
            mc.player.stopUsingItem();
            mc.setScreen(new InspectionScreen(rows));
        } else if (mc.screen instanceof InspectionScreen screen) {
            screen.updateRows(rows);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (pending != null && pending.world != mc.level) pending = null;
        if (world != mc.level) {
            selected.clear();
            world = null;
            core = null;
        }
        held = matchesHeldTool(mc);
    }

    private static boolean matchesHeldTool(Minecraft mc) {
        return core != null && mc.player != null && mc.level == world
                && core.equals(InspectionSpyglass.boundCore(mc.player.getMainHandItem()))
                && InspectionSpyglass.inDimension(mc.player.getMainHandItem(), world.dimension().location().toString());
    }

    static int count() { return selected.size(); }
    static boolean checked(UUID id) { return selected.containsKey(id); }

    static void toggle(InspectionSnapshot.Row row) {
        if (selected.remove(row.id()) == null && selected.size() < MAX_SELECTED) {
            selected.put(row.id(), overlay(row));
        }
    }

    static Component name(InspectionSnapshot.Row row) {
        return row.name().isBlank() ? ModuleText.name(row.moduleType()).copy().append(" #" + row.number())
                : Component.literal(row.name());
    }

    private static Overlay overlay(InspectionSnapshot.Row row) {
        return new Overlay(row, row.pieces().stream().map(InspectionClient::mesh).toList(),
                ModuleCategory.of(row.moduleType()).color & 0xFFFFFF, name(row));
    }

    private static Mesh mesh(Piece piece) {
        var size = piece.size();
        var rotation = new Quaterniond(piece.rotation());
        var edges = new ArrayList<Vec3>();
        switch (piece.shape()) {
            case BOX -> edges.addAll(MarkOutline.box(size.scale(-1), size, UnaryOperator.identity()));
            case FACE -> {
                var a = new Vec3(-size.x, -size.y, 0);
                var b = new Vec3(-size.x, size.y, 0);
                var c = new Vec3(size.x, size.y, 0);
                var d = new Vec3(size.x, -size.y, 0);
                edges.addAll(List.of(a, b, b, c, c, d, d, a));
            }
            case CROSS -> edges.addAll(List.of(new Vec3(-size.x, 0, 0), new Vec3(size.x, 0, 0),
                    new Vec3(0, -size.y, 0), new Vec3(0, size.y, 0), new Vec3(0, 0, -size.z), new Vec3(0, 0, size.z)));
            case ARROW -> {
                var tip = new Vec3(0, 0, size.x);
                double length = Math.min(size.x * .35, .55);
                double width = length / 2;
                double base = size.x - length;
                edges.addAll(List.of(Vec3.ZERO, tip, tip, new Vec3(width, 0, base), tip, new Vec3(-width, 0, base),
                        tip, new Vec3(0, width, base), tip, new Vec3(0, -width, base)));
            }
            case RING -> addCircle(edges, Vec3.ZERO, X, Y, size.x);
            case SPHERE -> {
                addCircle(edges, Vec3.ZERO, X, Y, size.x);
                addCircle(edges, Vec3.ZERO, X, Z, size.x);
                addCircle(edges, Vec3.ZERO, Y, Z, size.x);
            }
            case FRUSTUM -> {
                var end = Z.scale(size.z);
                if (size.x > 0) addCircle(edges, Vec3.ZERO, X, Y, size.x);
                if (size.y > 0) addCircle(edges, end, X, Y, size.y);
                for (var direction : List.of(X, X.scale(-1), Y, Y.scale(-1))) {
                    edges.add(direction.scale(size.x));
                    edges.add(end.add(direction.scale(size.y)));
                }
            }
            case FAN -> {
                for (int sign : new int[]{-1, 1}) {
                    var previous = Vec3.ZERO;
                    for (int i = 0; i <= CIRCLE_STEPS; i++) {
                        double angle = size.z * (2.0 * i / CIRCLE_STEPS - 1);
                        var point = new Vec3(Math.sin(angle) * size.x, sign * size.y, Math.cos(angle) * size.x);
                        edges.add(previous);
                        edges.add(point);
                        previous = point;
                    }
                    edges.add(previous);
                    edges.add(Vec3.ZERO);
                }
            }
        }
        var points = edges.stream().map(edge -> ContactBox.rotate(rotation, edge)).toList();
        var bounds = new AABB(Vec3.ZERO, Vec3.ZERO);
        for (var point : points) {
            bounds = bounds.minmax(new AABB(point, point));
        }
        return new Mesh(piece, points, bounds.getCenter(), Math.max(.08, bounds.getCenter().distanceTo(
                new Vec3(bounds.maxX, bounds.maxY, bounds.maxZ))));
    }

    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);

    private static UnaryOperator<Vec3> projection(SubLevelContainer container, Piece piece, float partial) {
        if (piece.anchor() == null) return offset -> piece.center().add(offset);
        var anchor = piece.anchor();
        if (container == null || !(container.getSubLevel(anchor.bodyId()) instanceof ClientSubLevel part) || part.isRemoved()) return null;
        var markPos = anchor.position(part);
        if (world.getBlockState(markPos).isAir()) return null;
        if (piece.second() != null && world.getBlockState(part.getPlot().getCenterBlock().offset(piece.second())).isAir()) return null;
        var origin = markPos.getCenter().add(piece.center());
        var pose = part.renderPose(partial);
        if (!piece.worldAxes()) return offset -> pose.transformPosition(origin.add(offset));
        var center = pose.transformPosition(origin);
        return center::add;
    }

    private static void addCircle(List<Vec3> edges, Vec3 center, Vec3 a, Vec3 b, double radius) {
        Vec3 start = center.add(a.scale(radius));
        Vec3 previous = start;
        for (int i = 1; i < CIRCLE_STEPS; i++) {
            double angle = Math.PI * 2 * i / CIRCLE_STEPS;
            var point = center.add(a.scale(radius * Math.cos(angle))).add(b.scale(radius * Math.sin(angle)));
            edges.add(previous);
            edges.add(point);
            previous = point;
        }
        edges.add(previous);
        edges.add(start);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || !held
                || selected.isEmpty() || mc.level != world || world == null) return;
        var container = SubLevelContainer.getContainer(world);
        var buffers = mc.renderBuffers().bufferSource();
        var lines = buffers.getBuffer(PreviewLineStyle.worldLines());
        var camera = event.getCamera().getPosition();
        var frustum = event.getFrustum();
        var pendingLabels = new ArrayList<Label>();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        for (var overlay : selected.values()) {
            Vec3 nearestLabel = null;
            double labelDistance = LABEL_DISTANCE * LABEL_DISTANCE;
            for (var mesh : overlay.meshes) {
                var transform = projection(container, mesh.piece, partial);
                if (transform == null) continue;
                var center = transform.apply(mesh.center);
                double range = LINE_DISTANCE + mesh.radius;
                if (center.distanceToSqr(camera) > range * range
                        || (frustum != null && !frustum.isVisible(new AABB(center, center).inflate(mesh.radius)))) continue;
                double top = center.y;
                for (int i = 0; i < mesh.edges.size(); i += 2) {
                    var from = transform.apply(mesh.edges.get(i)).subtract(camera);
                    var to = transform.apply(mesh.edges.get(i + 1)).subtract(camera);
                    if (from.distanceToSqr(to) > 1e-12) BlueprintPreviewClient.line(event.getPoseStack(), lines, from, to, overlay.color);
                    top = Math.max(top, camera.y + Math.max(from.y, to.y));
                }
                var label = new Vec3(center.x, Math.min(top, center.y + 3) + .18, center.z);
                double distance = label.distanceToSqr(camera);
                if (distance < labelDistance && (frustum == null || frustum.isVisible(new AABB(label, label).inflate(.6)))) {
                    labelDistance = distance;
                    nearestLabel = label;
                }
            }
            if (nearestLabel != null) pendingLabels.add(new Label(nearestLabel.subtract(camera), overlay.label));
        }
        buffers.endBatch(PreviewLineStyle.worldLines());
        drawLabels(mc.font, event, pendingLabels);
        if (!pendingLabels.isEmpty()) labels.endBatch();
    }

    private static void drawLabels(Font font, RenderLevelStageEvent event, List<Label> pending) {
        if (pending.isEmpty()) return;
        var projection = event.getProjectionMatrix();
        float aspectScale = Math.abs(projection.m00() / projection.m11());
        var occupied = new ArrayList<LabelBounds>(pending.size());
        // Stack billboards in projected space so separate marks can also overlap from the viewer's angle.
        for (var label : pending) {
            var point = ShockwaveMath.project(label.position, Vec3.ZERO, event.getModelViewMatrix(), projection);
            if (point == null || !(point.unitRadius() > 0)) continue;
            var text = label.text.getString();
            if (font.width(text) > 120) text = font.plainSubstrByWidth(text, 114) + "…";
            int width = font.width(text);
            float unit = point.unitRadius() * LABEL_SCALE;
            float halfWidth = (width / 2F + 2) * unit * aspectScale;
            float left = point.x() - halfWidth;
            float right = point.x() + halfWidth;
            float height = font.lineHeight * unit;
            float gap = 3 * unit;
            float bottom = point.y();
            boolean shifted;
            do {
                shifted = false;
                for (var previous : occupied) {
                    if (left >= previous.right || right <= previous.left || bottom >= previous.top + gap
                            || bottom + height + gap <= previous.bottom) continue;
                    bottom = previous.top + gap;
                    shifted = true;
                }
            } while (shifted);
            occupied.add(new LabelBounds(left, right, bottom, bottom + height));
            drawLabel(font, event, label.position, text, width, (bottom - point.y()) / unit);
        }
    }

    private static void drawLabel(Font font, RenderLevelStageEvent event, Vec3 position, String text, int width, float lift) {
        var pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(position.x, position.y, position.z);
        pose.mulPose(event.getCamera().rotation());
        pose.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        font.drawInBatch(text, -width / 2F, -font.lineHeight - lift, 0xFFF4F0F7, true,
                pose.last().pose(), labels, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }

    private InspectionClient() {}
}
