package dev.phosphor.mobil3.nexus.binder;

interface IPhosphorNexusBinder {
    String transact(String requestJson, IBinder clientToken);
}
