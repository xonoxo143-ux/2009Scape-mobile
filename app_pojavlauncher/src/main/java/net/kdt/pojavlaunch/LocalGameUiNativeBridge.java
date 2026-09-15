package net.kdt.pojavlaunch;

/** JNI access to the embedded single-player world from one dedicated Android thread. */
final class LocalGameUiNativeBridge {
    static {
        System.loadLibrary("pojavexec_awt");
    }

    private LocalGameUiNativeBridge() {}

    static native long stateSequence();
    static native String stateJson();
    static native boolean equipInventorySlot(int slot);
    static native boolean unequipEquipmentSlot(int slot);
    static native boolean inventoryAction(int slot, String action);
    static native boolean equipmentAction(int slot, String action);
    static native void release();
}
