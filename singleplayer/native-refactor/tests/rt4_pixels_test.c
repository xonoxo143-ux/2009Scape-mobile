#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "../../../app_pojavlauncher/src/main/jni/rt4_pixels.h"

int main(void) {
    uint32_t pixels[] = {0x00ff0000, 0x000000ff, 0xaa000000, 0x1200ff00};
    uint8_t guarded[24];
    memset(guarded, 0xcd, sizeof(guarded));
    assert(rt4_copy_rgba(guarded, 12, pixels, 2, 2));
    const uint8_t row0[] = {255, 0, 0, 255, 0, 0, 255, 255};
    const uint8_t row1[] = {0, 0, 0, 255, 0, 255, 0, 255};
    assert(memcmp(guarded, row0, 8) == 0);
    assert(memcmp(guarded + 12, row1, 8) == 0);
    for (int i = 8; i < 12; ++i) assert(guarded[i] == 0xcd && guarded[i + 12] == 0xcd);
    assert(!rt4_copy_rgba(guarded, 7, pixels, 2, 2));
    assert(!rt4_copy_rgba(guarded, 12, pixels, 0, 2));
    assert(!rt4_copy_rgba(guarded, 12, pixels, SIZE_MAX, 2));
    assert(!rt4_copy_rgba(guarded, 12, pixels, 2, SIZE_MAX));
    assert(!rt4_copy_rgba(NULL, 12, pixels, 2, 2));
    puts("RT4_PIXEL_REGRESSION: PASS (RGBA, opacity, stride, bounds)");
    return 0;
}
