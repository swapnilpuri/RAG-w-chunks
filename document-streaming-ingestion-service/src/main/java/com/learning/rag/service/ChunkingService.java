package com.learning.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ChunkingService {
    
    // Configuration
    private static final int CHUNK_SIZE = 1000;  // Characters per chunk
    private static final int CHUNK_OVERLAP = 200; // Overlap between chunks
    
    /**
     * Split text into overlapping chunks
     * 
     * @param text The text to chunk
     * @return List of text chunks
     */
    public List<String> chunkText(String text) {
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> chunks = new ArrayList<>();
        int textLength = text.length();
        int start = 0;
        
        while (start < textLength) {
            int end = Math.min(start + CHUNK_SIZE, textLength);
            
            // Try to break at sentence boundary if possible
            if (end < textLength) {
                end = findSentenceBoundary(text, end);
            }
            
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
                log.debug("Created chunk {}: {} characters", chunks.size(), chunk.length());
            }
            
            // Move start position with overlap
            start = end - CHUNK_OVERLAP;
            if (start >= textLength) break;
        }
        
        log.info("Split text into {} chunks", chunks.size());
        return chunks;
    }
    
    /**
     * Find the nearest sentence boundary (., !, ?) before the given position
     */
    private int findSentenceBoundary(String text, int position) {
        // Look back up to 100 characters for a sentence boundary
        int lookback = Math.min(100, position);
        int searchStart = position - lookback;
        
        // Search for sentence endings
        for (int i = position - 1; i >= searchStart; i--) {
            char c = text.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                // Make sure it's followed by space or end of text
                if (i + 1 < text.length() && Character.isWhitespace(text.charAt(i + 1))) {
                    return i + 1;
                }
            }
        }
        
        // If no sentence boundary found, look for paragraph break
        for (int i = position - 1; i >= searchStart; i--) {
            if (text.charAt(i) == '\n') {
                return i + 1;
            }
        }
        
        // If no good boundary found, use original position
        return position;
    }
    
    /**
     * Estimate token count (rough approximation: 1 token ≈ 4 characters)
     */
    public int estimateTokenCount(String text) {
        return text.length() / 4;
    }
    
    /**
     * Chunk text using paragraph-based strategy (alternative method)
     */
    public List<String> chunkByParagraphs(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\n\n+");
        
        StringBuilder currentChunk = new StringBuilder();
        
        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;
            
            // If adding this paragraph exceeds max size, start new chunk
            if (currentChunk.length() + paragraph.length() > maxChunkSize && currentChunk.length() > 0) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
            
            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
            
            // If single paragraph is too large, split it
            if (currentChunk.length() > maxChunkSize) {
                chunks.add(currentChunk.toString().trim());
                currentChunk = new StringBuilder();
            }
        }
        
        // Add remaining text
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks;
    }
}
