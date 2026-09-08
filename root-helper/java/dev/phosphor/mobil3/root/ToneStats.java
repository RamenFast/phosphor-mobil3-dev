package dev.phosphor.mobil3.root;

public final class ToneStats {
    public static final int RATE = 16000, LIMIT = 80000;
    public int frames, nonzero, crossings, zeroReads;
    public double squares, peak, real, imaginary;
    private short previous;
    private int firstCrossing = -1, lastCrossing = -1;
    public void add(short[] values, int count) {
        if (count < 0 || count > values.length || frames + count > LIMIT)
            throw new IllegalArgumentException("sample_count_limit");
        if (count == 0) zeroReads++;
        for (int i = 0; i < count; i++) {
            short raw = values[i]; double x = raw / 32768.0;
            if (raw != 0) nonzero++;
            if (frames > 0 && previous <= 0 && raw > 0) {
                crossings++;
                if (firstCrossing < 0) firstCrossing = frames;
                lastCrossing = frames;
            }
            previous = raw;
            squares += x * x; peak = Math.max(peak, Math.abs(x));
            double phase = 2 * Math.PI * 997 * frames / RATE;
            real += x * Math.cos(phase); imaginary += x * Math.sin(phase);
            frames++;
        }
    }
    public double rms() { return frames == 0 ? 0 : Math.sqrt(squares / frames); }
    public double frequency() { return crossings < 2 || lastCrossing <= firstCrossing ? 0 : (crossings - 1) * (double) RATE / (lastCrossing - firstCrossing); }
    public double toneRatio() { return squares == 0 || frames == 0 ? 0 : Math.min(1, 2 * (real * real + imaginary * imaginary) / (frames * squares)); }
    public boolean tonePresent() { return frames >= 64000 && nonzero > 32000 && rms() > 0.00001 && frequency() >= 975 && frequency() <= 1019 && toneRatio() >= 0.1; }
}
