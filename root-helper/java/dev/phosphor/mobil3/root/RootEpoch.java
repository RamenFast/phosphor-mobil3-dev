package dev.phosphor.mobil3.root;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Single helper owner. A read keeps its immutable binding through serialization. */
public final class RootEpoch {
    public enum Action { EPOCH, START, STOP }
    public static final class Binding {
        public final long controlSequence;
        public final long epoch;
        Binding(long controlSequence, long epoch) { this.controlSequence = controlSequence; this.epoch = epoch; }
    }
    private final long generation;
    private final int mode;
    private Binding binding;
    private long nextControl = 1;
    private boolean started;
    private boolean stopped;

    public RootEpoch(long generation, int mode) { this.generation = generation; this.mode = mode; }

    public Action accept(Protocol.Frame frame) throws IOException {
        if ((mode != 2 && mode != 3) || stopped) throw new IOException("epoch_control_state");
        if (frame.kind == 4) {
            if (frame.payload.length != 24) throw new IOException("epoch_control_length");
            ByteBuffer b = ByteBuffer.wrap(frame.payload).order(ByteOrder.LITTLE_ENDIAN);
            long actualGeneration = b.getLong(), sequence = b.getLong(), epoch = b.getLong();
            if (actualGeneration != generation || sequence != nextControl || sequence == Long.MAX_VALUE)
                throw new IOException("epoch_control_sequence_or_generation");
            if (binding != null && Long.compareUnsigned(epoch, binding.epoch) <= 0)
                throw new IOException("epoch_regression");
            binding = new Binding(sequence, epoch);
            nextControl++;
            return Action.EPOCH;
        }
        Protocol.control(frame, generation);
        if (frame.kind == 3) { stopped = true; return Action.STOP; }
        if (frame.kind != 2 || started || binding == null) throw new IOException("epoch_start_state");
        started = true;
        return Action.START;
    }

    public Binding adopted() throws IOException {
        if (binding == null) throw new IOException("epoch_unbound");
        return binding;
    }

    public Binding beforeRead() throws IOException {
        if (!started || stopped) throw new IOException("epoch_read_state");
        return adopted();
    }
}
