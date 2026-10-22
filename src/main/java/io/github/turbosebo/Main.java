package io.github.turbosebo;

import java.io.File;
import java.util.Arrays;
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
        File[] rwsFiles = resolveRwsFiles(options);
        int totalFiles = rwsFiles.length;
        if (options.outputDir != null && totalFiles > 1) {
            System.out.println("Multiple RWS files detected. Writing each file into its own subdirectory under: " + options.outputDir.getAbsolutePath());
        }

        for (File file : rwsFiles) {
            String currentFilePath = file.getPath().replace("\\", "/");
            File inputFile = new File(currentFilePath);
            if (!inputFile.exists()) {
                System.err.println("File not found: " + inputFile.getAbsolutePath());
                continue;
            }

            try {
                File exportRootDir = resolveExportRootDir(options, currentFilePath, totalFiles);

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

    static File resolveExportRootDir(CliArguments.Options options, String currentFilePath, int totalFiles) {
        if (options.outputDir == null) {
            return ExportPathResolver.createDefaultExportDir(currentFilePath);
        }
        if (totalFiles <= 1) {
            return options.outputDir;
        }

        String fileName = new File(currentFilePath).getName();
        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        if (baseName == null || baseName.isBlank()) {
            baseName = "extracted";
        }

        return new File(options.outputDir, baseName);
    }

    static File[] resolveRwsFiles(CliArguments.Options options) {
        String filePath = options.filePath;
        if (options.allRws || (filePath != null && new File(filePath).isDirectory())) {
            File baseDir = filePath != null ? new File(filePath) : new File(".");
            File[] files = baseDir.listFiles((dir, name) ->
                    name.toLowerCase(Locale.ROOT).endsWith(".rws") && new File(dir, name).isFile());
            if (files == null || files.length == 0) {
                System.err.println("No RWS files found in directory: " + baseDir.getAbsolutePath());
                System.exit(-1);
            }
            Arrays.sort(files);
            return files;
        }

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

        return new File[] { new File(filePath) };
    }
}
