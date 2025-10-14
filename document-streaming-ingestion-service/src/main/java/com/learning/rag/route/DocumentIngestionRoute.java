package com.learning.rag.route;

import com.learning.rag.service.StreamingIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@RequiredArgsConstructor
@Slf4j
public class DocumentIngestionRoute extends RouteBuilder {
    
    private final StreamingIngestionService streamingIngestionService;
    @Value("${document.input.path}")
    private String inputFolder;
    
    @Override
    public void configure() throws Exception {
        
        // Error handler
        onException(Exception.class)
            .handled(true)
            .log("Error ingesting document: ${exception.message}")
            .to("direct:ingestion-error");
        
        // Main ingestion route with streaming
        from("direct:ingest-document")
            .routeId("document-ingestion-route")
            .log("Received document for ingestion: ${header.filename}")
            .process(exchange -> {
                File file = exchange.getIn().getBody(File.class);
                String filename = exchange.getIn().getHeader("filename", String.class);
                String fileType = exchange.getIn().getHeader("fileType", String.class);
                
                if (file == null || filename == null || fileType == null) {
                    throw new IllegalArgumentException("Missing required parameters: file, filename, or fileType");
                }
                
                log.info("Starting streaming ingestion for: {}", filename);
                
                // Use streaming ingestion service
                streamingIngestionService.ingestDocumentStreaming(file, filename, fileType);
                
                exchange.getIn().setHeader("ingestionStatus", "SUCCESS");
                exchange.getIn().setBody("Document ingested successfully: " + filename);
            })
            .log("Document ingestion completed: ${header.filename}")
            .to("direct:ingestion-success");
        
        // Success handler
        from("direct:ingestion-success")
            .routeId("ingestion-success-handler")
            .log("Successfully processed: ${body}")
            .toD("file:{{document.archive.path}}?fileName=${header.CamelFileName}")
            .process(exchange -> {
                // Delete original file after successful copy
                File originalFile = new File(inputFolder, 
                    exchange.getIn().getHeader("CamelFileName", String.class));
                if (originalFile.exists()) {
                    originalFile.delete();
                    log.info("Deleted original file: {}", originalFile.getName());
                }
            });;
        
        // Error handler
        from("direct:ingestion-error")
            .routeId("ingestion-error-handler")
            .log("Failed to process document: ${exception.message}")
            .toD("file:{{document.failure.path}}?fileName=${header.CamelFileName}")
            .process(exchange -> {
                // Delete original file after copy
                File originalFile = new File(inputFolder, 
                    exchange.getIn().getHeader("CamelFileName", String.class));
                if (originalFile.exists()) {
                    originalFile.delete();
                    log.info("Deleted original file from input: {}", originalFile.getName());
                }
            });;
        
        // File polling route example (if you want to poll a directory)
        from("file:{{document.input.path}}"
         + "?include=.*\\.(pdf|PDF|xlsx|XLSX|docx|DOCX)"
            + "&noop=false"
            + "&preMove=inprogress"
            + "&readLock=changed"
            + "&readLockCheckInterval=5000"
            + "&delay={{document.poll-interval}}"
            + "&initialDelay=5000"
            + "&delete=false")
            .routeId("document-file-polling")
            .log("Picked up file: ${header.CamelFileName}")
            .setHeader("filename", simple("${header.CamelFileName}"))
            .setHeader("fileType", method(this, "extractFileType(${header.CamelFileName})"))
            .to("direct:ingest-document");
    }
    
    /**
     * Extract file type from filename
     */
    public String extractFileType(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "txt";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }
}