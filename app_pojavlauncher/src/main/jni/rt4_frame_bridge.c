#include <android/bitmap.h>
#include <limits.h>
#include "awt_graphics.h"
#include "rt4_pixels.h"

/* A recreated Android surface gets a new renderer thread. No JNIEnv or local
 * reference may be reused by that replacement thread. */
static __thread jclass frame_class;
static __thread jmethodID acquire_frame;
static __thread jmethodID frame_sequence;
static __thread jmethodID disable_frame;
static __thread int direct_unavailable;

void awt_release_direct_frame(JNIEnv* env) {
    if (frame_class) (*env)->DeleteGlobalRef(env, frame_class);
    frame_class = NULL;
    acquire_frame = NULL;
    frame_sequence = NULL;
    disable_frame = NULL;
    direct_unavailable = 0;
}

static int resolve_frame_bridge(JNIEnv* env) {
    if (direct_unavailable) return 0;
    if (frame_class) return 1;
    frame_class = awt_find_runtime_class(env, "rt4/DirectFrameBridge", "rt4.DirectFrameBridge");
    if (frame_class) {
        acquire_frame = (*env)->GetStaticMethodID(env, frame_class, "acquireFrame", "(II)[I");
        if (!(*env)->ExceptionCheck(env))
            frame_sequence = (*env)->GetStaticMethodID(env, frame_class, "frameSequence", "()J");
        if (!(*env)->ExceptionCheck(env))
            disable_frame = (*env)->GetStaticMethodID(env, frame_class, "disable", "()V");
    }
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    if (!frame_class || !acquire_frame || !frame_sequence || !disable_frame) {
        awt_release_direct_frame(env);
        direct_unavailable = 1; /* An older payload uses the retained Cacio path. */
        return 0;
    }
    return 1;
}

JNIEXPORT jlong JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_renderRT4Frame(
        JNIEnv* android_env, jclass unused, jobject bitmap, jlong previous_sequence) {
    (void) unused;
    AndroidBitmapInfo info;
    if (!bitmap || AndroidBitmap_getInfo(android_env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS
            || info.format != ANDROID_BITMAP_FORMAT_RGBA_8888
            || !info.width || !info.height || info.width > INT_MAX || info.height > INT_MAX
            || (uint64_t) info.width * info.height > INT_MAX
            || (uint64_t) info.width * 4 > info.stride) return 0;

    JNIEnv* env = awt_get_graphics_env();
    if (!env || !resolve_frame_bridge(env)) return 0;
    if ((*env)->MonitorEnter(env, frame_class) != JNI_OK) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        return 0;
    }

    jlong result = 0;
    jintArray pixels = NULL;
    void* output = NULL;
    int bitmap_locked = 0;
    int failed = 0;
    pixels = (jintArray) (*env)->CallStaticObjectMethod(env, frame_class,
            acquire_frame, (jint) info.width, (jint) info.height);
    if ((*env)->ExceptionCheck(env)) { failed = 1; goto cleanup; }
    if (!pixels) goto cleanup; /* Bootstrap, rebuild, or waiting for the first frame. */

    result = (*env)->CallStaticLongMethod(env, frame_class, frame_sequence);
    if ((*env)->ExceptionCheck(env)) { failed = 1; goto cleanup; }
    if (result == previous_sequence) goto cleanup;
    if ((*env)->GetArrayLength(env, pixels) != (jsize) (info.width * info.height)) {
        failed = 1;
        goto cleanup;
    }
    if (AndroidBitmap_lockPixels(android_env, bitmap, &output) != ANDROID_BITMAP_RESULT_SUCCESS) {
        failed = 1;
        goto cleanup;
    }
    bitmap_locked = 1;
    jint* input = (*env)->GetPrimitiveArrayCritical(env, pixels, NULL);
    if (!input) { failed = 1; goto cleanup; }
    /* No blocking, allocation, or other JNI call inside the critical region. */
    int copied = rt4_copy_rgba(output, info.stride, (const uint32_t*) input,
            info.width, info.height);
    (*env)->ReleasePrimitiveArrayCritical(env, pixels, input, JNI_ABORT);
    if (!copied) failed = 1;

cleanup:
    if (bitmap_locked && AndroidBitmap_unlockPixels(android_env, bitmap) != ANDROID_BITMAP_RESULT_SUCCESS)
        failed = 1;
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); failed = 1; }
    if ((*android_env)->ExceptionCheck(android_env)) {
        (*android_env)->ExceptionClear(android_env);
        failed = 1;
    }
    if (pixels) (*env)->DeleteLocalRef(env, pixels);
    if (failed) {
        (*env)->CallStaticVoidMethod(env, frame_class, disable_frame);
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        direct_unavailable = 1;
        result = 0;
    }
    (*env)->MonitorExit(env, frame_class);
    return result;
}
