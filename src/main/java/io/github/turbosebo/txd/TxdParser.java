package io.github.turbosebo.txd;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TxdParser {
    public static final int RW_ID_STRUCT = 0x01;
    public static final int RW_ID_STRING = 0x02;
    public static final int RW_ID_EXTENSION = 0x03;
    public static final int RW_ID_TEXTURE = 0x06;
    public static final int RW_ID_IMAGE = 0x18;
    public static final int RW_ID_PI_TEXDICTIONARY = 0x23;

    private TxdParser() {
    }

    public static List<TxdTypes.TextureRecord> parse(byte[] txdData) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(txdData).order(ByteOrder.LITTLE_ENDIAN);
        int limit = txdData.length;

        TxdTypes.Chunk outer = expectChunk(buffer, 0, limit, RW_ID_PI_TEXDICTIONARY, "PI TXD");
        if (outer.end() != limit) {
            throw new IOException(String.format("PI TXD outer ends at 0x%08X, data ends at 0x%08X", outer.end(), limit));
        }
        if (outer.size() < 4) {
            throw new IOException("PI TXD outer is too small");
        }

        int textureCount = Short.toUnsignedInt(buffer.getShort(outer.payloadOffset()));
        int headerValue = Short.toUnsignedInt(buffer.getShort(outer.payloadOffset() + 2));
        int cursor = outer.payloadOffset() + 4;
        List<TxdTypes.TextureRecord> textures = new ArrayList<>(textureCount);

        for (int index = 0; index < textureCount; index++) {
            int mipCount = u32(buffer, cursor, outer.end(), "texture " + index + " mip count");
            cursor += 4;
            if (mipCount <= 0 || mipCount > 32) {
                throw new IOException(String.format(
                        "Implausible mip count %d for texture %d at 0x%08X", mipCount, index, cursor - 4
                ));
            }

            List<TxdTypes.ImageRecord> images = new ArrayList<>(mipCount);
            for (int m = 0; m < mipCount; m++) {
                TxdTypes.ImageRecord img = parseImage(buffer, cursor, outer.end());
                images.add(img);
                cursor = img.chunk().end();
            }

            TxdTypes.TextureRecord tex = parseTexture(buffer, cursor, outer.end(), index, images);
            textures.add(tex);
            cursor = tex.textureChunk().end();
        }

        if (cursor != outer.end()) {
            throw new IOException(String.format(
                    "PI TXD inner data ends at 0x%08X, but we expected it to end at 0x%08X", cursor, outer.end()
            ));
        }

        System.out.printf(
                "PI texture dictionary: %d textures (headerValue=%d), %d RwImage chunks%n",
                textureCount, headerValue, textures.stream().mapToInt(t -> t.images().size()).sum()
        );
        return textures;
    }

    static TxdTypes.ImageRecord parseImage(ByteBuffer buf, int offset, int limit) throws IOException {
        TxdTypes.Chunk imageChunk = expectChunk(buf, offset, limit, RW_ID_IMAGE, "RwImage");
        TxdTypes.Chunk structChunk = expectChunk(buf, imageChunk.payloadOffset(), imageChunk.end(), RW_ID_STRUCT, "RwImage STRUCT");
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
        return new TxdTypes.ImageRecord(
                imageChunk, width, height, depth, stride, pixelsOffset, pixelsSize, paletteOffset, paletteSize
        );
    }

    static TxdTypes.TextureRecord parseTexture(
            ByteBuffer buffer, int offset, int limit, int index, List<TxdTypes.ImageRecord> images
    ) throws IOException {
        TxdTypes.Chunk textureChunk = expectChunk(buffer, offset, limit, RW_ID_TEXTURE, "RwTexture");
        int cursor = textureChunk.payloadOffset();

        TxdTypes.Chunk structChunk = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_STRUCT, "RwTexture STRUCT");
        if (structChunk.size() != 4) {
            throw new IOException(String.format(
                    "RwTexture STRUCT at 0x%08X has size %d, expected 4", structChunk.offset(), structChunk.size()
            ));
        }

        int filterAndAddress = u32(buffer, structChunk.payloadOffset(), structChunk.end(), "filter/address flags");
        cursor = structChunk.end();

        TxdTypes.Chunk nameChunk = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_STRING, "texture name");
        String name = readRwString(buffer, nameChunk, "texture name");
        cursor = nameChunk.end();

        TxdTypes.Chunk maskChunk = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_STRING, "mask name");
        String maskName = readRwString(buffer, maskChunk, "mask name");
        cursor = maskChunk.end();

        TxdTypes.Chunk extension = expectChunk(buffer, cursor, textureChunk.end(), RW_ID_EXTENSION, "RwTexture EXTENSION");
        if (extension.end() != textureChunk.end()) {
            throw new IOException(String.format(
                    "RwTexture at 0x%08X has bytes after its EXTENSION chunk", offset
            ));
        }

        return new TxdTypes.TextureRecord(
                index, name, maskName, filterAndAddress, List.copyOf(images), textureChunk, extension.size()
        );
    }

    static String readRwString(ByteBuffer buffer, TxdTypes.Chunk chunk, String label) throws IOException {
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

    static TxdTypes.Chunk readChunk(ByteBuffer buffer, int offset, int limit, String label) throws IOException {
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
        return new TxdTypes.Chunk(offset, chunkId, size, libraryId, payloadOffset, end);
    }

    static TxdTypes.Chunk expectChunk(ByteBuffer buffer, int offset, int limit, int expectedId, String label) throws IOException {
        TxdTypes.Chunk chunk = readChunk(buffer, offset, limit, label);
        if (chunk.chunkId() != expectedId) {
            throw new IOException(String.format(
                    "Expected %s id=0x%02X at 0x%08X, found 0x%02X", label, expectedId, offset, chunk.chunkId()
            ));
        }
        return chunk;
    }

    static int u32(ByteBuffer buffer, int offset, int limit, String label) throws IOException {
        if (offset < 0 || offset + 4 > limit) {
            throw new IOException(String.format("Missing uint32 for %s at 0x%08X", label, offset));
        }
        return buffer.getInt(offset);
    }
}

