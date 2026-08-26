package plugin.api;

import rt4.GlAlphaSprite;
import rt4.SoftwareAlphaSprite;
import rt4.Sprite;

import java.awt.image.BufferedImage;
import java.awt.image.DirectColorModel;
import java.awt.image.PixelGrabber;
import java.util.ArrayList;
import java.util.List;

public class SpritePNGLoader {

    /**
     * Converts the buffered image into a sprite image and returns it
     *
     * @param image The image to be converted
     * @return The buffered image as a sprite image
     */
    public static SpritePixels getImageSpritePixels(BufferedImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];

        try {
            PixelGrabber g = new PixelGrabber(image, 0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
            g.setColorModel(new DirectColorModel(32, 0xff0000, 0xff00, 0xff, 0xff000000));
            g.grabPixels();

            // Make any fully transparent pixels fully black, because the sprite draw routines
            // check for == 0, not actual transparency
            for (int i = 0; i < pixels.length; i++) {
                if ((pixels[i] & 0xFF000000) == 0) {
                    pixels[i] = 0;
                }
            }
        } catch (InterruptedException ex) {
            System.err.println("PixelGrabber was interrupted: " + ex);
        }

        return new SpritePixels(pixels, image.getWidth(), image.getHeight());
    }

    /**
     * Converts an image into an {@code IndexedSprite} instance.
     * <p>
     * The passed in image can only have at max 255 different colors.
     *
     * @param image  The image to be converted
     * @return The image as an {@code IndexedSprite}
     */
    public static Sprite getImageIndexedSprite(BufferedImage image) {
        final byte[] pixels = new byte[image.getWidth() * image.getHeight()];
        final List<Integer> palette = new ArrayList<>();
        /*
            When drawing the indexed sprite, palette idx 0 is seen as fully transparent,
            so pad the palette out so that our colors start at idx 1.
         */
        palette.add(0);

        final int[] sourcePixels = image.getRGB(0, 0,
                image.getWidth(), image.getHeight(),
                null, 0, image.getWidth());

        /*
            Build a color palette and assign the pixels to positions in the palette.
         */
        for (int j = 0; j < sourcePixels.length; j++) {
            final int argb = sourcePixels[j];
            final int a = (argb >> 24) & 0xFF;
            final int rgb = argb & 0xFF_FF_FF;

            // Default to not drawing the pixel.
            int paletteIdx = 0;

            // If the pixel is fully opaque, draw it.
            if (a == 0xFF) {
                paletteIdx = palette.indexOf(rgb);

                if (paletteIdx == -1) {
                    paletteIdx = palette.size();
                    palette.add(rgb);
                }
            }

            pixels[j] = (byte) paletteIdx;
        }

        if (palette.size() > 256) {
            throw new RuntimeException("Passed in image had " + (palette.size() - 1)
                    + " different colors, exceeding the max of 255.");
        }

        //int[] imgPalette = palette.stream().mapToInt(i -> i).toArray();

        Sprite sprites;
        //IndexedSprite sprite;
        if (API.IsHD()) { //width, height, xOffsets[local16], yOffsets[local16], innerWidths[local16], innerHeights[local16], local38
            sprites = new GlAlphaSprite(image.getWidth(), image.getHeight(), 0, 0, image.getWidth(), image.getHeight(), sourcePixels);
            //    sprite = new GlIndexedSprite(image.getWidth(), image.getHeight(), 0, 0, image.getWidth(), image.getHeight(), pixels, imgPalette);
        } else {
            sprites = new SoftwareAlphaSprite(image.getWidth(), image.getHeight(), 0, 0, image.getWidth(), image.getHeight(), sourcePixels);
            //    sprite = new SoftwareIndexedSprite(image.getWidth(), image.getHeight(), 0, 0, image.getWidth(), image.getHeight(), pixels, imgPalette);
        }
        return sprites;
    }
}