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
    public static byte[] identity(int uid, String build) throws IOException {
        if (uid % 100000 < 10000 || uid % 100000 > 19999 || !build.matches("[a-f0-9]{64}"))
            throw new IOException("identity_invalid");
        return ByteBuffer.allocate(68).order(ByteOrder.LITTLE_ENDIAN).putInt(uid)
            .put(build.getBytes(StandardCharsets.US_ASCII)).array();
    }
    public static int validateInit(Frame frame, String build) throws IOException {
        if (frame.kind != 10 || frame.payload.length != 68) throw new IOException("init_contract");
        int uid = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (!Arrays.equals(frame.payload, identity(uid, build))) throw new IOException("init_build_mismatch");
        return uid;
    }
    public static byte[] tagged(byte[] id, String json) throws IOException {
        byte[] text = json.getBytes(StandardCharsets.UTF_8);
        if (id.length != 68 || text.length + id.length > MAX) throw new IOException("tagged_frame_limit");
        byte[] result = Arrays.copyOf(id, id.length + text.length);
        System.arraycopy(text, 0, result, id.length, text.length);
        return result;
    }
    private Protocol() {}
}
