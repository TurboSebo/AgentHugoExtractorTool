package io.github.turbosebo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Scanner;
import java.util.Map;


public class Main {
    private static final int RWS_RESOURCE = 0x716;
    private static final int RWS_OBJECT = 0x704;
    private static final int RWS_CLASS = 0x71C;

    enum RWSFileType {
        RESOURCE,
        OBJECT,
        CLASS,
        UNKNOWN
    }
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

    private static String getDirectoryForType(String resourceType){
        return DIRECTORIES_BY_TYPE.getOrDefault(resourceType, "other");

    }
    private static String getExtensionForType(String logicalName, String resourceType, String sourcePath){
        if (logicalName.contains(".")){ //if name already has extension do not add
            return "";
        }
        if (EXTENSIONS_BY_TYPE.containsKey(resourceType)){
            return EXTENSIONS_BY_TYPE.get(resourceType);
        }

        int lastDotIndex = sourcePath.lastIndexOf('.');
        if (lastDotIndex != -1) return sourcePath.substring(lastDotIndex);
        return "";
    }

    private static int readUInt32LE(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.readFully(bytes);

        System.out.println("Read bytes: " + String.format("%02X %02X %02X %02X", bytes[0], bytes[1], bytes[2], bytes[3]));
        return ((int) bytes [0] & 0xFF) |(((int) bytes[1] & 0xFF) << 8) | (((int) bytes[2] & 0xFF) << 16) | (((int) bytes[3] & 0xFF) << 24);
    }

    private static String sanitizeName(String originalName) {
        if (originalName == null || originalName.isBlank()) return "_";

        StringBuilder sanitizedName = new StringBuilder(originalName.length());
        for (int i = 0; i < originalName.length(); i++) {
            char character = originalName.charAt(i);
            boolean controlCharacter = character < 0x20;

            if (controlCharacter) {
                sanitizedName.append('_');
            } else {
                switch (character) {
                    case '?' -> sanitizedName.append('@');
                    case ':' -> sanitizedName.append('-');
                    case '*' -> sanitizedName.append('#');
                    case '/','\\' -> sanitizedName.append('.');
                    case '<', '>', '"', '|' -> sanitizedName.append('_');
                    default -> sanitizedName.append(character);
                }
            }
        }
        int end = sanitizedName.length();
        while (end > 0 && (sanitizedName.charAt(end - 1) == ' '
                || sanitizedName.charAt(end - 1) == '.')) {
            end--;
        }

        if (end == 0) {
            return "_";
        }

        sanitizedName.setLength(end);
        return sanitizedName.toString();
    }

    private static RWSFileType detectFileType(int type) {
        if (type == RWS_RESOURCE) {
            return RWSFileType.RESOURCE;
        }
        if (type == RWS_OBJECT) {
            return RWSFileType.OBJECT;
        }
        if (type == RWS_CLASS) {
            return RWSFileType.CLASS;
        }
        return RWSFileType.UNKNOWN;
    }


    private static void scanRWSFile(RandomAccessFile file) throws IOException { //checking if 4 bytes is a header
        long offset = 0;
        long fileSize = file.length();
        int recordNumber = 0;
        while (offset < fileSize) {
            file.seek(offset);

            int type = readUInt32LE(file);
            int payloadSize = readUInt32LE(file);
            int libraryId = readUInt32LE(file);

            RWSFileType fileType = detectFileType(type);
            System.out.printf(
                    "Record=%d offset=%d type=0x%X (%s) payloadSize=%d libraryId=%d%n",
                    recordNumber++,
                    offset,
                    type,
                    fileType,
                    payloadSize,
                    libraryId
            );

            offset = offset + 12L + payloadSize;

            if (payloadSize < 0) {
                throw new IOException("Invalid payload size at offset " + offset);
            }
        }
    }

    public static void main(String[] args) {

        String filePath;

        if (args.length == 0) {
            Scanner scanner = new Scanner(System.in);
            //System.out.print("Enter the file path: ");
            //filePath = scanner.nextLine();
            filePath = "DOWNTOWN.RWS";
        } else {
            filePath = args[0];
        }

        filePath = filePath.replace("\\", "/");

        File file = new File(filePath); //dev purpose

        if (!file.exists()) {
            System.err.println("File not found: " + file.getAbsolutePath());
            System.exit(-1);
        }

            try (RandomAccessFile fileAccess = new RandomAccessFile(file, "r")) {
                scanRWSFile(fileAccess);

            }
            catch (IOException e) {
                System.err.println(e.getMessage());
                System.exit(-1);
            }

    }
}
