package io.github.turbosebo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RwsExtractor {
    private static final int RWS_RESOURCE = 0x716;
    private static final int RWS_OBJECT = 0x704;
    private static final int RWS_CLASS = 0x71C;

    private static final Map<String, String> EXTENSIONS_BY_TYPE = Map.ofEntries(
            Map.entry("rwID_CLUMP", ".dff"),
            Map.entry("rwID_HANIMANIMATION", ".anm"),
            Map.entry("UVA", ".uva"),
            Map.entry("rwID_TEXDICTIONARY", ".txd"),
            Map.entry("wbID_WORLDGEOMETRY", ".dff"),
            Map.entry("wbID_STATICWORLDCOLLISION", ".col"),
            Map.entry("wbID_NAVIGATIONMESH", ".nav"),
            Map.entry("wbID_ENTITYNAMELIST", ".txt"),
            Map.entry("wbID_Texture", ".texref"),
            Map.entry("LVO", ".lvo"),
            Map.entry("iteID_PARTICLEBANK", ".ppb"),
            Map.entry("iteID_RAILPATH", ".rail"),
            Map.entry("rwaID_WAVEDICT", ".rws"),
            Map.entry("rwID_2DMAESTRO", ".anm"),
            Map.entry("LUA", ".lua")
    );

    private static final Map<String, String> DIRECTORIES_BY_TYPE = Map.ofEntries(
            Map.entry("rwID_CLUMP", "models"),
            Map.entry("rwID_HANIMANIMATION", "animations"),
            Map.entry("UVA", "uv_animations"),
            Map.entry("rwID_TEXDICTIONARY", "texture_dictionary"),
            Map.entry("wbID_WORLDGEOMETRY", "world"),
            Map.entry("wbID_STATICWORLDCOLLISION", "collision"),
            Map.entry("wbID_NAVIGATIONMESH", "navigation"),
            Map.entry("wbID_ENTITYNAMELIST", "entity_names"),
            Map.entry("wbID_Texture", "texture_refs"),
            Map.entry("LVO", "light_volume"),
            Map.entry("iteID_PARTICLEBANK", "particles"),
            Map.entry("iteID_RAILPATH", "rail_paths"),
            Map.entry("rwaID_WAVEDICT", "audio"),
            Map.entry("rwID_2DMAESTRO", "2d_maestro"),
            Map.entry("LUA", "scripts")
    );

    public void extract(File inputFile) throws IOException {
        usedPaths.clear();
        try (RandomAccessFile file = new RandomAccessFile(inputFile, "r")) {
            scanRWSFile(file);
        }
    }

    private final Set<String> usedPaths = new HashSet<>();
    private void scanRWSFile(RandomAccessFile file) throws IOException {
        long offset = 0;
        long fileSize = file.length();
        int recordNumber = 0;

        while (offset < fileSize) {
            file.seek(offset);

            int type = readUInt32LE(file);
            long payloadSize = Integer.toUnsignedLong(readUInt32LE(file));
            int libraryId = readUInt32LE(file);

            RwsFileType fileType = detectFileType(type);
            System.out.printf(
                    "Record=%d offset=%d type=0x%X (%s) payloadSize=%d libraryId=%d%n",
                    recordNumber,
                    offset,
                    type,
                    fileType,
                    payloadSize,
                    libraryId
            );

            if (fileType == RwsFileType.RESOURCE) {
                try {
                    handleResourceRecord(file, payloadSize, offset, recordNumber);
                } catch (IOException e) {
                    System.err.println("Failed to handle resource record at offset " + offset + ": " + e.getMessage());
                    // Skip this record and continue with the next one
                }
            }

            long recordEnd = offset + 12L + payloadSize;
            if (recordEnd > fileSize) {
                throw new IOException("Record at offset " + offset + " exceeds file size.");
            }

            file.seek(recordEnd);
            offset = recordEnd;
            recordNumber++;
        }
    }
    private File uniqueFile(File dir, String directoryName, String fileName, int recordNumber) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";

        File candidate = new File(dir, fileName);
        String key = buildPathKey(directoryName, fileName);
        if (usedPaths.add(key)) {
            return candidate;
        }

        String suffixedName = base + String.format("__record_%04d", recordNumber) + ext;
        usedPaths.add(buildPathKey(directoryName, suffixedName));
        return new File(dir, suffixedName);
    }

    private String buildPathKey(String directoryName, String fileName) {
        return (directoryName + "/" + fileName).toLowerCase(Locale.ROOT);
    }
    private void handleResourceRecord(RandomAccessFile file, long payloadSize, long recordOffset, int recordNumber) throws IOException {
        System.out.println("Handling resource record at offset: " + recordOffset);
        readUInt32LE(file);
        String logicalName = readPaddedString(file);

        byte[] guid = new byte[16];
        file.readFully(guid);

        String resourceType = readPaddedString(file);
        String sourcePath = readPaddedString(file);
        String texturePath = readPaddedString(file);
        System.out.println("rType:" + resourceType + "," + "source Path:" + sourcePath + ", " + "texturePath: " + texturePath);

        readUInt32LE(file);
        int dataSize = readUInt32LE(file);

        String basename = extractBasename(logicalName);
        String safeName = sanitizeName(basename);
        String extension = getExtensionForType(safeName, resourceType, sourcePath);
        String directoryName = getDirectoryForType(resourceType);

        File outputDir = new File("extracted_RWS", directoryName);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir);
        }
        File outputFile = uniqueFile(outputDir, directoryName, safeName + extension, recordNumber);
        System.out.println("Path: " + outputFile.toPath());

        byte[] assetData = new byte[dataSize];
        file.readFully(assetData);

        try {
            Files.write(outputFile.toPath(), assetData);
            System.out.println("Extracted: " + outputFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
            throw e;
        }

        int paddingSize = (4 - (dataSize % 4)) % 4;
        file.skipBytes(paddingSize);

        long expectedRecordEnd = recordOffset + 12L + payloadSize;
        if (file.getFilePointer() != expectedRecordEnd) {
            throw new IOException("Parser position mismatch at record start " + recordOffset +
                    ": expected " + expectedRecordEnd + ", actual " + file.getFilePointer());
        }

    }

    private String getDirectoryForType(String resourceType) {
        return DIRECTORIES_BY_TYPE.getOrDefault(resourceType, "other");
    }

    private String getExtensionForType(String logicalName, String resourceType, String sourcePath) {
        if (logicalName.contains(".")) {
            return "";
        }
        if (EXTENSIONS_BY_TYPE.containsKey(resourceType)) {
            return EXTENSIONS_BY_TYPE.get(resourceType);
        }

        int lastDotIndex = sourcePath.lastIndexOf('.');
        if (lastDotIndex != -1) {
            return sourcePath.substring(lastDotIndex);
        }
        return "";
    }

    private int readUInt32LE(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.readFully(bytes);
        return ((int) bytes[0] & 0xFF)
                | (((int) bytes[1] & 0xFF) << 8)
                | (((int) bytes[2] & 0xFF) << 16)
                | (((int) bytes[3] & 0xFF) << 24);
    }

    private String readPaddedString(RandomAccessFile file) throws IOException {
        int paddedSize = readUInt32LE(file);
        if (paddedSize % 4 != 0) {
            throw new IOException("Wrong paddedSize: " + paddedSize);
        }

        byte[] buffer = new byte[paddedSize];
        file.readFully(buffer);

        int length = 0;
        while (length < paddedSize && buffer[length] != 0) {
            length++;
        }
        return new String(buffer, 0, length, StandardCharsets.US_ASCII);
    }

    private String sanitizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "_";
        }

        StringBuilder sanitizedName = new StringBuilder(originalName.length());
        for (int i = 0; i < originalName.length(); i++) {
            char character = originalName.charAt(i);
            if (character < 0x20) {
                sanitizedName.append('_');
                continue;
            }

            switch (character) {
                case '?' -> sanitizedName.append('@');
                case ':' -> sanitizedName.append('-');
                case '*' -> sanitizedName.append('#');
                case '/', '\\' -> sanitizedName.append('.');
                case '<', '>', '"', '|' -> sanitizedName.append('_');
                default -> sanitizedName.append(character);
            }
        }

        int end = sanitizedName.length();
        while (end > 0 && (sanitizedName.charAt(end - 1) == ' ' || sanitizedName.charAt(end - 1) == '.')) {
            end--;
        }

        if (end == 0) {
            return "_";
        }

        sanitizedName.setLength(end);
        return sanitizedName.toString();
    }

    private String extractBasename(String path) {
        String normalized = path.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        return lastSlash == -1 ? normalized : normalized.substring(lastSlash + 1);
    }

    private RwsFileType detectFileType(int type) {
        if (type == RWS_RESOURCE) {
            return RwsFileType.RESOURCE;
        }
        if (type == RWS_OBJECT) {
            return RwsFileType.OBJECT;
        }
        if (type == RWS_CLASS) {
            return RwsFileType.CLASS;
        }
        return RwsFileType.UNKNOWN;
    }

    private enum RwsFileType {
        RESOURCE,
        OBJECT,
        CLASS,
        UNKNOWN
    }
}

