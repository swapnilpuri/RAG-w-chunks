package com.learning.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.function.Consumer;

@Service
@Slf4j
public class StreamingChunkingService {
    
    private static final int CHUNK_SIZE = 1000;
    private static final int CHUNK_OVERLAP = 200;
    private static final int BUFFER_SIZE = 8192; // Read buffer size
    
    /**
     * Process text in streaming fashion, calling consumer for each chunk
     * This avoids loading entire document into memory
     */
    public int streamChunks(Reader reader, Consumer<String> chunkConsumer) throws IOException {
        StringBuilder buffer = new StringBuilder(CHUNK_SIZE + CHUNK_OVERLAP);
        StringBuilder overlap = new StringBuilder(CHUNK_OVERLAP);
        int chunkCount = 0;
        
        try (BufferedReader br = new BufferedReader(reader, BUFFER_SIZE)) {
            char[] readBuffer = new char[BUFFER_SIZE];
            int charsRead;
            
            while ((charsRead = br.read(readBuffer)) != -1) {
                buffer.append(readBuffer, 0, charsRead);
                
                // Process chunks when buffer is large enough
                while (buffer.length() >= CHUNK_SIZE) {
                    int chunkEnd = findChunkBoundary(buffer, CHUNK_SIZE);
                    
                    // Extract chunk
                    String chunk = buffer.substring(0, chunkEnd).trim();
                    if (!chunk.isEmpty()) {
                        chunkConsumer.accept(chunk);
                        chunkCount++;
                        log.debug("Processed chunk {}: {} characters", chunkCount, chunk.length());
                    }
                    
                    // Calculate overlap start position
                    int overlapStart = Math.max(0, chunkEnd - CHUNK_OVERLAP);
                    
                    // Save overlap for next chunk
                    overlap.setLength(0);
                    if (overlapStart < chunkEnd) {
                        overlap.append(buffer.substring(overlapStart, chunkEnd));
                    }
                    
                    // Remove processed content from buffer
                    buffer.delete(0, chunkEnd);
                    
                    // If buffer is now small, prepend overlap and continue reading
                    if (buffer.length() < CHUNK_SIZE / 2) {
                        String remaining = buffer.toString();
                        buffer.setLength(0);
                        buffer.append(overlap).append(remaining);
                        overlap.setLength(0);
                        break;
                    }
                }
            }
            
            // Process remaining content
            if (buffer.length() > 0) {
                String finalChunk = buffer.toString().trim();
                if (!finalChunk.isEmpty()) {
                    chunkConsumer.accept(finalChunk);
                    chunkCount++;
                }
            }
        }
        
        log.info("Streaming chunking complete: {} chunks created", chunkCount);
        return chunkCount;
    }
    
    /**
     * Find appropriate chunk boundary near target position
     */
    private int findChunkBoundary(StringBuilder buffer, int targetPosition) {
        int bufferLength = buffer.length();
        int position = Math.min(targetPosition, bufferLength);
        
        // Look back up to 100 characters for a sentence boundary
        int lookback = Math.min(100, position);
        int searchStart = position - lookback;
        
        // Search for sentence endings
        for (int i = position - 1; i >= searchStart; i--) {
            char c = buffer.charAt(i);
            if (c == '.' || c == '!' || c == '?') {
                if (i + 1 < bufferLength && Character.isWhitespace(buffer.charAt(i + 1))) {
                    return i + 1;
                }
            }
        }
        
        // Look for paragraph break
        for (int i = position - 1; i >= searchStart; i--) {
            if (buffer.charAt(i) == '\n') {
                return i + 1;
            }
        }
        
        // Look for any whitespace
        for (int i = position - 1; i >= searchStart; i--) {
            if (Character.isWhitespace(buffer.charAt(i))) {
                return i + 1;
            }
        }
        
        return position;
    }
    
    /**
     * Estimate token count
     */
    public int estimateTokenCount(String text) {
        return text.length() / 4;
    }
}