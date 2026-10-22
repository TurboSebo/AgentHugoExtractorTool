package io.github.turbosebo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RwsExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCreateCsvManifestForDowntownFile() throws IOException {
        File downtown = new File("DOWNTOWN.RWS");
        assumeTrue(downtown.isFile(), "DOWNTOWN.RWS not found");

        RwsExtractor extractor = new RwsExtractor();
        extractor.setExportRootDir(tempDir.toFile());
        extractor.setManifestOnly(true);

        extractor.extract(downtown);

        File csvManifest = tempDir.resolve("export_manifest.csv").toFile();
        assertTrue(csvManifest.isFile(), "Manifest CSV should be created");
        assertTrue(Files.readString(csvManifest.toPath()).contains("recordNumber;logicalName;guid;resourceType"),
                "Manifest should contain CSV header");
    }

    @Test
    void shouldCreateTxtManifestWhenRequested() throws IOException {
        File downtown = new File("DOWNTOWN.RWS");
        assumeTrue(downtown.isFile(), "DOWNTOWN.RWS not found");

        RwsExtractor extractor = new RwsExtractor();
        extractor.setExportRootDir(tempDir.toFile());
        extractor.setManifestOnly(true);
        extractor.setManifestFormat(ExportManifest.Format.TXT);

        extractor.extract(downtown);

        File txtManifest = tempDir.resolve("export_manifest.txt").toFile();
        assertTrue(txtManifest.isFile(), "Manifest TXT should be created");
        assertTrue(Files.readString(txtManifest.toPath()).contains("recordNumber\tlogicalName\tguid\tresourceType"),
                "Manifest should contain TXT header");
    }

    @Test
    void shouldResolveAllRwsFilesFromDirectory() throws IOException {
        Path rwsDir = Files.createDirectory(tempDir.resolve("rws_dir"));
        Path first = Files.createFile(rwsDir.resolve("first.rws"));
        Path second = Files.createFile(rwsDir.resolve("second.rws"));
        Path nestedDir = Files.createDirectory(rwsDir.resolve("nested"));
        Files.createFile(nestedDir.resolve("third.rws"));

        CliArguments.Options options = new CliArguments.Options();
        options.allRws = true;
        options.filePath = rwsDir.toString();

        File[] files = Main.resolveRwsFiles(options);

        assertTrue(files.length == 2, "Only files from the target directory should be returned");
        assertTrue(java.util.Arrays.stream(files).map(File::getName).toList().contains("first.rws"));
        assertTrue(java.util.Arrays.stream(files).map(File::getName).toList().contains("second.rws"));
        assertTrue(java.util.Arrays.stream(files).map(File::getName).toList().stream().noneMatch(name -> name.equals("third.rws")),
                "Nested directory files should not be included");
    }
}
