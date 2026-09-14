package io.github.turbosebo;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        String filePath;

        if (args.length == 0) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter the file path: ");
            filePath = scanner.nextLine();
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

                long offset = 0;
                long fileSize = file.length();
                int recordNumber = 0;

            }
            catch (IOException e) {
                e.printStackTrace();
                System.err.println(e.getMessage());
                System.exit(-1);
            }

    }
}
