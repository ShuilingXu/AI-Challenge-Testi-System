package com.autohr.modules.interview.service;

import com.autohr.common.exception.BusinessException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.sl.extractor.SlideShowExtractor;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class TeachingMaterialReader {
    public static final int MAX_TEXT = 60000;

    public String read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 10 * 1024 * 1024)
            throw new BusinessException("材料不能为空，且每个文件不能超过10MB");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        try (var input = file.getInputStream()) {
            String text;
            switch (extension) {
                case "pdf" -> {
                    try (var doc = PDDocument.load(input)) {
                        if (doc.getNumberOfPages() > 200) throw new BusinessException("PDF最多支持200页");
                        text = new PDFTextStripper().getText(doc);
                    }
                }
                case "docx" -> {
                    try (var doc = new XWPFDocument(input); var extractor = new XWPFWordExtractor(doc)) {
                        text = extractor.getText();
                    }
                }
                case "pptx" -> {
                    try (var doc = new XMLSlideShow(input); var extractor = new SlideShowExtractor<>(doc)) {
                        text = extractor.getText();
                    }
                }
                case "xls", "xlsx" -> {
                    try (var workbook = WorkbookFactory.create(input)) {
                        var result = new StringBuilder();
                        var formatter = new DataFormatter();
                        for (var sheet : workbook) for (var row : sheet) {
                            for (var cell : row) result.append(formatter.formatCellValue(cell)).append('\t');
                            result.append('\n');
                            if (result.length() > MAX_TEXT) throw new BusinessException("材料文字过多，请拆分后上传");
                        }
                        text = result.toString();
                    }
                }
                case "txt", "md", "csv" -> text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                default -> throw new BusinessException("支持 PDF、DOCX、PPTX、XLS、XLSX、TXT、MD、CSV 格式");
            }
            text = text.replace("\u0000", "").trim();
            if (text.isBlank()) throw new BusinessException("材料没有可提取的文字，扫描件或图片请先转为文字");
            if (text.length() > MAX_TEXT) throw new BusinessException("材料文字过多，请拆分后上传（单次最多60000字）");
            return text;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("无法读取材料，请检查格式、文件完整性及是否加密");
        }
    }
}
