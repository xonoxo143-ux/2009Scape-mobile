package net.kdt.pojavlaunch;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Keeps every embedded-JVM UI call on one dedicated Android thread.
 *
 * The world publishes immutable snapshots on its own tick. This bridge only
 * copies those snapshots and queues semantic commands; it never reads or mutates
 * live world objects directly from the Android UI thread.
 */
final class LocalGameUiBridge implements AutoCloseable {
    interface Listener {
        void onState(JSONObject state);
    }

    private enum CommandType {
        EQUIP_INVENTORY,
        UNEQUIP_EQUIPMENT,
        INVENTORY_ACTION,
        EQUIPMENT_ACTION
    }

    private static final class Command {
        final CommandType type;
        final int slot;
        final String action;

        Command(CommandType type, int slot, String action) {
            this.type = type;
            this.slot = slot;
            this.action = action;
        }
    }

    private static final long POLL_MS = 100L;
    private static final String TAG = "SINGLEPLAYER_NATIVE_UI";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
    private final Listener listener;
    private final Thread worker;
    private volatile boolean running = true;

    LocalGameUiBridge(Listener listener) {
        this.listener = listener;
        worker = new Thread(this::runLoop, "SinglePlayerNativeUi");
        worker.setDaemon(true);
        worker.start();
    }

    void equipInventorySlot(int slot) {
        commands.offer(new Command(CommandType.EQUIP_INVENTORY, slot, null));
    }

    void unequipEquipmentSlot(int slot) {
        commands.offer(new Command(CommandType.UNEQUIP_EQUIPMENT, slot, null));
    }

    void inventoryAction(int slot, String action) {
        commands.offer(new Command(CommandType.INVENTORY_ACTION, slot, action));
    }

    void equipmentAction(int slot, String action) {
        commands.offer(new Command(CommandType.EQUIPMENT_ACTION, slot, action));
    }

    private void runLoop() {
        long previousSequence = 0L;
        boolean announced = false;
        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                drainCommands();

                long sequence = LocalGameUiNativeBridge.stateSequence();
                if (sequence > 0L && sequence != previousSequence) {
                    String json = LocalGameUiNativeBridge.stateJson();
                    if (json != null && !json.isEmpty()) {
                        JSONObject state = new JSONObject(json);
                        previousSequence = sequence;
                        if (!announced && !state.isNull("username")) {
                            announced = true;
                            Log.i(TAG, "SEMANTIC_STATE_CONNECTED schema="
                                    + state.optLong("schema", -1L));
                        }
                        mainHandler.post(() -> {
                            if (running) listener.onState(state);
                        });
                    }
                }

                Thread.sleep(POLL_MS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (Throwable failure) {
            Log.e(TAG, "Semantic UI bridge failed", failure);
        } finally {
            try {
                LocalGameUiNativeBridge.release();
            } catch (Throwable ignored) {
            }
        }
    }

    private void drainCommands() {
        for (int i = 0; i < 32; i++) {
            Command command = commands.poll();
            if (command == null) return;
            boolean accepted;
            switch (command.type) {
                case EQUIP_INVENTORY:
                    accepted = LocalGameUiNativeBridge.equipInventorySlot(command.slot);
                    break;
                case UNEQUIP_EQUIPMENT:
                    accepted = LocalGameUiNativeBridge.unequipEquipmentSlot(command.slot);
                    break;
                case INVENTORY_ACTION:
                    accepted = LocalGameUiNativeBridge.inventoryAction(
                            command.slot, command.action);
                    break;
                case EQUIPMENT_ACTION:
                    accepted = LocalGameUiNativeBridge.equipmentAction(
                            command.slot, command.action);
                    break;
                default:
                    accepted = false;
            }
            if (!accepted) {
                Log.w(TAG, "Command not accepted: " + command.type
                        + " slot=" + command.slot);
            }
        }
    }

    @Override
    public void close() {
        running = false;
        worker.interrupt();
    }
}
