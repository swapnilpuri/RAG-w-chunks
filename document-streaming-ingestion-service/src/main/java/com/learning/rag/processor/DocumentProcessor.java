package com.learning.rag.processor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Component
public class DocumentProcessor {
    
    public String extractText(File file, String fileType) throws IOException {
        return switch (fileType.toLowerCase()) {
            case "pdf" -> extractTextFromPDF(file);
            case "docx" -> extractTextFromDOCX(file);
            case "xlsx" -> extractTextFromXLSX(file);
            default -> throw new IllegalArgumentException("Unsupported file type: " + fileType);
        };
    }
    
    private String extractTextFromPDF(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
    
    private String extractTextFromDOCX(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                text.append(paragraph.getText()).append("\n");
            }
            return text.toString();
        }
    }
    
    private String extractTextFromXLSX(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {
            StringBuilder text = new StringBuilder();
            DataFormatter formatter = new DataFormatter();
            
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                text.append("Sheet: ").append(sheet.getSheetName()).append("\n");
                
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        text.append(formatter.formatCellValue(cell)).append("\t");
                    }
                    text.append("\n");
                }
                text.append("\n");
            }
            return text.toString();
        }
    }
}