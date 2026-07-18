package dev.phosphor.mobil3

// The one JNI seam. Grows per plan (surface lifecycle, command channel, events);
// M0 carries only the engine handshake.
object PhosphorNative {
    init {
        System.loadLibrary("phosphor_mobil3_core")
    }

    /** JSON string from the Rust core proving the engine crates link and run on-device. */
    external fun engineInfo(): String
}
