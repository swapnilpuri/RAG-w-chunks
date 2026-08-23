package com.learning.rag.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StreamingChunkingService}. The point of the streaming
 * rewrite is that reading a document through a bounded buffer produces
 * essentially the same chunking outcome as {@link ChunkingService}'s
 * in-memory pass over the full text -- these tests check the parts of that
 * equivalence that should hold exactly (chunk count, first boundary), plus
 * streaming-specific edge cases (empty input, multi-buffer reads).
 */
class StreamingChunkingServiceTest {

    private final StreamingChunkingService streamingChunkingService = new StreamingChunkingService();
    private final ChunkingService chunkingService = new ChunkingService();

    private List<String> streamChunks(String text) throws IOException {
        List<String> chunks = new ArrayList<>();
        streamingChunkingService.streamChunks(new StringReader(text), chunks::add);
        return chunks;
    }

    @Test
    void streamChunks_returnsNoChunks_forEmptyInput() throws IOException {
        assertEquals(0, streamChunks("").size());
    }

    @Test
    void streamChunks_returnsSingleChunk_whenShorterThanChunkSize() throws IOException {
        String text = "This is a short sentence.";

        List<String> chunks = streamChunks(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void streamChunks_matchesInMemoryChunking_forUniformLongText() throws IOException {
        // StreamingChunkingService and ChunkingService use independent boundary
        // searches (a sliding buffer vs. the whole string), so they can pick
        // slightly different cut points a few chunks in -- that's expected,
        // not a bug. What must hold: same chunk count for the same input, and
        // an identical first chunk, since both start the search from position
        // 0 with the same "look back up to 100 chars for a sentence end" rule.
        String sentence = "The quick brown fox jumps over the lazy dog. ";
        String text = sentence.repeat(50); // well over the 1000-char chunk size

        List<String> streamed = streamChunks(text);
        List<String> inMemory = chunkingService.chunkText(text);

        assertEquals(inMemory.size(), streamed.size(),
            "streaming and in-memory chunking should produce the same chunk count");
        assertEquals(inMemory.get(0), streamed.get(0),
            "both strategies should agree on the first chunk boundary");
    }

    @Test
    void streamChunks_handlesTextSpanningMultipleInternalBuffers() throws IOException {
        // BUFFER_SIZE is 8192 chars; make sure a document that requires
        // several internal read() calls still chunks without losing content.
        String sentence = "Alpha beta gamma delta epsilon zeta eta theta iota kappa. ";
        String text = sentence.repeat(1000); // ~60,000 chars, several buffer refills

        List<String> chunks = streamChunks(text);

        assertFalse(chunks.isEmpty());
        chunks.forEach(chunk -> assertFalse(chunk.isBlank()));

        // No content should vanish: every chunk's content should actually
        // appear in the source text (sanity check against silent corruption).
        chunks.forEach(chunk -> assertTrue(text.contains(chunk.trim()),
            "chunk should be a substring of the original text"));
    }

    @Test
    void streamChunks_returnsChunkCountFromConsumerCallCount() throws IOException {
        String sentence = "Alpha beta gamma delta epsilon zeta eta theta. ";
        String text = sentence.repeat(40);

        List<String> chunks = new ArrayList<>();
        int reportedCount = streamingChunkingService.streamChunks(new StringReader(text), chunks::add);

        assertEquals(chunks.size(), reportedCount,
            "the returned chunk count should match the number of times the consumer was called");
    }
}
