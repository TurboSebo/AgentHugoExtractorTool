package io.github.turbosebo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RwsExtractor {
    private static final int RWS_RESOURCE = 0x716;
    private static final int RWS_OBJECT = 0x704;
    private static final int RWS_CLASS = 0x71C;
    private static final File DEFAULT_EXPORT_ROOT_DIR = new File("extracted_RWS");
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

    private final Set<String> usedPaths = new HashSet<>();
    private final List<ExportManifest.ExportedResource> exportedResources = new ArrayList<>();
    private ExportManifest.Format manifestFormat = ExportManifest.Format.CSV;
    private File exportRootDir = DEFAULT_EXPORT_ROOT_DIR;
    private boolean manifestOnly;
    private boolean extractAllMips;

    public void setManifestFormat(ExportManifest.Format manifestFormat) {
        if (manifestFormat != null) {
            this.manifestFormat = manifestFormat;
        }
    }

    public void setExportRootDir(File exportRootDir) {
        if (exportRootDir != null) {
            this.exportRootDir = exportRootDir;
        }
    }

    public void setManifestOnly(boolean manifestOnly) {
        this.manifestOnly = manifestOnly;
    }

    public void setExtractAllMips(boolean extractAllMips) {
        this.extractAllMips = extractAllMips;
    }

    public void extract(File inputFile) throws IOException {
        usedPaths.clear();
        exportedResources.clear();

        try (RandomAccessFile file = new RandomAccessFile(inputFile, "r")) {
            scanRWSFile(file);
        }

        writeManifest(exportRootDir);
    }

    private void scanRWSFile(RandomAccessFile file) throws IOException {
        long offset = 0;
        long fileSize = file.length();
        int recordNumber = 0;

        while (offset < fileSize) {
            //file.seek(offset); //probably unnecessary

            int type = readUInt32LE(file);
            long payloadSize = Integer.toUnsignedLong(readUInt32LE(file));
            int libraryId = readUInt32LE(file);

            long recordEnd = offset + 12L + payloadSize; // end of record is offset + 12 bytes of header + data
            if (recordEnd > fileSize) {
                // if recordEnd is further than fileSize that probably means file is corrupted
                throw new IOException("Record at offset " + offset + " exceeds file size.");
            }

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
                handleResourceRecord(file, payloadSize, offset, recordNumber);
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
        long expectedRecordEnd = recordOffset + 12L + payloadSize;

        long metadataSizeLong = readUInt32LEAsLong(file);
        if (metadataSizeLong > Integer.MAX_VALUE){
            throw new IOException("Metadata size too large for int at offset " + recordOffset
                    + ": metadataSize=" + metadataSizeLong + " exceeds Integer.MAX_VALUE");
        }
        int metadataSize = (int) metadataSizeLong;
        long metadataStart = file.getFilePointer();
        long expectedMetadataEnd = metadataStart + metadataSize;

        if (expectedMetadataEnd + 4L > expectedRecordEnd)throw new IOException("Metadata exceeds record boundary at offset" + recordOffset );

        String logicalName = readPaddedString(file, expectedRecordEnd);

        byte[] guid = new byte[16];
        if (file.getFilePointer() + guid.length > expectedRecordEnd) {
            throw new IOException("GUID exceeds record boundary at offset " + recordOffset
                    + ": pointer=" + file.getFilePointer() + ", limit=" + expectedRecordEnd);
        }
        file.readFully(guid);

        String resourceType = readPaddedString(file, expectedRecordEnd);
        String sourcePath = readPaddedString(file, expectedRecordEnd);
        String texturePath = readPaddedString(file, expectedRecordEnd);
        System.out.println("rType:" + resourceType + ",source Path:" + sourcePath + ", texturePath: " + texturePath);

        int metadataTail = readUInt32LE(file);

        if (file.getFilePointer() != expectedMetadataEnd) {
            throw new IOException("Metadata tail mismatch at offset " + recordOffset + ": expected pointer "
                    + expectedMetadataEnd + ", but actual pointer is " + file.getFilePointer());
        }

        long dataSize = readUInt32LEAsLong(file);
        long dataStart = file.getFilePointer();
        long dataEnd = dataStart + dataSize;
        long paddingSize = (4L - (dataSize % 4L)) % 4;

        if (dataEnd + paddingSize != expectedRecordEnd) {
            throw new IOException("Record padding/size mismatch at offset " + recordOffset
                    + ": expectedRecordEnd=" + expectedRecordEnd
                    + ", actualAfterPadding=" + (dataEnd + paddingSize)
                    + " (dataSize=" + dataSize + ", paddingSize=" + paddingSize + ")");
        }


        String basename = extractBasename(logicalName);
        String safeName = sanitizeName(basename);
        String extension = getExtensionForType(safeName, resourceType, sourcePath);
        String directoryName = getDirectoryForType(resourceType);

        File resourceOutputDir = new File(exportRootDir, directoryName);
        File outputFile = uniqueFile(resourceOutputDir, directoryName, safeName + extension, recordNumber);
        System.out.println("Path: " + outputFile.toPath());

        if (!manifestOnly) {
            if (dataSize > Integer.MAX_VALUE) {
                throw new IOException("Resource data too large for byte[] at offset " + recordOffset
                        + ": dataSize=" + dataSize + " exceeds Integer.MAX_VALUE");
            }
            if (!resourceOutputDir.exists() && !resourceOutputDir.mkdirs()) {
                throw new IOException("Resource output dir " + resourceOutputDir + " does not exist and cannot be created");
            }
            byte[] assetData = new byte[(int) dataSize];
            file.readFully(assetData);

            if ("rwID_TEXDICTIONARY".equals(resourceType) && assetData.length >= 4) {
                int firstChunkId = (assetData[0] & 0xFF)
                        | ((assetData[1] & 0xFF) << 8)
                        | ((assetData[2] & 0xFF) << 16)
                        | ((assetData[3] & 0xFF) << 24);

                if (firstChunkId == PiTxdExtractor.RW_ID_PI_TEXDICTIONARY) {
                    File texturesPngDir = new File(exportRootDir, "textures_png");
                    PiTxdExtractor.extract(assetData, texturesPngDir, extractAllMips, manifestOnly, manifestFormat);
                }
            }

            Files.write(outputFile.toPath(), assetData);
            System.out.println("Extracted: " + outputFile.getAbsolutePath());
        } else {
            System.out.println("Skipping file extraction because --manifest-only was set.");
        }

        file.seek(expectedRecordEnd);
        exportedResources.add(new ExportManifest.ExportedResource(
                recordNumber,
                logicalName,
                toGuidString(guid),
                resourceType,
                sourcePath,
                texturePath,
                metadataSize,
                dataSize,
                exportRootDir.toPath().relativize(outputFile.toPath()).toString()
        ));
    }

    private void writeManifest(File manifestDir) throws IOException {
        if (!manifestDir.exists() && !manifestDir.mkdirs()) {
            throw new IOException("Failed to create manifest directory: " + manifestDir.getAbsolutePath());
        }

        ExportManifest.write(manifestDir, exportedResources, manifestFormat);
        System.out.println("Manifest written to: " + new File(manifestDir, "export_manifest." + manifestFormat.getExtension()).getAbsolutePath());
    }

    private String toGuidString(byte[] guid) {
        if (guid == null || guid.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder(guid.length * 2);
        for (byte b : guid) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
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

    private long readUInt32LEAsLong(RandomAccessFile file) throws IOException {
        return Integer.toUnsignedLong(readUInt32LE(file));
    }

    private String readPaddedString(RandomAccessFile file, long limit) throws IOException {
        long paddedSize = readUInt32LEAsLong(file);
        if (paddedSize % 4 != 0) {
            throw new IOException("Wrong paddedSize: " + paddedSize);
        }
        if (paddedSize > Integer.MAX_VALUE) {
            throw new IOException("Padded string too large: " + paddedSize + " exceeds Integer.MAX_VALUE");
        }

        long currentPosition = file.getFilePointer();
        long requiredEnd = currentPosition + paddedSize;
        if (requiredEnd > limit) {
            throw new IOException("String exceeds record limit: paddedSize=" + paddedSize
                    + ", currentPosition=" + currentPosition + ", limit=" + limit);
        }

        byte[] buffer = new byte[(int) paddedSize];
        file.readFully(buffer);

        int length = -1;
        for (int i = 0; i < buffer.length; i++) {
            if (buffer[i] == 0) {
                length = i;
                break;
            }
        }
        if (length < 0) {
            throw new IOException("Missing NUL terminator in string at offset " + currentPosition);
        }
        return new String(buffer, 0, length, StandardCharsets.ISO_8859_1);
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
