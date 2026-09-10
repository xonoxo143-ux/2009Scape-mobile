#include <jni.h>
#include <assert.h>
#include <string.h>
#include <stdio.h>

static JavaVM* dalvikJavaVMPtr;

static JavaVM* runtimeJavaVMPtr;
static JNIEnv* runtimeJNIEnvPtr_GRAPHICS;
static JNIEnv* runtimeJNIEnvPtr_INPUT;
jclass class_CTCScreen;
jmethodID method_GetRGB;

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

// TODO: check for memory leaks
// int printed = 0;
int threadAttached = 0;
JNIEXPORT jintArray JNICALL Java_net_kdt_pojavlaunch_utils_JREUtils_renderAWTScreenFrame(JNIEnv* env, jclass clazz /*, jobject canvas, jint width, jint height */) {
    if (runtimeJNIEnvPtr_GRAPHICS == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return NULL;
        } else {
            (*runtimeJavaVMPtr)->AttachCurrentThread(runtimeJavaVMPtr, &runtimeJNIEnvPtr_GRAPHICS, NULL);
        }
    }

    int *rgbArray;
    jintArray jreRgbArray, androidRgbArray;
  
    if (method_GetRGB == NULL) {
        class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(runtimeJNIEnvPtr_GRAPHICS, "net/java/openjdk/cacio/ctc/CTCScreen");
        if ((*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionClear(runtimeJNIEnvPtr_GRAPHICS);
            class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(runtimeJNIEnvPtr_GRAPHICS, "com/github/caciocavallosilano/cacio/ctc/CTCScreen");
        }
        assert(class_CTCScreen != NULL);
        method_GetRGB = (*runtimeJNIEnvPtr_GRAPHICS)->GetStaticMethodID(runtimeJNIEnvPtr_GRAPHICS, class_CTCScreen, "getCurrentScreenRGB", "()[I");
        assert(method_GetRGB != NULL);
    }
    jreRgbArray = (jintArray) (*runtimeJNIEnvPtr_GRAPHICS)->CallStaticObjectMethod(
        runtimeJNIEnvPtr_GRAPHICS,
        class_CTCScreen,
        method_GetRGB
    );
    if (jreRgbArray == NULL) {
        return NULL;
    }
    
    // Copy JRE RGB array memory to Android.
    int arrayLength = (*runtimeJNIEnvPtr_GRAPHICS)->GetArrayLength(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
    rgbArray = (*runtimeJNIEnvPtr_GRAPHICS)->GetIntArrayElements(runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, 0);
    androidRgbArray = (*env)->NewIntArray(env, arrayLength);
    (*env)->SetIntArrayRegion(env, androidRgbArray, 0, arrayLength, rgbArray);

    (*runtimeJNIEnvPtr_GRAPHICS)->ReleaseIntArrayElements(
        runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, rgbArray, JNI_ABORT);
    (*runtimeJNIEnvPtr_GRAPHICS)->DeleteLocalRef(
        runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);

    return androidRgbArray;
}

JNIEXPORT jboolean JNICALL
Java_net_kdt_pojavlaunch_utils_JREUtils_renderAWTScreenFrameInto(
        JNIEnv* env, jclass clazz, jintArray androidRgbArray) {
    if (androidRgbArray == NULL) {
        return JNI_FALSE;
    }

    if (runtimeJNIEnvPtr_GRAPHICS == NULL) {
        if (runtimeJavaVMPtr == NULL) {
            return JNI_FALSE;
        }
        (*runtimeJavaVMPtr)->AttachCurrentThread(
            runtimeJavaVMPtr, &runtimeJNIEnvPtr_GRAPHICS, NULL);
    }

    if (method_GetRGB == NULL) {
        class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(
            runtimeJNIEnvPtr_GRAPHICS, "net/java/openjdk/cacio/ctc/CTCScreen");
        if ((*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionClear(runtimeJNIEnvPtr_GRAPHICS);
            class_CTCScreen = (*runtimeJNIEnvPtr_GRAPHICS)->FindClass(
                runtimeJNIEnvPtr_GRAPHICS,
                "com/github/caciocavallosilano/cacio/ctc/CTCScreen");
        }
        if (class_CTCScreen == NULL) {
            if ((*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS) == JNI_TRUE) {
                (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionClear(runtimeJNIEnvPtr_GRAPHICS);
            }
            return JNI_FALSE;
        }
        method_GetRGB = (*runtimeJNIEnvPtr_GRAPHICS)->GetStaticMethodID(
            runtimeJNIEnvPtr_GRAPHICS,
            class_CTCScreen,
            "getCurrentScreenRGB",
            "()[I");
        if (method_GetRGB == NULL) {
            if ((*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS) == JNI_TRUE) {
                (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionClear(runtimeJNIEnvPtr_GRAPHICS);
            }
            return JNI_FALSE;
        }
    }

    jintArray jreRgbArray = (jintArray) (*runtimeJNIEnvPtr_GRAPHICS)->CallStaticObjectMethod(
        runtimeJNIEnvPtr_GRAPHICS,
        class_CTCScreen,
        method_GetRGB);
    if (jreRgbArray == NULL) {
        if ((*runtimeJNIEnvPtr_GRAPHICS)->ExceptionCheck(runtimeJNIEnvPtr_GRAPHICS) == JNI_TRUE) {
            (*runtimeJNIEnvPtr_GRAPHICS)->ExceptionClear(runtimeJNIEnvPtr_GRAPHICS);
        }
        return JNI_FALSE;
    }

    jsize sourceLength = (*runtimeJNIEnvPtr_GRAPHICS)->GetArrayLength(
        runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
    jsize destinationLength = (*env)->GetArrayLength(env, androidRgbArray);
    if (sourceLength <= 0 || destinationLength < sourceLength) {
        (*runtimeJNIEnvPtr_GRAPHICS)->DeleteLocalRef(
            runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
        return JNI_FALSE;
    }

    jint* sourcePixels = (*runtimeJNIEnvPtr_GRAPHICS)->GetIntArrayElements(
        runtimeJNIEnvPtr_GRAPHICS, jreRgbArray, NULL);
    if (sourcePixels == NULL) {
        (*runtimeJNIEnvPtr_GRAPHICS)->DeleteLocalRef(
            runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);
        return JNI_FALSE;
    }

    (*env)->SetIntArrayRegion(
        env,
        androidRgbArray,
        0,
        sourceLength,
        sourcePixels);
    (*runtimeJNIEnvPtr_GRAPHICS)->ReleaseIntArrayElements(
        runtimeJNIEnvPtr_GRAPHICS,
        jreRgbArray,
        sourcePixels,
        JNI_ABORT);
    (*runtimeJNIEnvPtr_GRAPHICS)->DeleteLocalRef(
        runtimeJNIEnvPtr_GRAPHICS, jreRgbArray);

    if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
        (*env)->ExceptionClear(env);
        return JNI_FALSE;
    }
    return JNI_TRUE;
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
