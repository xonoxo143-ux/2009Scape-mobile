#include <jni.h>
#include <string.h>
#include "awt_graphics.h"

static __thread jclass class_ui_state;
static __thread jclass class_ui_commands;
static __thread jmethodID method_sequence;
static __thread jmethodID method_snapshot_json;
static __thread jmethodID method_equip_inventory;
static __thread jmethodID method_unequip_equipment;
static __thread jmethodID method_inventory_action;
static __thread jmethodID method_equipment_action;

static void clear_runtime_exception(JNIEnv* env) {
    if (env && (*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
}

static jboolean resolve_state(JNIEnv* env) {
    if (!class_ui_state) {
        class_ui_state = awt_find_runtime_class(env,
                "core/local/ui/LocalPlayerUiState",
                "core.local.ui.LocalPlayerUiState");
        if (!class_ui_state) return JNI_FALSE;
    }
    if (!method_sequence) {
        method_sequence = (*env)->GetStaticMethodID(env, class_ui_state, "sequence", "()J");
        if (!method_sequence) { clear_runtime_exception(env); return JNI_FALSE; }
    }
    if (!method_snapshot_json) {
        method_snapshot_json = (*env)->GetStaticMethodID(env, class_ui_state,
                "snapshotJson", "()Ljava/lang/String;");
        if (!method_snapshot_json) { clear_runtime_exception(env); return JNI_FALSE; }
    }
    return JNI_TRUE;
}

static jboolean resolve_commands(JNIEnv* env) {
    if (!class_ui_commands) {
        class_ui_commands = awt_find_runtime_class(env,
                "core/local/ui/LocalPlayerUiCommands",
                "core.local.ui.LocalPlayerUiCommands");
        if (!class_ui_commands) return JNI_FALSE;
    }
    if (!method_equip_inventory) {
        method_equip_inventory = (*env)->GetStaticMethodID(env, class_ui_commands,
                "equipInventorySlot", "(I)Z");
        if (!method_equip_inventory) { clear_runtime_exception(env); return JNI_FALSE; }
    }
    if (!method_unequip_equipment) {
        method_unequip_equipment = (*env)->GetStaticMethodID(env, class_ui_commands,
                "unequipEquipmentSlot", "(I)Z");
        if (!method_unequip_equipment) { clear_runtime_exception(env); return JNI_FALSE; }
    }
    if (!method_inventory_action) {
        method_inventory_action = (*env)->GetStaticMethodID(env, class_ui_commands,
                "inventoryAction", "(ILjava/lang/String;)Z");
        if (!method_inventory_action) { clear_runtime_exception(env); return JNI_FALSE; }
    }
    if (!method_equipment_action) {
        method_equipment_action = (*env)->GetStaticMethodID(env, class_ui_commands,
                "equipmentAction", "(ILjava/lang/String;)Z");
        if (!method_equipment_action) { clear_runtime_exception(env); return JNI_FALSE; }
    }
    return JNI_TRUE;
}

JNIEXPORT jlong JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_localUiStateSequence(
        JNIEnv* android_env, jclass clazz) {
    (void) android_env;
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime || !resolve_state(runtime)) return 0;
    jlong sequence = (*runtime)->CallStaticLongMethod(runtime, class_ui_state, method_sequence);
    if ((*runtime)->ExceptionCheck(runtime)) {
        (*runtime)->ExceptionClear(runtime);
        return 0;
    }
    return sequence;
}

JNIEXPORT jstring JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_localUiStateJson(
        JNIEnv* android_env, jclass clazz) {
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime || !resolve_state(runtime)) return NULL;

    jstring source = (jstring) (*runtime)->CallStaticObjectMethod(
            runtime, class_ui_state, method_snapshot_json);
    if (!source || (*runtime)->ExceptionCheck(runtime)) {
        clear_runtime_exception(runtime);
        if (source) (*runtime)->DeleteLocalRef(runtime, source);
        return NULL;
    }

    jsize length = (*runtime)->GetStringLength(runtime, source);
    const jchar* chars = (*runtime)->GetStringChars(runtime, source, NULL);
    if (!chars) {
        clear_runtime_exception(runtime);
        (*runtime)->DeleteLocalRef(runtime, source);
        return NULL;
    }
    jstring result = (*android_env)->NewString(android_env, chars, length);
    (*runtime)->ReleaseStringChars(runtime, source, chars);
    (*runtime)->DeleteLocalRef(runtime, source);
    if ((*android_env)->ExceptionCheck(android_env)) {
        (*android_env)->ExceptionClear(android_env);
        return NULL;
    }
    return result;
}

static jboolean invoke_slot(JNIEnv* runtime, jmethodID method, jint slot) {
    jboolean result = (*runtime)->CallStaticBooleanMethod(
            runtime, class_ui_commands, method, slot);
    if ((*runtime)->ExceptionCheck(runtime)) {
        (*runtime)->ExceptionClear(runtime);
        return JNI_FALSE;
    }
    return result;
}

static jboolean invoke_action(JNIEnv* android_env, JNIEnv* runtime,
        jmethodID method, jint slot, jstring action) {
    if (!action) return JNI_FALSE;
    const jchar* chars = (*android_env)->GetStringChars(android_env, action, NULL);
    if (!chars) return JNI_FALSE;
    jsize length = (*android_env)->GetStringLength(android_env, action);
    jstring runtime_action = (*runtime)->NewString(runtime, chars, length);
    (*android_env)->ReleaseStringChars(android_env, action, chars);
    if (!runtime_action) {
        clear_runtime_exception(runtime);
        return JNI_FALSE;
    }
    jboolean result = (*runtime)->CallStaticBooleanMethod(
            runtime, class_ui_commands, method, slot, runtime_action);
    (*runtime)->DeleteLocalRef(runtime, runtime_action);
    if ((*runtime)->ExceptionCheck(runtime)) {
        (*runtime)->ExceptionClear(runtime);
        return JNI_FALSE;
    }
    return result;
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_localUiEquipInventorySlot(
        JNIEnv* android_env, jclass clazz, jint slot) {
    (void) android_env;
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime || !resolve_commands(runtime)) return JNI_FALSE;
    return invoke_slot(runtime, method_equip_inventory, slot);
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_localUiUnequipEquipmentSlot(
        JNIEnv* android_env, jclass clazz, jint slot) {
    (void) android_env;
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime || !resolve_commands(runtime)) return JNI_FALSE;
    return invoke_slot(runtime, method_unequip_equipment, slot);
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_localUiInventoryAction(
        JNIEnv* android_env, jclass clazz, jint slot, jstring action) {
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime || !resolve_commands(runtime)) return JNI_FALSE;
    return invoke_action(android_env, runtime, method_inventory_action, slot, action);
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_localUiEquipmentAction(
        JNIEnv* android_env, jclass clazz, jint slot, jstring action) {
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime || !resolve_commands(runtime)) return JNI_FALSE;
    return invoke_action(android_env, runtime, method_equipment_action, slot, action);
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseLocalUiBridge(
        JNIEnv* android_env, jclass clazz) {
    (void) android_env;
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime) return;
    if (class_ui_state) (*runtime)->DeleteGlobalRef(runtime, class_ui_state);
    if (class_ui_commands) (*runtime)->DeleteGlobalRef(runtime, class_ui_commands);
    class_ui_state = NULL;
    class_ui_commands = NULL;
    method_sequence = NULL;
    method_snapshot_json = NULL;
    method_equip_inventory = NULL;
    method_unequip_equipment = NULL;
    method_inventory_action = NULL;
    method_equipment_action = NULL;
    awt_release_graphics_env();
}
