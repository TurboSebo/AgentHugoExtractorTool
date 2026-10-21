package io.github.turbosebo.txd;

import java.util.List;
import java.util.Map;

public final class TxdTypes {
    private TxdTypes() {
    }

    public record Chunk(int offset, int chunkId, int size, int libraryId, int payloadOffset, int end) {
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
    ) {
    }

    public record TextureRecord(
            int index,
            String name,
            String maskName,
            int filterAndAddress,
            List<ImageRecord> images,
            Chunk textureChunk,
            int extensionSize
    ) {
        private static final Map<Integer, String> FILTER_NAMES = Map.of(
                0, "NA",
                1, "NEAREST",
                2, "LINEAR",
                3, "MIP_NEAREST",
                4, "MIP_LINEAR",
                5, "LINEAR_MIP_NEAREST",
                6, "LINEAR_MIP_LINEAR"
        );
        private static final Map<Integer, String> ADDRESS_NAMES = Map.of(
                0, "NA",
                1, "WRAP",
                2, "MIRROR",
                3, "CLAMP",
                4, "BORDER"
        );

        public String filterModeName() {
            return FILTER_NAMES.getOrDefault(filterAndAddress & 0xFF, "UNKNOWN");
        }

        public String addressUName() {
            return ADDRESS_NAMES.getOrDefault((filterAndAddress >>> 8) & 0x0F, "UNKNOWN");
        }

        public String addressVName() {
            return ADDRESS_NAMES.getOrDefault((filterAndAddress >>> 12) & 0x0F, "UNKNOWN");
        }

        public int streamFlags() {
            return (filterAndAddress >> 16) & 0xFF;
        }

        public boolean userMipmapsFlag() {
            return (streamFlags() & 1) != 0;
        }
    }
}

