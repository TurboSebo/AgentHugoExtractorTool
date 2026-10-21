package io.github.turbosebo.txd;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public final class TxdImageDecoder {
    private TxdImageDecoder() {
    }

    public static BufferedImage decodeToBufferedImage(byte[] data, TxdTypes.ImageRecord image) {
        int width = image.width();
        int height = image.height();
        int[] argb = new int[width * height];

        if (image.depth() == 32) {
            for (int y = 0; y < height; y++) {
                int rowStart = image.pixelsOffset() + y * image.stride();
                for (int x = 0; x < width; x++) {
                    int p = rowStart + x * 4;
                    int r = data[p] & 0xFF;
                    int g = data[p + 1] & 0xFF;
                    int b = data[p + 2] & 0xFF;
                    int a = data[p + 3] & 0xFF;
                    argb[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        } else {
            int palOffset = image.paletteOffset();
            int[] palette = new int[256];
            for (int i = 0; i < 256; i++) {
                int p = palOffset + i * 4;
                int r = data[p] & 0xFF;
                int g = data[p + 1] & 0xFF;
                int b = data[p + 2] & 0xFF;
                int a = data[p + 3] & 0xFF;
                palette[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            for (int y = 0; y < height; y++) {
                int rowStart = image.pixelsOffset() + y * image.stride();
                for (int x = 0; x < width; x++) {
                    int idx = data[rowStart + x] & 0xFF;
                    argb[y * width + x] = palette[idx];
                }
            }
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, argb, 0, width);
        return img;
    }

    public static void writePng(byte[] txdData, TxdTypes.ImageRecord image, File pngFile) throws IOException {
        BufferedImage bufferedImage = decodeToBufferedImage(txdData, image);
        if (!ImageIO.write(bufferedImage, "png", pngFile)) {
            throw new IOException("Failed to write PNG: " + pngFile);
        }
    }
}

