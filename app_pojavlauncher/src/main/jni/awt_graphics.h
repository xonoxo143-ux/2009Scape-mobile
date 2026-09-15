#ifndef POJAV_AWT_GRAPHICS_H
#define POJAV_AWT_GRAPHICS_H

#include <jni.h>

JNIEnv* awt_get_graphics_env(void);
jclass awt_find_runtime_class(JNIEnv* env, const char* binary_name, const char* dotted_name);
void awt_release_direct_frame(JNIEnv* env);

#endif
