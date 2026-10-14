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

