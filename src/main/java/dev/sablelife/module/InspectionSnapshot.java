package dev.sablelife.module;

import dev.sablelife.SableLife;
import dev.sablelife.heart.HeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A bounded set of compact, read-only shapes. Vertices are expanded only for selected modules. */
public record InspectionSnapshot(UUID core, boolean open, boolean available, int batch, boolean last,
                                 List<Row> rows) implements CustomPacketPayload {
    public static final int MAX_ROWS = HeartModules.LIMIT;
    private static final int BATCH_ROWS = 128;
    private static final int MAX_PIECES = ModuleRedstone.MAX_OUTPUT_FACES;
    private static final int MAX_NAME = 80, MAX_TYPE = 128;
    public static final Type<InspectionSnapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SableLife.MOD_ID, "inspect_batch"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InspectionSnapshot> CODEC = StreamCodec.of(
            InspectionSnapshot::write, InspectionSnapshot::read);

    public enum Shape { BOX, FACE, RING, SPHERE, ARROW, FRUSTUM, FAN, CROSS }

    /** A null anchor is fixed in the world. Otherwise the center is relative to the anchor block. */
    public record Piece(Shape shape, ModuleAnchor anchor, BlockPos second, boolean worldAxes,
                        Vec3 center, Vec3 size, Quaternionf rotation) {}

    public record Row(UUID id, int number, String name, String moduleType, List<Piece> pieces) {
        public Row {
            if (pieces.isEmpty() || pieces.size() > MAX_PIECES || name.length() > MAX_NAME || moduleType.length() > MAX_TYPE)
                throw new IllegalArgumentException("Invalid inspection module");
            pieces = List.copyOf(pieces);
        }
    }

    public InspectionSnapshot {
        if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("Too many inspection modules");
        rows = List.copyOf(rows);
    }

    public InspectionSnapshot(UUID core, boolean open, boolean available, List<Row> rows) {
        this(core, open, available, 0, true, rows);
    }

    public List<InspectionSnapshot> batches() {
        var result = new ArrayList<InspectionSnapshot>();
        for (int offset = 0; offset < rows.size(); offset += BATCH_ROWS) {
            int end = Math.min(rows.size(), offset + BATCH_ROWS);
            result.add(new InspectionSnapshot(core, open, available, offset / BATCH_ROWS, end == rows.size(), rows.subList(offset, end)));
        }
        return result.isEmpty() ? List.of(this) : List.copyOf(result);
    }

    public void send(ServerPlayer player) {
        for (var packet : batches()) PacketDistributor.sendToPlayer(player, packet);
    }

    public static InspectionSnapshot capture(UUID id, HeartBlockEntity core, boolean open) {
        var rows = new ArrayList<Row>();
        var modules = core.modules().all();
        for (int i = 0; i < Math.min(modules.size(), MAX_ROWS); i++) {
            var module = modules.get(i);
            if (!ModuleAvailability.enabled(core, module) || module.type().length() > MAX_TYPE) continue;
            var pieces = InspectionGeometry.pieces(core, module);
            if (pieces.isEmpty()) continue;
            rows.add(new Row(module.id(), i + 1, module.name().substring(0, Math.min(module.name().length(), MAX_NAME)),
                    module.type(), pieces));
        }
        return new InspectionSnapshot(id, open, true, rows);
    }

    private static void write(RegistryFriendlyByteBuf out, InspectionSnapshot snapshot) {
        count(snapshot.rows.size(), BATCH_ROWS);
        out.writeUUID(snapshot.core);
        out.writeBoolean(snapshot.open);
        out.writeBoolean(snapshot.available);
        out.writeVarInt(snapshot.batch);
        out.writeBoolean(snapshot.last);
        out.writeCollection(snapshot.rows, (buf, row) -> {
            buf.writeUUID(row.id);
            buf.writeVarInt(row.number);
            buf.writeUtf(row.name, MAX_NAME);
            buf.writeUtf(row.moduleType, MAX_TYPE);
            buf.writeCollection(row.pieces, InspectionSnapshot::writePiece);
        });
    }

    private static void writePiece(FriendlyByteBuf out, Piece piece) {
        out.writeEnum(piece.shape);
        out.writeNullable(piece.anchor, (buf, anchor) -> {
            buf.writeUUID(anchor.bodyId());
            buf.writeBlockPos(anchor.offset());
            buf.writeEnum(anchor.face());
        });
        out.writeNullable(piece.second, (buf, second) -> buf.writeBlockPos(second));
        out.writeBoolean(piece.worldAxes);
        if (piece.anchor == null) {
            out.writeDouble(piece.center.x);
            out.writeDouble(piece.center.y);
            out.writeDouble(piece.center.z);
        } else {
            writeLocal(out, piece.center);
        }
        writeLocal(out, piece.size);
        out.writeFloat(piece.rotation.x).writeFloat(piece.rotation.y).writeFloat(piece.rotation.z).writeFloat(piece.rotation.w);
    }

    private static InspectionSnapshot read(RegistryFriendlyByteBuf in) {
        var core = in.readUUID();
        boolean open = in.readBoolean();
        boolean available = in.readBoolean();
        int batch = count(in.readVarInt(), (MAX_ROWS - 1) / BATCH_ROWS);
        boolean last = in.readBoolean();
        int size = count(in.readVarInt(), BATCH_ROWS);
        var rows = new ArrayList<Row>(size);
        for (int i = 0; i < size; i++) {
            var id = in.readUUID();
            int number = in.readVarInt();
            var name = in.readUtf(MAX_NAME);
            var type = in.readUtf(MAX_TYPE);
            int pieceCount = count(in.readVarInt(), MAX_PIECES);
            var pieces = new ArrayList<Piece>(pieceCount);
            for (int j = 0; j < pieceCount; j++) pieces.add(readPiece(in));
            rows.add(new Row(id, number, name, type, pieces));
        }
        return new InspectionSnapshot(core, open, available, batch, last, rows);
    }

    private static Piece readPiece(FriendlyByteBuf in) {
        var shape = in.readEnum(Shape.class);
        var anchor = in.readNullable(buf -> new ModuleAnchor(buf.readUUID(), buf.readBlockPos(), buf.readEnum(Direction.class)));
        var second = in.readNullable(buf -> buf.readBlockPos());
        var worldAxes = in.readBoolean();
        var center = anchor == null ? new Vec3(in.readDouble(), in.readDouble(), in.readDouble()) : readLocal(in);
        var size = readLocal(in);
        var rotation = new Quaternionf(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        return new Piece(shape, anchor, second, worldAxes, center, size, rotation);
    }

    private static int count(int size, int maximum) {
        if (size < 0 || size > maximum) throw new IllegalArgumentException("Invalid inspection list size");
        return size;
    }

    private static void writeLocal(FriendlyByteBuf out, Vec3 point) {
        out.writeFloat((float) point.x).writeFloat((float) point.y).writeFloat((float) point.z);
    }

    private static Vec3 readLocal(FriendlyByteBuf in) {
        return new Vec3(in.readFloat(), in.readFloat(), in.readFloat());
    }

    @Override public Type<InspectionSnapshot> type() { return TYPE; }
}
