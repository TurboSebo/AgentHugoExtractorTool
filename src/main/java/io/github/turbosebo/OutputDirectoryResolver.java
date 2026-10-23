package io.github.turbosebo;

import java.io.File;

public final class OutputDirectoryResolver {
    private OutputDirectoryResolver() {
    }

    public static File resolveExportRootDir(CliArguments.Options options, String currentFilePath, int totalFiles) {
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
}

