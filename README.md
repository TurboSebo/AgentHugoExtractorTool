# AgentHugoExtractorTool

A command-line tool for extracting resources from `.rws` files used by the game *Agent Hugo* (developed and published by ITE Media, 2005).

It scans an RWS file, finds the resource records inside it and saves each one as a separate file.

> **Status: alpha.** The tool is under development. Command-line options and the manifest format may still change between versions.

## Requirements

- Java 21 or newer

## Usage

```
java -jar AgentHugoExtractorTool.jar [options] <input.rws>
```

Example:

```
java -jar AgentHugoExtractorTool.jar DOWNTOWN.RWS
```

If no input file is given, the program asks for a path in the console.

### Options

| Option | Description |
|---|---|
| `--manifest-only`, `-m` | Only generate the manifest, without writing extracted files |
| `--manifest-format <csv\|txt>` | Manifest file format (default: `csv`) |
| `--output-dir`, `-o <path>` | Output directory (default: `extracted_RWS`) |
| `--about` | Show version, author and license information |
| `--help`, `-h` | Show help |

## Output

Extracted files are sorted into subdirectories by resource type, for example:

```
extracted_RWS/
├── models/
├── animations/
├── texture_dictionary/
├── collision/
├── scripts/
├── other/
└── export_manifest.csv
```

Resources of types the tool does not recognize go to `other/`. If two resources would get the same file name, the record number is added to the second one (`name__record_0042.ext`).

### Manifest

`export_manifest.csv` (semicolon-separated) or `export_manifest.txt` (tab-separated) lists every exported resource with these columns:

`recordNumber`, `logicalName`, `guid`, `resourceType`, `sourcePath`, `texturePath`, `metadataSize`, `dataSize`, `outputPath`

## Limitations

- Only records of the *resource* type are extracted. Other record types are skipped.
- Extracted files are saved as they are stored in the RWS file. The tool does not convert or unpack them further.
-  For now the tool supports only PC files of the game. The PS2 version is unsupported and untested

## Building

```
mvn clean package
```

The JAR is created at `target/AgentHugoExtractorTool.jar`.

## Disclaimer

This is an unofficial fan project and is not affiliated with the creators or publishers of *Agent Hugo*. The repository does not contain any game files. To use the tool you need your own copy of the game.

## License

Copyright (C) 2026 Turbosebo

This program is free software, distributed under the terms of the GNU General Public License version 3 or (at your option) any later version, without any warranty. See the [LICENSE](LICENSE) file for details.