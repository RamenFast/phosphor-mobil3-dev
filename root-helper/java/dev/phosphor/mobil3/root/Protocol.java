package dev.phosphor.mobil3.root;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class Protocol {
    public static final int MAGIC = 0x31524150, MAX = 4096;
    public static final class Frame {
        public final int kind;
        public final byte[] payload;
        Frame(int kind, byte[] payload) { this.kind = kind; this.payload = payload; }
    }
    public static byte[] encode(int kind, byte[] payload) throws IOException {
        if (payload.length > MAX) throw new IOException("frame_overflow");
        return ByteBuffer.allocate(12 + payload.length).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(MAGIC).putInt(kind).putInt(payload.length).put(payload).array();
    }
    public static Frame read(InputStream in) throws IOException {
        byte[] header = exact(in, 12);
        ByteBuffer b = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
        if (b.getInt() != MAGIC) throw new IOException("frame_magic");
        int kind = b.getInt(), size = b.getInt();
        if (size < 0 || size > MAX) throw new IOException("frame_length");
        return new Frame(kind, exact(in, size));
    }
    private static byte[] exact(InputStream in, int size) throws IOException {
        byte[] b = new byte[size];
        for (int n = 0; n < size;) {
            int got = in.read(b, n, size - n);
            if (got < 0) throw new EOFException("partial_frame_or_control_eof");
            if (got == 0) throw new IOException("zero_progress_read");
            n += got;
        }
        return b;
    }
    public static byte[] identity(int uid, String build, long generation, int mode) throws IOException {
        if (generation <= 0 || mode < 0 || mode > 5 || uid % 100000 < 10000 || uid % 100000 > 19999 || !build.matches("[a-f0-9]{64}"))
            throw new IOException("identity_invalid");
        return ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN).putInt(uid)
            .put(build.getBytes(StandardCharsets.US_ASCII)).putLong(generation).putInt(mode).array();
    }
    public static int validateInit(Frame frame, String build) throws IOException {
        if (frame.kind != 10 || frame.payload.length != 80) throw new IOException("init_contract");
        int uid = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (!Arrays.equals(frame.payload, identity(uid, build, generation(frame.payload), mode(frame.payload)))) throw new IOException("init_build_mismatch");
        return uid;
    }
    public static byte[] tagged(byte[] id, String json) throws IOException {
        byte[] text = json.getBytes(StandardCharsets.UTF_8);
        if (id.length != 80 || text.length + id.length > MAX) throw new IOException("tagged_frame_limit");
        byte[] result = Arrays.copyOf(id, id.length + text.length);
        System.arraycopy(text, 0, result, id.length, text.length);
        return result;
    }
    public static long generation(byte[] id) { return ByteBuffer.wrap(id).order(ByteOrder.LITTLE_ENDIAN).getLong(68); }
    public static int mode(byte[] id) { return ByteBuffer.wrap(id).order(ByteOrder.LITTLE_ENDIAN).getInt(76); }
    public static void control(Frame f, long generation) throws IOException {
        if (f.payload.length != 8 || ByteBuffer.wrap(f.payload).order(ByteOrder.LITTLE_ENDIAN).getLong() != generation)
            throw new IOException("control_generation");
    }
    public static int readCount(int mode, int block, long frames) {
        if (block < 0 || frames < 0 || (mode == 3 && frames > 80000)) throw new IllegalArgumentException("finite_read_limit");
        return mode == 3 ? (int)Math.min(block, 80000 - frames) : block;
    }
    public static byte[] progress(byte[] id, long sequence) {
        if ((mode(id) == 2 || mode(id) == 3) && (sequence < 0 || sequence == Long.MAX_VALUE)) throw new IllegalArgumentException("progress_sequence");
        return ByteBuffer.allocate(88).order(ByteOrder.LITTLE_ENDIAN).put(id).putLong(sequence).array();
    }
    public static byte[] epochAck(byte[] id, RootEpoch.Binding binding, long nextPcmSequence) throws IOException {
        if (id.length != 80 || (mode(id) != 2 && mode(id) != 3) || binding == null ||
            binding.controlSequence < 1 || binding.controlSequence == Long.MAX_VALUE || nextPcmSequence < 0)
            throw new IOException("epoch_ack_contract");
        return ByteBuffer.allocate(104).order(ByteOrder.LITTLE_ENDIAN).put(id)
            .putLong(binding.controlSequence).putLong(binding.epoch).putLong(nextPcmSequence).array();
    }
    public static byte[] pcm(byte[] id, long sequence, RootEpoch.Binding binding, short[] block, int count) throws IOException {
        if (id.length != 80 || (mode(id) != 2 && mode(id) != 3) || sequence < 0 || sequence == Long.MAX_VALUE ||
            binding == null || binding.controlSequence < 1 || binding.controlSequence == Long.MAX_VALUE ||
            count < 1 || count > 160 || count > block.length) throw new IOException("pcm_contract");
        ByteBuffer b = ByteBuffer.allocate(120 + count*2).order(ByteOrder.LITTLE_ENDIAN).put(id).putLong(sequence)
            .putInt(16000).putInt(1).putInt(2).putInt(count).putLong(binding.controlSequence).putLong(binding.epoch);
        for (int i=0;i<count;i++) b.putShort(block[i]);
        return b.array();
    }
    private Protocol() {}

}
