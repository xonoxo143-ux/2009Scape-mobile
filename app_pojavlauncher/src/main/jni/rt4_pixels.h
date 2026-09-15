#ifndef POJAV_RT4_PIXELS_H
#define POJAV_RT4_PIXELS_H

#include <stddef.h>
#include <stdint.h>

/* RT4 is opaque 0x00RRGGBB; an Android RGBA_8888 bitmap stores R,G,B,A bytes.
 * Write bytes explicitly so neither native endian nor RT4's spare alpha bits
 * can swap red/blue or turn a valid black pixel transparent. */
static inline int rt4_copy_rgba(uint8_t* target, size_t stride,
        const uint32_t* source, size_t width, size_t height) {
    if (!target || !source || !width || !height || width > SIZE_MAX / 4
            || stride < width * 4 || height > SIZE_MAX / stride
            || height > SIZE_MAX / width / sizeof(uint32_t)) return 0;
    for (size_t y = 0; y < height; ++y) {
        uint8_t* row = target + y * stride;
        const uint32_t* input = source + y * width;
        for (size_t x = 0; x < width; ++x) {
            uint32_t rgb = input[x];
            row[x * 4] = (uint8_t) (rgb >> 16);
            row[x * 4 + 1] = (uint8_t) (rgb >> 8);
            row[x * 4 + 2] = (uint8_t) rgb;
            row[x * 4 + 3] = 255;
        }
    }
    return 1;
}

#endif
