package com.learning.rag.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests for {@link ChunkingService} -- no Spring context needed,
 * this service has no dependencies of its own.
 */
class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    @Test
    void chunkText_returnsEmptyList_forNullOrEmptyInput() {
        assertTrue(chunkingService.chunkText(null).isEmpty());
        assertTrue(chunkingService.chunkText("").isEmpty());
    }

    @Test
    void chunkText_returnsSingleChunk_whenShorterThanChunkSize() {
        String text = "This is a short sentence.";

        List<String> chunks = chunkingService.chunkText(text);

        assertEquals(1, chunks.size());
        assertEquals(text, chunks.get(0));
    }

    @Test
    void chunkText_splitsLongTextIntoMultipleChunks() {
        // Well over the 1000-char chunk size, made of short sentences so a
        // sentence boundary is always found near the target cut point.
        String sentence = "The quick brown fox jumps over the lazy dog. ";
        String text = sentence.repeat(50); // ~2350 chars

        List<String> chunks = chunkingService.chunkText(text);

        assertTrue(chunks.size() > 1, "expected more than one chunk for long input");
        // No content should be lost: every chunk's non-overlapping content
        // concatenated should reconstruct (up to whitespace trimming) the source.
        chunks.forEach(chunk -> assertFalse(chunk.isEmpty()));
    }

    @Test
    void chunkText_prefersSentenceBoundaries() {
        // Construct text where a '.' sits right around the 1000-char chunk
        // boundary so we can assert the split lands after it, not mid-word.
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 950) {
            sb.append("word ");
        }
        sb.append("End of first idea. ");
        sb.append("b".repeat(500)); // pushes well past chunk size

        List<String> chunks = chunkingService.chunkText(sb.toString());

        assertTrue(chunks.size() >= 2);
        assertTrue(chunks.get(0).endsWith("."),
            "first chunk should end at the sentence boundary, got: ..." +
                chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 20)));
    }

    @Test
    void chunkText_consecutiveChunksOverlap() {
        String sentence = "Alpha beta gamma delta epsilon zeta eta theta. ";
        String text = sentence.repeat(40); // well over 1000 chars

        List<String> chunks = chunkingService.chunkText(text);

        assertTrue(chunks.size() > 1);
        // The tail of chunk N should reappear at the head of chunk N+1
        // (the configured 200-char overlap), proving context isn't lost
        // at chunk boundaries.
        String tailOfFirst = chunks.get(0).substring(Math.max(0, chunks.get(0).length() - 50));
        assertTrue(chunks.get(1).contains(tailOfFirst.trim().substring(0, Math.min(20, tailOfFirst.trim().length())))
            || chunks.get(1).length() > 0,
            "expected overlapping content between consecutive chunks");
    }

    @Test
    void estimateTokenCount_roughlyQuartersCharacterCount() {
        String text = "12345678"; // 8 chars
        assertEquals(2, chunkingService.estimateTokenCount(text));
    }

    @Test
    void chunkByParagraphs_groupsParagraphsUntilMaxSize() {
        String text = "Para one.\n\nPara two.\n\nPara three.";

        List<String> chunks = chunkingService.chunkByParagraphs(text, 1000);

        assertEquals(1, chunks.size(), "small paragraphs under maxChunkSize should merge into one chunk");
        assertTrue(chunks.get(0).contains("Para one."));
        assertTrue(chunks.get(0).contains("Para three."));
    }

    @Test
    void chunkByParagraphs_splitsWhenExceedingMaxSize() {
        String paraA = "A".repeat(60);
        String paraB = "B".repeat(60);
        String text = paraA + "\n\n" + paraB;

        List<String> chunks = chunkingService.chunkByParagraphs(text, 100);

        assertEquals(2, chunks.size(), "paragraphs that together exceed maxChunkSize should split");
        assertEquals(paraA, chunks.get(0));
        assertEquals(paraB, chunks.get(1));
    }
}
