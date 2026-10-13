package io.github.turbosebo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class ExportManifest {
    public enum Format {
        CSV("csv"),
        TXT("txt");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String getExtension() {
            return extension;
        }
    }

    public record ExportedResource(
            int recordNumber,
            String logicalName,
            String guid,
            String resourceType,
            String sourcePath,
            String texturePath,
            int metadataSize,
            long dataSize,
            String outputPath
    ) {
    }

    private ExportManifest() {
    }

    public static void write(File outputRoot, List<ExportedResource> records, Format format) throws IOException {
        if (outputRoot == null) {
            throw new IllegalArgumentException("outputRoot cannot be null");
        }
        if (format == null) {
            format = Format.CSV;
        }
        if (!outputRoot.exists() && !outputRoot.mkdirs()) {
            throw new IOException("Failed to create output root: " + outputRoot.getAbsolutePath());
        }

        File manifestFile = new File(outputRoot, "export_manifest." + format.getExtension());
        Files.writeString(manifestFile.toPath(), buildContent(records, format), StandardCharsets.UTF_8);
    }

    private static String buildContent(List<ExportedResource> records, Format format) {
        StringBuilder sb = new StringBuilder();

        if (format == Format.TXT) {
            sb.append("recordNumber\tlogicalName\tguid\tresourceType\tsourcePath\ttexturePath\tmetadataSize\tdataSize\toutputPath\n");
        } else {
            sb.append("recordNumber;logicalName;guid;resourceType;sourcePath;texturePath;metadataSize;dataSize;outputPath\n");
        }

        if (records == null) {
            return sb.toString();
        }

        for (ExportedResource record : records) {
            if (format == Format.TXT) {
                sb.append(record.recordNumber()).append('\t')
                        .append(nullToEmpty(record.logicalName())).append('\t')
                        .append(nullToEmpty(record.guid())).append('\t')
                        .append(nullToEmpty(record.resourceType())).append('\t')
                        .append(nullToEmpty(record.sourcePath())).append('\t')
                        .append(nullToEmpty(record.texturePath())).append('\t')
                        .append(record.metadataSize()).append('\t')
                        .append(record.dataSize()).append('\t')
                        .append(nullToEmpty(record.outputPath())).append('\n');
            } else {
                sb.append(record.recordNumber()).append(';')
                        .append(escapeCsv(record.logicalName())).append(';')
                        .append(escapeCsv(record.guid())).append(';')
                        .append(escapeCsv(record.resourceType())).append(';')
                        .append(escapeCsv(record.sourcePath())).append(';')
                        .append(escapeCsv(record.texturePath())).append(';')
                        .append(record.metadataSize()).append(';')
                        .append(record.dataSize()).append(';')
                        .append(escapeCsv(record.outputPath())).append('\n');
            }
        }

        return sb.toString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }

        String sanitized = value.replace("\r", " ").replace("\n", " ");
        if (sanitized.contains(";") || sanitized.contains(",") || sanitized.contains("\"") || sanitized.contains("\n")) {
            return '"' + sanitized.replace("\"", "\"\"") + '"';
        }
        return sanitized;
    }
}
