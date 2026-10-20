package io.github.turbosebo;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PiTxdExtractor {

    public static final int RW_ID_STRUCT = 0x01;
    public static final int RW_ID_STRING = 0x02;
    public static final int RW_ID_EXTENSION = 0x03;
    public static final int RW_ID_TEXTURE = 0x06;
    public static final int RW_ID_IMAGE = 0x18;
    public static final int RW_ID_PI_TEXDICTIONARY = 0x23;

    private static final Map<Integer, String> FILTER_NAMES = Map.of (
            0, "NA",
            1, "NEAREST",
            2, "LINEAR",
            3, "MIP_NEAREST",
            4, "MIP_LINEAR",
            5, "LINEAR_MIP_NEAREST",
            6, "LINEAR_MIP_LINEAR"
    );
    private static final Map<Integer, String> ADDRESS_NAMES = Map.of (
            0, "NA",
            1, "WRAP",
            2, "MIRROR",
            3, "CLAMP",
            4, "BORDER"
    );

    public record Chunk(int offset, int chunkId, int size, int libraryId, int payloadOffset, int end){
    }


    public record ImageRecord(
            Chunk chunk,
            int width,
            int height,
            int depth,
            int stride,
            int pixelsOffset,
            int pixelsSize,
            Integer paletteOffset,
            int paletteSize
    ){}

    public record TextureRecord(
            int index,
            String name,
            String maskName,
            int filterAndAddress,
            List<ImageRecord> images,
            Chunk textureChunk,
            int extensionSize

    ){
        public String filterModeName() {
            return FILTER_NAMES.getOrDefault(filterAndAddress & 0xFF, "UNKNOWN");
        }
        public String addressUName() {
            return ADDRESS_NAMES.getOrDefault((filterAndAddress >>> 8) & 0x0F, "UNKNOWN");
        }
        public String addressVName() {
            return ADDRESS_NAMES.getOrDefault((filterAndAddress >>> 12) & 0x0F, "UNKNOWN");
        }

        public int streamFlags(){
         return (filterAndAddress >> 16) & 0xFF;
        }

        public boolean userMipmapsFlag(){
            return (streamFlags() & 1) != 0;
        }
    }

    private PiTxdExtractor() {}

    // PI TXD is the container: Count + per-texture mip images + texture metadata.
    public static List<TextureRecord> extract(
            byte[] txdData,
            File outputDir,
            boolean allMips,
            boolean manifestOnly,
            ExportManifest.Format manifestFormat
    ) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(txdData).order(ByteOrder.LITTLE_ENDIAN);
        int limit = txdData.length;

        // The outer chunk must begin with the PI TXD ID (0x23).
        Chunk outer = expectChunk(buffer, 0, limit, RW_ID_PI_TEXDICTIONARY, "PI TXD");

        if (outer.end != limit) {
            throw new IOException(String.format("PI TXD outer ends at 0x%08X, data ends at 0x%08X", outer.end(), limit));
        }
        if (outer.size() < 4) throw new IOException("PI TXD outer is too small");

        int textureCount = Short.toUnsignedInt(buffer.getShort(outer.payloadOffset()));
        int headerValue = Short.toUnsignedInt(buffer.getShort(outer.payloadOffset() + 2));
        int cursor = outer.payloadOffset() + 4;

        List<TextureRecord> textures = new ArrayList<>(textureCount);

        for (int index = 0; index < textureCount; index++) {
            int mipCount = u32(buffer, cursor, outer.end(), "texture " + index + " mip count");
            cursor += 4;
            if (mipCount <= 0 || mipCount > 32) {
                throw new IOException(String.format(
                        "Implausible mip count %d for texture %d at 0x%08X", mipCount, index, cursor - 4
                ));
            }

            List<ImageRecord> images = new ArrayList<>(mipCount);
            for (int m = 0; m < mipCount; m++) {
                ImageRecord img = parseImage(buffer, cursor, outer.end());
                images.add(img);
                cursor = img.chunk().end();
            }

            TextureRecord tex = parseTexture(buffer, cursor, outer.end(), index, images);
            textures.add(tex);
            cursor = tex.textureChunk().end();
        }
        if (cursor != outer.end()) throw new IOException(String.format("PI TXD inner data ends at 0x%08X, but we expected it to end at 0x%08X", cursor, outer.end()));
        if (!outputDir.exists() && !outputDir.mkdirs())throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());

        int writtenPngCount = 0;
        for (TextureRecord texture : textures) {
            String base = safeFilename(texture.name(), String.format("texture_%03d", texture.index()));
            if (base.toLowerCase(Locale.ROOT).endsWith(".png")) {
                base = base.substring(0, base.length() - 4);
            }

            int mipsToExport = allMips ? texture.images().size() : 1;
            for (int mipIndex = 0; mipIndex < mipsToExport; mipIndex++) {
                ImageRecord image = texture.images().get(mipIndex);
                String suffix = allMips ? String.format("__mip_%02d", mipIndex) : "";
                String filename = String.format("%03d_%s%s.png", texture.index(), base, suffix);

                if (!manifestOnly) {
                    BufferedImage bufferedImage = decodeToBufferedImage(txdData, image);
                    File pngFile = new File(outputDir, filename);
                    if (!ImageIO.write(bufferedImage, "png", pngFile)) {
                        throw new IOException("Failed to write PNG: " + pngFile);
                    }
                    writtenPngCount++;
                }
            }
        }

        writeTexturesManifest(outputDir, textures, manifestFormat);

        int totalImages = textures.stream().mapToInt(t -> t.images().size()).sum();
        System.out.printf(
                "PI texture dictionary: %d textures (headerValue=%d), %d RwImage chunks, %d PNG files written%n",
                textureCount, headerValue, totalImages, writtenPngCount
        );

        return textures;
    }

    private static ImageRecord parseImage(ByteBuffer buf, int offset, int limit) throws IOException {
        Chunk imageChunk = expectChunk(buf, offset, limit, RW_ID_IMAGE, "RwImage");
        Chunk structChunk = expectChunk(buf, imageChunk.payloadOffset(), imageChunk.end(), RW_ID_STRUCT, "RwImage STRUCT");
        if (structChunk.size() != 16) {
            throw new IOException(String.format(
                    "RwImage STRUCT at 0x%08X has size %d, expected 16", structChunk.offset(), structChunk.size()
            ));
        }

        int p = structChunk.payloadOffset();
        int width = buf.getInt(p);
        int height = buf.getInt(p + 4);
        int depth = buf.getInt(p + 8);
        int stride = buf.getInt(p + 12);

        if (width <= 0 || height <= 0) {
            throw new IOException(String.format("Zero/negative RwImage dimensions at 0x%08X", offset));
        }
        if (depth != 8 && depth != 32) {
            throw new IOException(String.format(
                    "Unsupported RwImage depth %d at 0x%08X; only 8 and 32 are supported", depth, offset
            ));
        }

        int pixelsOffset = structChunk.end();
        int pixelsSize = stride * height;
        int paletteSize = (depth == 8) ? 1024 : 0;
        int expectedEnd = pixelsOffset + pixelsSize + paletteSize;

        if (expectedEnd != imageChunk.end()) {
            throw new IOException(String.format(
                    "RwImage at 0x%08X: expected body end 0x%08X, chunk ends at 0x%08X",
                    offset, expectedEnd, imageChunk.end()
            ));
        }

        int minimumStride = (depth == 8) ? width : width * 4;
        if (stride < minimumStride) {
            throw new IOException(String.format(
                    "RwImage at 0x%08X: stride %d is smaller than minimum %d", offset, stride, minimumStride
            ));
        }

        Integer paletteOffset = (paletteSize > 0) ? (pixelsOffset + pixelsSize) : null;
        return new ImageRecord(
                imageChunk, width, height, depth, stride, pixelsOffset, pixelsSize, paletteOffset, paletteSize
        );
    }

    private static TextureRecord parseTexture(
            ByteBuffer buffer, int offset, int limit, int index, List<ImageRecord> images
    ) throws IOException {
        Chunk textureChunk = expectChunk(buffer, offset, limit, RW_ID_TEXTURE, "RwTexture");
        int cursor = textureChunk.payloadOffset();

        Chunk structChunk = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_STRUCT, "RwTexture STRUCT");
        if (structChunk.size() != 4) {
            throw new IOException(String.format(
                    "RwTexture STRUCT at 0x%08X has size %d, expected 4", structChunk.offset(), structChunk.size()
            ));
        }

        int filterAndAddress = u32(buffer, structChunk.payloadOffset(), structChunk.end(), "filter/address flags");
        cursor = structChunk.end();

        Chunk nameChunk = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_STRING, "texture name");
        String name = readRwString(buffer, nameChunk, "texture name");
        cursor = nameChunk.end();

        Chunk maskChunk = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_STRING, "mask name");
        String maskName = readRwString(buffer, maskChunk, "mask name");
        cursor = maskChunk.end();

        Chunk extension = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_EXTENSION, "RwTexture EXTENSION");
        if (extension.end() != textureChunk.end()) {
            throw new IOException(String.format(
                    "RwTexture at 0x%08X has bytes after its EXTENSION chunk", offset
            ));
        }

        return new TextureRecord(
                index, name, maskName, filterAndAddress, List.copyOf(images), textureChunk, extension.size()
        );
    }

    private static BufferedImage decodeToBufferedImage(byte[] data, ImageRecord image) {
        int width = image.width();
        int height = image.height();
        int[] argb = new int[width * height];

        if (image.depth() == 32) {
            for (int y = 0; y < height; y++) {
                int rowStart = image.pixelsOffset() + y * image.stride();
                for (int x = 0; x < width; x++) {
                    int p = rowStart + x * 4;
                    int r = data[p] & 0xFF;
                    int g = data[p + 1] & 0xFF;
                    int b = data[p + 2] & 0xFF;
                    int a = data[p + 3] & 0xFF;
                    argb[y * width + x] = (a << 24) | (r << 16) | (g << 8) | b;
                }
            }
        } else {
            int palOffset = image.paletteOffset();
            int[] palette = new int[256];
            for (int i = 0; i < 256; i++) {
                int p = palOffset + i * 4;
                int r = data[p] & 0xFF;
                int g = data[p + 1] & 0xFF;
                int b = data[p + 2] & 0xFF;
                int a = data[p + 3] & 0xFF;
                palette[i] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            for (int y = 0; y < height; y++) {
                int rowStart = image.pixelsOffset() + y * image.stride();
                for (int x = 0; x < width; x++) {
                    int idx = data[rowStart + x] & 0xFF;
                    argb[y * width + x] = palette[idx];
                }
            }
        }

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, argb, 0, width);
        return img;
    }
    private static void writeTexturesManifest(
            File outputDir, List<TextureRecord> textures, ExportManifest.Format format
    ) throws IOException {
        char sep = (format == ExportManifest.Format.TXT) ? '\t' : ';';
        StringBuilder sb = new StringBuilder();
        sb.append("index").append(sep)
                .append("name").append(sep)
                .append("maskName").append(sep)
                .append("width").append(sep)
                .append("height").append(sep)
                .append("depthBits").append(sep)
                .append("mipCount").append(sep)
                .append("filterAndAddressHex").append(sep)
                .append("filterMode").append(sep)
                .append("addressU").append(sep)
                .append("addressV").append(sep)
                .append("userMipmapsFlag\n");

        for (TextureRecord t : textures) {
            ImageRecord top = t.images().get(0);
            sb.append(t.index()).append(sep)
                    .append(t.name()).append(sep)
                    .append(t.maskName()).append(sep)
                    .append(top.width()).append(sep)
                    .append(top.height()).append(sep)
                    .append(top.depth()).append(sep)
                    .append(t.images().size()).append(sep)
                    .append(String.format("0x%08X", t.filterAndAddress())).append(sep)
                    .append(t.filterModeName()).append(sep)
                    .append(t.addressUName()).append(sep)
                    .append(t.addressVName()).append(sep)
                    .append(t.userMipmapsFlag()).append('\n');
        }

        File manifestFile = new File(outputDir, "textures_manifest." + format.getExtension());
        Files.writeString(manifestFile.toPath(), sb.toString(), StandardCharsets.UTF_8);
    }

    private static String readRwString(ByteBuffer buffer, Chunk chunk, String label) throws IOException {
        byte[] raw = new byte[chunk.size()];
        buffer.position(chunk.payloadOffset());
        buffer.get(raw);

        int nul = -1;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) {
                nul = i;
                break;
            }
        }
        if (nul < 0) {
            throw new IOException(String.format("%s at 0x%08X is not NUL-terminated", label, chunk.offset()));
        }
        return new String(raw, 0, nul, StandardCharsets.ISO_8859_1);
    }

    private static Chunk readChunk(ByteBuffer buffer, int offset, int limit, String label) throws IOException {
        if (offset < 0 || offset + 12 > limit) {
            throw new IOException(String.format("Incomplete %s header at 0x%08X", label, offset));
        }
        int chunkId = buffer.getInt(offset);
        int size = buffer.getInt(offset + 4);
        int libraryId = buffer.getInt(offset + 8);
        int payloadOffset = offset + 12;
        int end = payloadOffset + size;

        if (size < 0 || end > limit) {
            throw new IOException(String.format(
                    "%s at 0x%08X leaves its parent: end=0x%08X, limit=0x%08X", label, offset, end, limit
            ));
        }
        return new Chunk(offset, chunkId, size, libraryId, payloadOffset, end);
    }

    private static Chunk expectChunk(ByteBuffer buffer, int offset, int limit, int expectedId, String label) throws IOException {
        Chunk chunk = readChunk(buffer, offset, limit, label);
        if (chunk.chunkId() != expectedId) {
            throw new IOException(String.format(
                    "Expected %s id=0x%02X at 0x%08X, found 0x%02X", label, expectedId, offset, chunk.chunkId()
            ));
        }
        return chunk;
    }

    private static int u32(ByteBuffer buffer, int offset, int limit, String label) throws IOException {
        if (offset < 0 || offset + 4 > limit) {
            throw new IOException(String.format("Missing uint32 for %s at 0x%08X", label, offset));
        }
        return buffer.getInt(offset);
    }

    private static String safeFilename(String name, String fallback) {
        if (name == null) {
            return fallback;
        }
        String normalized = name.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0) {
            normalized = normalized.substring(slash + 1);
        }
        String cleaned = normalized.replaceAll("[<>:\"/\\\\|?*\\x00-\\x1F]", "_").trim();
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? fallback : cleaned;
    }
}
