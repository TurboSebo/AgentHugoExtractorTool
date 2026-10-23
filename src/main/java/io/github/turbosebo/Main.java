package io.github.turbosebo;

import java.io.File;

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

        File[] rwsFiles = RwsFileResolver.resolveRwsFiles(options);
        int totalFiles = rwsFiles.length;
        if (options.outputDir != null && totalFiles > 1) {
            System.out.println("Multiple RWS files detected. Writing each file into its own subdirectory under: "
                    + options.outputDir.getAbsolutePath());
        }

        for (File file : rwsFiles) {
            String currentFilePath = file.getPath().replace("\\", "/");
            File inputFile = new File(currentFilePath);
            if (!inputFile.exists()) {
                System.err.println("File not found: " + inputFile.getAbsolutePath());
                continue;
            }

            try {
                File exportRootDir = OutputDirectoryResolver.resolveExportRootDir(options, currentFilePath, totalFiles);

                RwsExtractor extractor = new RwsExtractor();
                extractor.setExportRootDir(exportRootDir);
                extractor.setManifestFormat(options.manifestFormat);
                extractor.setManifestOnly(options.manifestOnly);
                extractor.setExtractAllMips(options.allMips);
                extractor.extract(inputFile);

            } catch (Exception e) {
                System.err.println("Failed to process file: " + currentFilePath);
                System.err.println(e.getMessage());
            }
        }
    }

    static File[] resolveRwsFiles(CliArguments.Options options) {
        return RwsFileResolver.resolveRwsFiles(options);
    }

    static File resolveExportRootDir(CliArguments.Options options, String currentFilePath, int totalFiles) {
        return OutputDirectoryResolver.resolveExportRootDir(options, currentFilePath, totalFiles);
    }
}
