package io.github.turbosebo;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        String filePath;

        if (args.length == 0) {
            Scanner scanner = new Scanner(System.in);
            System.out.print("Please input file relative path: ");
            String input = scanner.nextLine();
            if (input.isEmpty()) {
                System.out.println("No input provided. Using default: DOWNTOWN.RWS");
                filePath = "DOWNTOWN.RWS";
            } else {
                filePath = input;
            }
            scanner.close();
        } else {
            filePath = args[0];
        }

        filePath = filePath.replace("\\", "/");

        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            System.err.println("File not found: " + inputFile.getAbsolutePath());
            System.exit(-1);
        }

        try {
            new RwsExtractor().extract(inputFile);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
