package com.learning.rag.processor;

import lombok.extern.slf4j.Slf4j;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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

            case "xlsx":
                return getXlsxReader(file);
                
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
     * Create reader for XLSX files
     * Extracts text from all sheets, all rows, all cells
     */
    private Reader getXlsxReader(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        XSSFWorkbook workbook = new XSSFWorkbook(fis);
        
        return new XlsxStreamReader(workbook, fis);
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

        /**
     * Custom Reader implementation for XLSX documents
     * Streams through sheets, rows, and cells
     */
    private static class XlsxStreamReader extends Reader {
        private final XSSFWorkbook workbook;
        private final FileInputStream fis;
        private final StringReader textReader;
        
        public XlsxStreamReader(XSSFWorkbook workbook, FileInputStream fis) {
            this.workbook = workbook;
            this.fis = fis;
            this.textReader = new StringReader(extractAllText());
        }
        
        /**
         * Extract text from all sheets in the workbook
         */
        private String extractAllText() {
            StringBuilder allText = new StringBuilder();
            DataFormatter dataFormatter = new DataFormatter();
            
            int numberOfSheets = workbook.getNumberOfSheets();
            
            for (int sheetIndex = 0; sheetIndex < numberOfSheets; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = sheet.getSheetName();
                
                // Add sheet header
                allText.append("=== Sheet: ").append(sheetName).append(" ===\n\n");
                
                // Check if sheet has any content
                if (sheet.getPhysicalNumberOfRows() == 0) {
                    allText.append("(Empty sheet)\n\n");
                    continue;
                }
                
                // Process each row
                for (Row row : sheet) {
                    StringBuilder rowText = new StringBuilder();
                    boolean hasContent = false;
                    
                    // Process each cell in the row
                    for (Cell cell : row) {
                        String cellValue = getCellValueAsString(cell, dataFormatter);
                        
                        if (cellValue != null && !cellValue.trim().isEmpty()) {
                            if (hasContent) {
                                rowText.append(" | ");
                            }
                            rowText.append(cellValue.trim());
                            hasContent = true;
                        }
                    }
                    
                    // Add row to output if it has content
                    if (hasContent) {
                        allText.append(rowText.toString()).append("\n");
                    }
                }
                
                // Add spacing between sheets
                allText.append("\n");
            }
            
            return allText.toString();
        }
        
        /**
         * Extract cell value as string, handling different cell types
         */
        private String getCellValueAsString(Cell cell, DataFormatter dataFormatter) {
            if (cell == null) {
                return "";
            }
            
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                    
                case NUMERIC:
                    if (DateUtil.isCellDateFormatted(cell)) {
                        // Format date cells
                        return dataFormatter.formatCellValue(cell);
                    } else {
                        // Format numeric cells
                        return dataFormatter.formatCellValue(cell);
                    }
                    
                case BOOLEAN:
                    return String.valueOf(cell.getBooleanCellValue());
                    
                case FORMULA:
                    // Try to get the cached formula result
                    try {
                        return dataFormatter.formatCellValue(cell);
                    } catch (Exception e) {
                        return cell.getCellFormula();
                    }
                    
                case BLANK:
                    return "";
                    
                case ERROR:
                    return "ERROR:" + cell.getErrorCellValue();
                    
                default:
                    return "";
            }
        }
        
        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            return textReader.read(cbuf, off, len);
        }
        
        @Override
        public void close() throws IOException {
            textReader.close();
            workbook.close();
            fis.close();
        }
    }
}