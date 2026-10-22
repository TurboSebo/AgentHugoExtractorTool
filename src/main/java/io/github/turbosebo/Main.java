package io.github.turbosebo;

import java.io.File;
import java.util.Locale;
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
        File[] rwsFiles = null;
        if (!options.allRws) {
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
            rwsFiles = new File[] { new File(filePath) };
        } else {
            rwsFiles = new File(".").listFiles((dir, name) ->
                    name.toLowerCase(Locale.ROOT).endsWith(".rws"));
            if (rwsFiles == null || rwsFiles.length == 0) {
                System.err.println("No RWS files found in the current directory.");
                System.exit(-1);
            }
        }

        for (File file : rwsFiles) {
            String currentFilePath = file.getPath().replace("\\", "/");
            File inputFile = new File(currentFilePath);
            if (!inputFile.exists()) {
                System.err.println("File not found: " + inputFile.getAbsolutePath());
                continue;
            }

            try {
                File exportRootDir = options.outputDir != null
                        ? options.outputDir
                        : ExportPathResolver.createDefaultExportDir(currentFilePath);

                RwsExtractor extractor = new RwsExtractor();
                extractor.setExportRootDir(exportRootDir);
                extractor.setManifestFormat(options.manifestFormat);
                extractor.setManifestOnly(options.manifestOnly);
                extractor.setExtractAllMips(options.allMips);
                extractor.extract(inputFile);

            } catch (Exception e) {
                System.err.println("Failed to process file: " + currentFilePath);
                System.err.println(e.getMessage());
                continue;
            }
        }
    }
}
