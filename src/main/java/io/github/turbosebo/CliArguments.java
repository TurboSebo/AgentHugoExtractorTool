package io.github.turbosebo;

import java.io.File;
import java.util.Locale;

public final class CliArguments {
    public static final class Options {
        public boolean help;
        public boolean manifestOnly;
        public ExportManifest.Format manifestFormat = ExportManifest.Format.CSV;
        public File outputDir = new File("extracted_RWS");
        public String filePath;
        public boolean about;
    }

    private CliArguments() {
    }

    public static Options parse(String[] args) {
        Options options = new Options();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--help", "-h" -> options.help = true;
                case "--manifest-only", "-m" -> options.manifestOnly = true;
                case "--about", "-a" -> options.about = true;
                case "--manifest-format" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --manifest-format");
                    }
                    options.manifestFormat = parseManifestFormat(args[++i]);
                }
                case "--output-dir", "-o" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("Missing value for --output-dir");
                    }
                    options.outputDir = new File(args[++i]);
                }
                case "--" -> {
                    for (int j = i + 1; j < args.length; j++) {
                        if (options.filePath == null) {
                            options.filePath = args[j];
                        } else {
                            throw new IllegalArgumentException("Unexpected extra argument: " + args[j]);
                        }
                    }
                    return options;
                }
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("Unknown argument: " + arg);
                    }
                    if (options.filePath != null) {
                        throw new IllegalArgumentException("Unexpected extra argument: " + arg);
                    }
                    options.filePath = arg;
                }
            }
        }

        return options;
    }

    public static ExportManifest.Format parseManifestFormat(String value) {
        try {
            return ExportManifest.Format.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported manifest format: " + value + ". Allowed values: csv, txt");
        }
    }
    public static void printAboutInfo() {
        System.out.println("Agent Hugo Extractor Tool");
        System.out.println("Version: v0.1.0-alpha.1");
        System.out.println("Author: Turbosebo");
        System.out.println("Webpage: https://github.com/turbosebo/AgentHugoExtractorTool");
        System.out.println();
        System.out.print("""
            GNU General Public License v3
            Copyright (C) 2026 Turbosebo

            This program is free software: you can redistribute it and/or modify
            it under the terms of the GNU General Public License as published by
            the Free Software Foundation, either version 3 of the License, or
            (at your option) any later version.

            This program is distributed in the hope that it will be useful,
            but WITHOUT ANY WARRANTY; without even the implied warranty of
            MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
            GNU General Public License for more details.

            You should have received a copy of the GNU General Public License
            along with this program. If not, see <https://www.gnu.org/licenses/>.
            """);
    }

    public static void printUsage() {
        System.out.println("Usage: java -jar AgentHugoExtractorTool.jar [options] <input.rws>");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --manifest-only, -m        Only generate a manifest without writing extracted files");
        System.out.println("  --manifest-format <csv|txt> Set the manifest file format");
        System.out.println("  --output-dir, -o <path>    Set the extraction output directory");
        System.out.println("  --help, -h                 Show this help");
    }
}

