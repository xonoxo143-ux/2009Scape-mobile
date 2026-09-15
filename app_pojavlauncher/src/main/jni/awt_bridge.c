#include <jni.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>
#include "awt_graphics.h"

static JavaVM* dalvikJavaVMPtr;

static JavaVM* runtimeJavaVMPtr;
static __thread JNIEnv* runtimeJNIEnvPtr_GRAPHICS;
static __thread int graphics_attached;
static JNIEnv* runtimeJNIEnvPtr_INPUT;
static __thread jclass class_CTCScreen;
static __thread jmethodID method_GetRGB;

jclass class_CTCAndroidInput;
jmethodID method_ReceiveInput;

jclass class_MobileGestureBridge;
jmethodID method_ReceiveMobileGesture;

jclass class_MobileLifecycleBridge;
jmethodID method_SetAppPaused;

jclass class_MainActivity;
jmethodID method_OpenLink;
jmethodID method_OpenPath;
jmethodID method_QuerySystemClipboard;
jmethodID method_PutClipboardData;

jclass class_Frame;
jclass class_Rectangle;
jclass class_CTCClipboard = NULL;
jmethodID constructor_Rectangle;
jmethodID method_GetFrames;
jmethodID method_GetBounds;
jmethodID method_SetBounds;
jmethodID method_SystemClipboardDataReceived = NULL;

jfieldID field_x;
jfieldID field_y;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    if (dalvikJavaVMPtr == NULL) {
        //Save dalvik global JavaVM pointer
        dalvikJavaVMPtr = vm;
        JNIEnv *env = NULL;
        (*vm)->GetEnv(vm, (void**)&env, JNI_VERSION_1_4);
        class_MainActivity = (*env)->NewGlobalRef(env,(*env)->FindClass(env, "net/kdt/pojavlaunch/MainActivity"));
        method_OpenLink= (*env)->GetStaticMethodID(env, class_MainActivity, "openLink", "(Ljava/lang/String;)V");
        method_OpenPath= (*env)->GetStaticMethodID(env, class_MainActivity, "openLink", "(Ljava/lang/String;)V");
        method_QuerySystemClipboard = (*env)->GetStaticMethodID(env, class_MainActivity, "querySystemClipboard", "()V");
        method_PutClipboardData = (*env)->GetStaticMethodID(env, class_MainActivity, "putClipboardData", "(Ljava/lang/String;Ljava/lang/String;)V");
    } else if (dalvikJavaVMPtr != vm) {
        runtimeJavaVMPtr = vm;
    }

    return JNI_VERSION_1_4;
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeSendData(JNIEnv* env, jclass clazz, jint type, jint i1, jint i2, jint i3, jint i4) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }

    if (type >= 2000 && type <= 2006) {
        if (method_ReceiveMobileGesture == NULL) {
            jclass localClass = (*runtimeJNIEnvPtr_INPUT)->FindClass(
                runtimeJNIEnvPtr_INPUT, "singleplayer/MobileGestureBridge");
            if (localClass == NULL) {
                if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
                    (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
                }

                // Threads attached from Android do not necessarily inherit the
                // embedded JVM application's class loader. Resolve the bridge
                // explicitly through the runtime system class loader.
                jclass classLoaderClass = (*runtimeJNIEnvPtr_INPUT)->FindClass(
                    runtimeJNIEnvPtr_INPUT, "java/lang/ClassLoader");
                if (classLoaderClass != NULL) {
                    jmethodID getSystemClassLoader = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(
                        runtimeJNIEnvPtr_INPUT,
                        classLoaderClass,
                        "getSystemClassLoader",
                        "()Ljava/lang/ClassLoader;");
                    jmethodID loadClass = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(
                        runtimeJNIEnvPtr_INPUT,
                        classLoaderClass,
                        "loadClass",
                        "(Ljava/lang/String;)Ljava/lang/Class;");
                    jobject loader = (*runtimeJNIEnvPtr_INPUT)->CallStaticObjectMethod(
                        runtimeJNIEnvPtr_INPUT,
                        classLoaderClass,
                        getSystemClassLoader);
                    jstring className = (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(
                        runtimeJNIEnvPtr_INPUT,
                        "singleplayer.MobileGestureBridge");
                    localClass = (jclass) (*runtimeJNIEnvPtr_INPUT)->CallObjectMethod(
                        runtimeJNIEnvPtr_INPUT,
                        loader,
                        loadClass,
                        className);
                    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(
                        runtimeJNIEnvPtr_INPUT, className);
                    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(
                        runtimeJNIEnvPtr_INPUT, loader);
                    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(
                        runtimeJNIEnvPtr_INPUT, classLoaderClass);
                }
                if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
                    (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
                    localClass = NULL;
                }
                if (localClass == NULL) {
                    return;
                }
            }
            class_MobileGestureBridge = (*runtimeJNIEnvPtr_INPUT)->NewGlobalRef(
                runtimeJNIEnvPtr_INPUT, localClass);
            (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, localClass);
            method_ReceiveMobileGesture = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(
                runtimeJNIEnvPtr_INPUT,
                class_MobileGestureBridge,
                "receive",
                "(IIIII)V");
            if (method_ReceiveMobileGesture == NULL) {
                if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
                    (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
                }
                return;
            }
        }

        (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(
            runtimeJNIEnvPtr_INPUT,
            class_MobileGestureBridge,
            method_ReceiveMobileGesture,
            type, i1, i2, i3, i4
        );
        if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
        }
        return;
    }

    if (method_ReceiveInput == NULL) {
        class_CTCAndroidInput = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "net/java/openjdk/cacio/ctc/CTCAndroidInput");
        if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
            class_CTCAndroidInput = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "com/github/caciocavallosilano/cacio/ctc/CTCAndroidInput");
        }
        assert(class_CTCAndroidInput != NULL);
        method_ReceiveInput = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(runtimeJNIEnvPtr_INPUT, class_CTCAndroidInput, "receiveData", "(IIIII)V");
        assert(method_ReceiveInput != NULL);
    }
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(
        runtimeJNIEnvPtr_INPUT,
        class_CTCAndroidInput,
        method_ReceiveInput,
        type, i1, i2, i3, i4
    );
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeSetMobilePaused(
        JNIEnv* env, jclass clazz, jboolean paused) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        }
        (*runtimeJavaVMPtr)->AttachCurrentThread(
            runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
    }

    if (method_SetAppPaused == NULL) {
        jclass localClass = (*runtimeJNIEnvPtr_INPUT)->FindClass(
            runtimeJNIEnvPtr_INPUT, "singleplayer/MobileLifecycleBridge");
        if (localClass == NULL) {
            if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
                (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
            }

            jclass classLoaderClass = (*runtimeJNIEnvPtr_INPUT)->FindClass(
                runtimeJNIEnvPtr_INPUT, "java/lang/ClassLoader");
            if (classLoaderClass != NULL) {
                jmethodID getSystemClassLoader = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(
                    runtimeJNIEnvPtr_INPUT,
                    classLoaderClass,
                    "getSystemClassLoader",
                    "()Ljava/lang/ClassLoader;");
                jmethodID loadClass = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(
                    runtimeJNIEnvPtr_INPUT,
                    classLoaderClass,
                    "loadClass",
                    "(Ljava/lang/String;)Ljava/lang/Class;");
                jobject loader = (*runtimeJNIEnvPtr_INPUT)->CallStaticObjectMethod(
                    runtimeJNIEnvPtr_INPUT,
                    classLoaderClass,
                    getSystemClassLoader);
                jstring className = (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(
                    runtimeJNIEnvPtr_INPUT,
                    "singleplayer.MobileLifecycleBridge");
                localClass = (jclass) (*runtimeJNIEnvPtr_INPUT)->CallObjectMethod(
                    runtimeJNIEnvPtr_INPUT,
                    loader,
                    loadClass,
                    className);
                (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(
                    runtimeJNIEnvPtr_INPUT, className);
                (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(
                    runtimeJNIEnvPtr_INPUT, loader);
                (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(
                    runtimeJNIEnvPtr_INPUT, classLoaderClass);
            }

            if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
                (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
                localClass = NULL;
            }
            if (localClass == NULL) {
                return;
            }
        }

        class_MobileLifecycleBridge = (*runtimeJNIEnvPtr_INPUT)->NewGlobalRef(
            runtimeJNIEnvPtr_INPUT, localClass);
        (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(
            runtimeJNIEnvPtr_INPUT, localClass);
        method_SetAppPaused = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(
            runtimeJNIEnvPtr_INPUT,
            class_MobileLifecycleBridge,
            "setAppPaused",
            "(Z)V");
        if (method_SetAppPaused == NULL) {
            if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
                (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
            }
            return;
        }
    }

    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(
        runtimeJNIEnvPtr_INPUT,
        class_MobileLifecycleBridge,
        method_SetAppPaused,
        paused);
    if ((*runtimeJNIEnvPtr_INPUT)->ExceptionCheck(runtimeJNIEnvPtr_INPUT) == JNI_TRUE) {
        (*runtimeJNIEnvPtr_INPUT)->ExceptionClear(runtimeJNIEnvPtr_INPUT);
    }
}

/* The graphics attachment and all cached references belong to one Android
 * renderer thread, including after SurfaceTexture recreation. */
JNIEnv* awt_get_graphics_env(void) {
    if (runtimeJNIEnvPtr_GRAPHICS) return runtimeJNIEnvPtr_GRAPHICS;
    if (!runtimeJavaVMPtr) return NULL;
    jint status = (*runtimeJavaVMPtr)->GetEnv(runtimeJavaVMPtr,
            (void**) &runtimeJNIEnvPtr_GRAPHICS, JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if ((*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr,
                (void**) &runtimeJNIEnvPtr_GRAPHICS, NULL) != JNI_OK) {
            runtimeJNIEnvPtr_GRAPHICS = NULL;
            return NULL;
        }
        graphics_attached = 1;
    } else if (status != JNI_OK) {
        runtimeJNIEnvPtr_GRAPHICS = NULL;
    }
    return runtimeJNIEnvPtr_GRAPHICS;
}

/* Attached Android threads may lack the embedded application's class loader.
 * The returned reference is global and is deleted when the renderer exits. */
jclass awt_find_runtime_class(JNIEnv* env, const char* binary_name, const char* dotted_name) {
    if ((*env)->PushLocalFrame(env, 12) != JNI_OK) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        return NULL;
    }
    jclass found = (*env)->FindClass(env, binary_name);
    if (!found) {
        if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
        jclass loader_class = (*env)->FindClass(env, "java/lang/ClassLoader");
        if (loader_class) {
            jmethodID system_loader = (*env)->GetStaticMethodID(env, loader_class,
                    "getSystemClassLoader", "()Ljava/lang/ClassLoader;");
            jmethodID load_class = NULL;
            if (system_loader) load_class = (*env)->GetMethodID(env, loader_class,
                    "loadClass", "(Ljava/lang/String;)Ljava/lang/Class;");
            if (system_loader && load_class) {
                jobject loader = (*env)->CallStaticObjectMethod(env, loader_class, system_loader);
                if (loader && !(*env)->ExceptionCheck(env)) {
                    jstring name = (*env)->NewStringUTF(env, dotted_name);
                    if (name) found = (jclass) (*env)->CallObjectMethod(env, loader, load_class, name);
                }
            }
        }
    }
    jclass global = NULL;
    if (!(*env)->ExceptionCheck(env) && found) global = (*env)->NewGlobalRef(env, found);
    if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
    (*env)->PopLocalFrame(env, NULL);
    return global;
}

static jintArray awt_screen_pixels(JNIEnv* env) {
    if (!class_CTCScreen) {
        class_CTCScreen = awt_find_runtime_class(env,
                "net/java/openjdk/cacio/ctc/CTCScreen", "net.java.openjdk.cacio.ctc.CTCScreen");
        if (!class_CTCScreen) class_CTCScreen = awt_find_runtime_class(env,
                "com/github/caciocavallosilano/cacio/ctc/CTCScreen",
                "com.github.caciocavallosilano.cacio.ctc.CTCScreen");
        if (!class_CTCScreen) return NULL;
    }
    if (!method_GetRGB) {
        method_GetRGB = (*env)->GetStaticMethodID(env, class_CTCScreen, "getCurrentScreenRGB", "()[I");
        if (!method_GetRGB) {
            if ((*env)->ExceptionCheck(env)) (*env)->ExceptionClear(env);
            return NULL;
        }
    }
    jintArray pixels = (jintArray) (*env)->CallStaticObjectMethod(env, class_CTCScreen, method_GetRGB);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->ExceptionClear(env);
        if (pixels) (*env)->DeleteLocalRef(env, pixels);
        return NULL;
    }
    return pixels;
}

JNIEXPORT jintArray JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_renderAWTScreenFrame(JNIEnv* env, jclass clazz) {
    (void) clazz;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime) return NULL;
    jintArray source = awt_screen_pixels(runtime);
    if (!source) return NULL;
    jsize length = (*runtime)->GetArrayLength(runtime, source);
    jintArray target = (*env)->NewIntArray(env, length);
    if (target) {
        jint* pixels = (*runtime)->GetIntArrayElements(runtime, source, NULL);
        if (pixels) {
            (*env)->SetIntArrayRegion(env, target, 0, length, pixels);
            (*runtime)->ReleaseIntArrayElements(runtime, source, pixels, JNI_ABORT);
        }
    }
    if ((*runtime)->ExceptionCheck(runtime)) (*runtime)->ExceptionClear(runtime);
    (*runtime)->DeleteLocalRef(runtime, source);
    return target;
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_renderAWTScreenFrameInto(
        JNIEnv* env, jclass clazz, jintArray target) {
    (void) clazz;
    if (!target) return JNI_FALSE;
    JNIEnv* runtime = awt_get_graphics_env();
    if (!runtime) return JNI_FALSE;
    jintArray source = awt_screen_pixels(runtime);
    if (!source) return JNI_FALSE;
    jboolean result = JNI_FALSE;
    jsize length = (*runtime)->GetArrayLength(runtime, source);
    if (length > 0 && (*env)->GetArrayLength(env, target) >= length) {
        jint* pixels = (*runtime)->GetIntArrayElements(runtime, source, NULL);
        if (pixels) {
            (*env)->SetIntArrayRegion(env, target, 0, length, pixels);
            (*runtime)->ReleaseIntArrayElements(runtime, source, pixels, JNI_ABORT);
            result = JNI_TRUE;
        }
    }
    (*runtime)->DeleteLocalRef(runtime, source);
    if ((*runtime)->ExceptionCheck(runtime)) { (*runtime)->ExceptionClear(runtime); result = JNI_FALSE; }
    if ((*env)->ExceptionCheck(env)) { (*env)->ExceptionClear(env); result = JNI_FALSE; }
    return result;
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_releaseAWTRenderer(JNIEnv* env, jclass clazz) {
    (void) env;
    (void) clazz;
    JNIEnv* runtime = runtimeJNIEnvPtr_GRAPHICS;
    if (!runtime) return;
    awt_release_direct_frame(runtime);
    if (class_CTCScreen) (*runtime)->DeleteGlobalRef(runtime, class_CTCScreen);
    class_CTCScreen = NULL;
    method_GetRGB = NULL;
    runtimeJNIEnvPtr_GRAPHICS = NULL;
    if (graphics_attached) (*runtimeJavaVMPtr)->DetachCurrentThread(runtimeJavaVMPtr);
    graphics_attached = 0;
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    if(method_SystemClipboardDataReceived == NULL) {
        class_CTCClipboard = (*env)->NewGlobalRef(env, clazz);
        method_SystemClipboardDataReceived = (*env)->GetStaticMethodID(env, clazz, "systemClipboardDataReceived", "(Ljava/lang/String;Ljava/lang/String;)V");
    }
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_QuerySystemClipboard);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }

    const char* dataChars = (*env)->GetStringUTFChars(env, clipboardData, NULL);
    const char* mimeChars = (*env)->GetStringUTFChars(env, clipboardDataMime, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_PutClipboardData,
                                       (*dalvikEnv)->NewStringUTF(dalvikEnv, dataChars),
                                       (*dalvikEnv)->NewStringUTF(dalvikEnv, mimeChars));
    (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nQuerySystemClipboard(JNIEnv *env, jclass clazz) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nQuerySystemClipboard(env, clazz);
}

JNIEXPORT void JNICALL Java_com_github_caciocavallosilano_cacio_ctc_CTCClipboard_nPutClipboardData(JNIEnv* env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    Java_net_java_openjdk_cacio_ctc_CTCClipboard_nPutClipboardData(env, clazz, clipboardData, clipboardDataMime);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openFile(JNIEnv *env, jclass clazz, jstring filePath) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    const char* stringChars = (*env)->GetStringUTFChars(env, filePath, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_OpenPath, (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars));
    (*env)->ReleaseStringUTFChars(env, filePath, stringChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_java_openjdk_cacio_ctc_CTCDesktopPeer_openUri(JNIEnv *env, jclass clazz, jstring uri) {
    JNIEnv *dalvikEnv;char detachable = 0;
    if((*dalvikJavaVMPtr)->GetEnv(dalvikJavaVMPtr, (void **) &dalvikEnv, JNI_VERSION_1_6) == JNI_EDETACHED) {
        (*dalvikJavaVMPtr)->AttachCurrentThread(dalvikJavaVMPtr, &dalvikEnv, NULL);
        detachable = 1;
    }
    const char* stringChars = (*env)->GetStringUTFChars(env, uri, NULL);
    (*dalvikEnv)->CallStaticVoidMethod(dalvikEnv, class_MainActivity, method_OpenLink, (*dalvikEnv)->NewStringUTF(dalvikEnv, stringChars));
    (*env)->ReleaseStringUTFChars(env, uri, stringChars);
    if(detachable) (*dalvikJavaVMPtr)->DetachCurrentThread(dalvikJavaVMPtr);
}

JNIEXPORT void JNICALL Java_net_kdt_pojavlaunch_AWTInputBridge_nativeClipboardReceived(JNIEnv *env, jclass clazz, jstring clipboardData, jstring clipboardDataMime) {
    if(method_SystemClipboardDataReceived == NULL || class_CTCClipboard == NULL) return;
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }
    const char* dataChars = clipboardData != NULL ? (*env)->GetStringUTFChars(env, clipboardData, NULL) : NULL;
    const char* mimeChars = clipboardDataMime != NULL ? (*env)->GetStringUTFChars(env, clipboardDataMime, NULL) : NULL;
    (*runtimeJNIEnvPtr_INPUT)->CallStaticVoidMethod(runtimeJNIEnvPtr_INPUT, class_CTCClipboard, method_SystemClipboardDataReceived,
                                                    clipboardData != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, dataChars) : NULL,
                                                    clipboardDataMime != NULL ? (*runtimeJNIEnvPtr_INPUT)->NewStringUTF(runtimeJNIEnvPtr_INPUT, mimeChars) : NULL);
    if(dataChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardData, dataChars);
    if(mimeChars != NULL) (*env)->ReleaseStringUTFChars(env, clipboardDataMime, mimeChars);
}

JNIEXPORT void JNICALL
Java_net_kdt_pojavlaunch_AWTInputBridge_nativeMoveWindow(JNIEnv *env, jclass clazz, jint xoff, jint yoff) {
    if (runtimeJNIEnvPtr_INPUT == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_INPUT, NULL);
        }
    }
    if(field_y == NULL) {
        class_Frame = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "java/awt/Frame");
        method_GetFrames = (*runtimeJNIEnvPtr_INPUT)->GetStaticMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getFrames", "()[Ljava/awt/Frame;");
        method_GetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "getBounds", "(Ljava/awt/Rectangle;)Ljava/awt/Rectangle;");
        method_SetBounds = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Frame, "setBounds", "(Ljava/awt/Rectangle;)V");
        class_Rectangle = (*runtimeJNIEnvPtr_INPUT)->FindClass(runtimeJNIEnvPtr_INPUT, "java/awt/Rectangle");
        constructor_Rectangle = (*runtimeJNIEnvPtr_INPUT)->GetMethodID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "<init>", "()V");
        field_x = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "x", "I");
        field_y = (*runtimeJNIEnvPtr_INPUT)->GetFieldID(runtimeJNIEnvPtr_INPUT, class_Rectangle, "y", "I");
    }
    jobject rectangle = (*runtimeJNIEnvPtr_INPUT)->NewObject(runtimeJNIEnvPtr_INPUT, class_Rectangle, constructor_Rectangle);
    jobjectArray frames = (*runtimeJNIEnvPtr_INPUT)->CallStaticObjectMethod(runtimeJNIEnvPtr_INPUT, class_Frame, method_GetFrames);
    for(jsize i = 0; i < (*runtimeJNIEnvPtr_INPUT)->GetArrayLength(runtimeJNIEnvPtr_INPUT, frames); i++) {
        jobject frame = (*runtimeJNIEnvPtr_INPUT)->GetObjectArrayElement(runtimeJNIEnvPtr_INPUT, frames, i);
        (*runtimeJNIEnvPtr_INPUT)->CallObjectMethod(runtimeJNIEnvPtr_INPUT, frame, method_GetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_x, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_x) + xoff);
        (*runtimeJNIEnvPtr_INPUT)->SetIntField(runtimeJNIEnvPtr_INPUT, rectangle,  field_y, (*runtimeJNIEnvPtr_INPUT)->GetIntField(runtimeJNIEnvPtr_INPUT, rectangle, field_y) + yoff);
        (*runtimeJNIEnvPtr_INPUT)->CallVoidMethod(runtimeJNIEnvPtr_INPUT, frame, method_SetBounds, rectangle);
        (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frame);
    }
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, rectangle);
    (*runtimeJNIEnvPtr_INPUT)->DeleteLocalRef(runtimeJNIEnvPtr_INPUT, frames);
}

