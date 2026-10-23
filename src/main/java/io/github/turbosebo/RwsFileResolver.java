package io.github.turbosebo;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;

public final class RwsFileResolver {
    private RwsFileResolver() {
    }

    public static File[] resolveRwsFiles(CliArguments.Options options) {
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

