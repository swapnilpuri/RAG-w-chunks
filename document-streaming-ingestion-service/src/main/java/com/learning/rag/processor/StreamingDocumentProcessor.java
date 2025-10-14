package com.learning.rag.processor;

import lombok.extern.slf4j.Slf4j;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
public class StreamingDocumentProcessor {
    
    /**
     * Get a Reader for the document without loading entire content into memory
     */
    public Reader getDocumentReader(File file, String fileType) throws IOException {
        log.info("Creating reader for file type: {}", fileType);
        
        switch (fileType.toLowerCase()) {
            case "txt":
                return new BufferedReader(
                    new InputStreamReader(
                        new FileInputStream(file), 
                        StandardCharsets.UTF_8
                    )
                );
                
            case "pdf":
                return getPdfReader(file);
                
            case "docx":
                return getDocxReader(file);
                
            default:
                throw new IllegalArgumentException("Unsupported file type: " + fileType);
        }
    }
    
    /**
     * Create reader for PDF files
     * Note: PDFBox still loads PDF into memory, but we extract text page by page
     */
    private Reader getPdfReader(File file) throws IOException {
        PDDocument document = Loader.loadPDF(file);
        return new PdfStreamReader(document);
    }
    
    /**
     * Create reader for DOCX files
     */
    private Reader getDocxReader(File file) throws IOException {
        try(FileInputStream fis = new FileInputStream(file);
        XWPFDocument document = new XWPFDocument(fis)) {
            XWPFWordExtractor extractor = new XWPFWordExtractor(document);
            return new DocxStreamReader(extractor, fis, document);
        }
        
    }
    
    /**
     * Custom Reader implementation for PDF documents
     * Extracts text page by page to reduce memory usage
     */
    private static class PdfStreamReader extends Reader {
        private final PDDocument document;
        private final PDFTextStripper stripper;
        private int currentPage = 1;
        private StringReader currentPageReader;
        private final int totalPages;
        
        public PdfStreamReader(PDDocument document) throws IOException {
            this.document = document;
            this.totalPages = document.getNumberOfPages();
            this.stripper = new PDFTextStripper();
            loadNextPage();
        }
        
        private void loadNextPage() throws IOException {
            if (currentPage <= totalPages) {
                stripper.setStartPage(currentPage);
                stripper.setEndPage(currentPage);
                String pageText = stripper.getText(document);
                currentPageReader = new StringReader(pageText);
                currentPage++;
            } else {
                currentPageReader = null;
            }
        }
        
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (currentPageReader == null) {
                return -1;
            }
            
            int charsRead = currentPageReader.read(cbuf, off, len);
            
            if (charsRead == -1) {
                loadNextPage();
                if (currentPageReader != null) {
                    return read(cbuf, off, len);
                }
                return -1;
            }
            
            return charsRead;
        }
        
        @Override
        public void close() throws IOException {
            if (currentPageReader != null) {
                currentPageReader.close();
            }
            if (document != null) {
                document.close();
            }
        }
    }
    
    /**
     * Custom Reader implementation for DOCX documents
     */
    private static class DocxStreamReader extends Reader {
        private final StringReader textReader;
        private final XWPFWordExtractor extractor;
        private final FileInputStream fis;
        private final XWPFDocument document;
        
        public DocxStreamReader(XWPFWordExtractor extractor, FileInputStream fis, XWPFDocument document) {
            this.extractor = extractor;
            this.fis = fis;
            this.document = document;
            this.textReader = new StringReader(extractor.getText());
        }
        
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return textReader.read(cbuf, off, len);
        }
        
        @Override
        public void close() throws IOException {
            textReader.close();
            extractor.close();
            document.close();
            fis.close();
        }
    }
}