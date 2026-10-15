package io.github.turbosebo;

import java.io.File;

public final class ExportPathResolver {
    private ExportPathResolver() {
    }

    public static File createDefaultExportDir(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return new File("extracted_RWS");
        }

        String normalizedPath = filePath.replace("\\", "/");
        int lastSlash = normalizedPath.lastIndexOf('/');
        String name = lastSlash >= 0 ? normalizedPath.substring(lastSlash + 1) : normalizedPath;

        int indexOfDot = name.lastIndexOf('.');
        if (indexOfDot > 0) {
            name = name.substring(0, indexOfDot);
        }

        String safeName = name.trim();
        if (safeName.isEmpty()) {
            safeName = "extracted_RWS";
        }

        return new File(safeName + "_extracted");
    }
}

