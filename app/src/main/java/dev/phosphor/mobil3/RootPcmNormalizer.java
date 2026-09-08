package dev.phosphor.mobil3;

/** Causal 3:1 interpolation. Two identical channels, not recovered stereo. */
public final class RootPcmNormalizer {
    private float previous;
    private boolean initialized;
    public float[] convert(short[] mono) {
        if (mono.length < 1 || mono.length > 160) throw new IllegalArgumentException("pcm_count");
        float[] output = new float[mono.length * 6];
        int at = 0;
        for (short sample : mono) {
            float value = sample / 32768f;
            float before = initialized ? previous : value;
            for (int phase = 0; phase < 3; phase++) {
                float interpolated = before + (value - before) * (phase / 3f);
                output[at++] = interpolated;
                output[at++] = interpolated;
            }
            previous = value;
            initialized = true;
        }
        return output;
    }
}
