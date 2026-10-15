package io.github.turbosebo;

import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        final CliArguments.Options options;
        try {
            options = CliArguments.parse(args);
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            CliArguments.printUsage();
            System.exit(-1);
            return;
        }
        if (options.about) {
            CliArguments.printAboutInfo();
            return;
        }
        if (options.help) {
            CliArguments.printUsage();
            return;
        }

        String filePath = options.filePath;
        if (filePath == null || filePath.isBlank()) {
            try (Scanner scanner = new Scanner(System.in)) {
                System.out.print("Please input file relative path: ");
                String input = scanner.nextLine();
                if (input.isEmpty()) {
                    System.out.println("No input provided. Using default: DOWNTOWN.RWS");
                    filePath = "DOWNTOWN.RWS";
                } else {
                    filePath = input;
                }
            }
        }

        filePath = filePath.replace("\\", "/");

        File inputFile = new File(filePath);
        if (!inputFile.exists()) {
            System.err.println("File not found: " + inputFile.getAbsolutePath());
            System.exit(-1);
        }

        try {
            File exportRootDir = options.outputDir != null
                    ? options.outputDir
                    : ExportPathResolver.createDefaultExportDir(filePath);

            RwsExtractor extractor = new RwsExtractor();
            extractor.setExportRootDir(exportRootDir);
            extractor.setManifestFormat(options.manifestFormat);
            extractor.setManifestOnly(options.manifestOnly);
            extractor.extract(inputFile);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(-1);
        }
    }
}
