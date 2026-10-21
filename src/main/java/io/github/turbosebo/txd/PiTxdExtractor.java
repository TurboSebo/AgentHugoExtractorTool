package io.github.turbosebo.txd;

import io.github.turbosebo.ExportManifest;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public final class PiTxdExtractor {
    public static final int RW_ID_STRUCT = TxdParser.RW_ID_STRUCT;
    public static final int RW_ID_STRING = TxdParser.RW_ID_STRING;
    public static final int RW_ID_EXTENSION = TxdParser.RW_ID_EXTENSION;
    public static final int RW_ID_TEXTURE = TxdParser.RW_ID_TEXTURE;
    public static final int RW_ID_IMAGE = TxdParser.RW_ID_IMAGE;
    public static final int RW_ID_PI_TEXDICTIONARY = TxdParser.RW_ID_PI_TEXDICTIONARY;

    private PiTxdExtractor() {
    }

    public static List<TxdTypes.TextureRecord> extract(
            byte[] txdData,
            File outputDir,
            boolean allMips,
            boolean manifestOnly,
            ExportManifest.Format manifestFormat
    ) throws IOException {
        List<TxdTypes.TextureRecord> textures = TxdParser.parse(txdData);

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        int writtenPngCount = 0;
        for (TxdTypes.TextureRecord texture : textures) {
            String base = safeFilename(texture.name(), String.format("texture_%03d", texture.index()));
            if (base.toLowerCase(Locale.ROOT).endsWith(".png")) {
                base = base.substring(0, base.length() - 4);
            }

            int mipsToExport = allMips ? texture.images().size() : 1;
            for (int mipIndex = 0; mipIndex < mipsToExport; mipIndex++) {
                TxdTypes.ImageRecord image = texture.images().get(mipIndex);
                String suffix = allMips ? String.format("__mip_%02d", mipIndex) : "";
                String filename = String.format("%03d_%s%s.png", texture.index(), base, suffix);

                if (!manifestOnly) {
                    File pngFile = new File(outputDir, filename);
                    TxdImageDecoder.writePng(txdData, image, pngFile);
                    writtenPngCount++;
                }
            }
        }

        TxdManifestWriter.write(outputDir, textures, manifestFormat);

        System.out.printf(
                "PI texture dictionary: %d textures, %d PNG files written%n",
                textures.size(), writtenPngCount
        );
        return textures;
    }

    private static String safeFilename(String name, String fallback) {
        if (name == null) {
            return fallback;
        }
        String normalized = name.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }

        StringBuilder cleaned = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (ch <= 0x1F || ch == '<' || ch == '>' || ch == ':' || ch == '"'
                    || ch == '/' || ch == '\\' || ch == '|' || ch == '?' || ch == '*') {
                cleaned.append('_');
            } else {
                cleaned.append(ch);
            }
        }

        String result = cleaned.toString().trim();
        while (result.endsWith(".") || result.endsWith(" ")) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? fallback : result;
    }
}
