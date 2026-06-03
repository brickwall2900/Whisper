package io.github.brickwall2900.processing.packets;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

final class PacketUtils {
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    public static void writeString(String string, ByteBuf buffer) {
        Objects.requireNonNull(string, "string == null");
        buffer.writeInt(ByteBufUtil.utf8Bytes(string));
        buffer.writeCharSequence(string, CHARSET);
    }

    public static String readString(ByteBuf buffer) {
        return String.valueOf(buffer.readCharSequence(buffer.readInt(), CHARSET));
    }

    public static <T> void writeNullable(@Nullable T obj, ByteBuf buffer, Consumer<@NotNull T> writer) {
        buffer.writeBoolean(obj != null);
        if (obj != null) {
            writer.accept(obj);
        }
    }

    public static <T> void writeNullable(@Nullable T obj, ByteBuf buffer, BiConsumer<@NotNull T, ByteBuf> writer) {
        buffer.writeBoolean(obj != null);
        if (obj != null) {
            writer.accept(obj, buffer);
        }
    }

    public static <T> @Nullable T readNullable(ByteBuf buffer, Supplier<T> reader) {
        boolean isNotNull = buffer.readBoolean();
        return isNotNull ? reader.get() : null;
    }

    public static <T> @Nullable T readNullable(ByteBuf buffer, Function<ByteBuf, T> reader) {
        boolean isNotNull = buffer.readBoolean();
        return isNotNull ? reader.apply(buffer) : null;
    }

    public static void writeUUID(UUID uuid, ByteBuf buffer) {
        Objects.requireNonNull(uuid, "UUID == null");
        buffer.writeLong(uuid.getMostSignificantBits());
        buffer.writeLong(uuid.getLeastSignificantBits());
    }

    public static UUID readUUID(ByteBuf buffer) {
        return new UUID(buffer.readLong(), buffer.readLong());
    }

    public static <T> void writeSet(Set<T> set, ByteBuf buffer, BiConsumer<T, ByteBuf> writer) {
        buffer.writeInt(set.size());
        for (T obj : set) {
            writer.accept(obj, buffer);
        }
    }

    public static <T> Set<T> readSet(Supplier<Set<T>> setSupplier, ByteBuf buffer, Function<ByteBuf, T> reader) {
        int size = buffer.readInt();
        Set<T> set = setSupplier.get();
        for (int i = 0; i < size; i++) {
            set.add(reader.apply(buffer));
        }
        return set;
    }

    public static void writeStringArray(String[] array, ByteBuf buffer) {
        buffer.writeInt(array.length);
        for (String x : array) {
            writeString(x, buffer);
        }
    }

    public static String[] readStringArray(ByteBuf buffer) {
        int length = buffer.readInt();
        String[] array = new String[length];
        for (int i = 0; i < length; i++) {
            array[i] = readString(buffer);
        }
        return array;
    }
}
