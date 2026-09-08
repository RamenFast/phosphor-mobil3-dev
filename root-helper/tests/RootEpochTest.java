package dev.phosphor.mobil3.root;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class RootEpochTest {
    private static int checks;
    private static void check(boolean value) { checks++; if (!value) throw new AssertionError("check " + checks); }
    private interface Action { void run() throws Exception; }
    private static void rejects(Action action) throws Exception {
        try { action.run(); throw new AssertionError("accepted invalid epoch"); }
        catch (IOException | IllegalArgumentException expected) { checks++; }
    }
    private static ByteBuffer bytes(int size) { return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN); }
    private static Protocol.Frame control(int kind, long sequence, long epoch) throws IOException {
        byte[] payload = kind == 4 ? bytes(24).putLong(42).putLong(sequence).putLong(epoch).array() : bytes(8).putLong(42).array();
        return Protocol.read(new ByteArrayInputStream(Protocol.encode(kind, payload)));
    }
    public static void main(String[] args) throws Exception {
        byte[] id = Protocol.identity(10401, "a".repeat(64), 42, 2);
        RootEpoch state = new RootEpoch(42, 2);
        rejects(state::beforeRead);
        rejects(() -> state.accept(control(2, 0, 0)));
        check(state.accept(control(4, 1, 0)) == RootEpoch.Action.EPOCH);
        byte[] ack = Protocol.epochAck(id, state.adopted(), 0);
        check(ack.length == 104 && ByteBuffer.wrap(ack).order(ByteOrder.LITTLE_ENDIAN).getLong(96) == 0);
        rejects(state::beforeRead);
        check(state.accept(control(2, 0, 0)) == RootEpoch.Action.START);
        rejects(() -> state.accept(control(2, 0, 0)));
        rejects(() -> state.accept(control(4, 1, 1)));
        rejects(() -> state.accept(control(4, 2, 0)));

        CountDownLatch readHeld = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicReference<byte[]> oldWire = new AtomicReference<>();
        AtomicReference<Protocol.Frame> queuedControl = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread owner = new Thread(() -> {
            try {
                RootEpoch.Binding read = state.beforeRead();
                readHeld.countDown();
                if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("held read timeout");
                oldWire.set(Protocol.encode(15, Protocol.pcm(id, 0, read, new short[]{32767}, 1)));
                state.accept(queuedControl.get());
            } catch (Throwable error) { failure.set(error); }
        });
        owner.start();
        check(readHeld.await(2, TimeUnit.SECONDS));
        // The queued control cannot mutate the single owner's held read snapshot.
        queuedControl.set(control(4, 2, Long.MIN_VALUE));
        release.countDown();
        owner.join(2000);
        check(!owner.isAlive() && failure.get() == null);
        Protocol.Frame old = Protocol.read(new ByteArrayInputStream(oldWire.get()));
        ByteBuffer oldPcm = ByteBuffer.wrap(old.payload).order(ByteOrder.LITTLE_ENDIAN);
        check(oldPcm.getLong(104) == 1 && oldPcm.getLong(112) == 0 && oldPcm.getShort(120) == 32767);
        check(state.beforeRead().epoch == Long.MIN_VALUE);
        byte[] newAck = Protocol.epochAck(id, state.adopted(), 1);
        byte[] fresh = Protocol.pcm(id, 1, state.beforeRead(), new short[160], 160);
        check(fresh.length == 440 && ByteBuffer.wrap(fresh).order(ByteOrder.LITTLE_ENDIAN).getLong(112) == Long.MIN_VALUE);
        rejects(() -> state.accept(control(4, 3, 1)));
        rejects(() -> state.accept(control(4, Long.MAX_VALUE, -1)));
        for (int count : new int[]{0, 161}) rejects(() -> Protocol.pcm(id, 1, state.beforeRead(), new short[161], count));
        rejects(() -> Protocol.pcm(id, Long.MAX_VALUE, state.beforeRead(), new short[1], 1));
        rejects(() -> Protocol.progress(id, Long.MAX_VALUE));
        for (int mode : new int[]{0, 1, 4, 5}) {
            RootEpoch forbidden = new RootEpoch(42, mode);
            rejects(() -> forbidden.accept(control(4, 1, 0)));
            rejects(() -> Protocol.epochAck(Protocol.identity(10401, "a".repeat(64), 42, mode), state.adopted(), 0));
        }
        long frames = 0;
        for (int i = 0; i < 501; i++) frames += Protocol.readCount(3, 160, frames);
        check(frames == 80000 && Protocol.readCount(2, 160, 80000) == 160);
        check(state.accept(control(3, 0, 0)) == RootEpoch.Action.STOP);
        rejects(state::beforeRead);
        rejects(() -> state.accept(control(4, 3, -1)));
        if (args.length == 1) {
            Path output = Path.of(args[0]);
            Files.write(output.resolve("initial-ack.bin"), Protocol.encode(17, ack));
            Files.write(output.resolve("old-pcm.bin"), oldWire.get());
            Files.write(output.resolve("new-ack.bin"), Protocol.encode(17, newAck));
            Files.write(output.resolve("new-pcm.bin"), Protocol.encode(15, fresh));
        }
        System.out.println("Root epoch host checks passed: " + checks);
    }
    private RootEpochTest() {}
}
