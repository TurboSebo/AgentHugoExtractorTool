package io.github.turbosebo;

import java.io.File;
import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        String filePath = args.length > 0 ? args[0] : "DOWNTOWN.RWS";
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
