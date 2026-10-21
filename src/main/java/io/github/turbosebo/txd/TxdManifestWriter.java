package io.github.turbosebo.txd;

import io.github.turbosebo.ExportManifest;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class TxdManifestWriter {
    private TxdManifestWriter() {
    }

    public static void write(File outputDir, List<TxdTypes.TextureRecord> textures, ExportManifest.Format format)
            throws IOException {
        char sep = (format == ExportManifest.Format.TXT) ? '\t' : ';';
        StringBuilder sb = new StringBuilder();
        sb.append("index").append(sep)
                .append("name").append(sep)
                .append("maskName").append(sep)
                .append("width").append(sep)
                .append("height").append(sep)
                .append("depthBits").append(sep)
                .append("mipCount").append(sep)
                .append("filterAndAddressHex").append(sep)
                .append("filterMode").append(sep)
                .append("addressU").append(sep)
                .append("addressV").append(sep)
                .append("userMipmapsFlag\n");

        for (TxdTypes.TextureRecord t : textures) {
            TxdTypes.ImageRecord top = t.images().get(0);
            sb.append(t.index()).append(sep)
                    .append(t.name()).append(sep)
                    .append(t.maskName()).append(sep)
                    .append(top.width()).append(sep)
                    .append(top.height()).append(sep)
                    .append(top.depth()).append(sep)
                    .append(t.images().size()).append(sep)
                    .append(String.format("0x%08X", t.filterAndAddress())).append(sep)
                    .append(t.filterModeName()).append(sep)
                    .append(t.addressUName()).append(sep)
                    .append(t.addressVName()).append(sep)
                    .append(t.userMipmapsFlag()).append('\n');
        }

        File manifestFile = new File(outputDir, "textures_manifest." + format.getExtension());
        Files.writeString(manifestFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }
}

