package io.github.turbosebo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Scanner;



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
    private static int readUInt32LE(RandomAccessFile file) throws IOException {
        byte[] bytes = new byte[4];
        file.readFully(bytes);

        System.out.println("Read bytes: " + String.format("%02X %02X %02X %02X", bytes[0], bytes[1], bytes[2], bytes[3]));
        return ((int) bytes [0] & 0xFF) |(((int) bytes[1] & 0xFF) << 8) | (((int) bytes[2] & 0xFF) << 16) | (((int) bytes[3] & 0xFF) << 24);
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
