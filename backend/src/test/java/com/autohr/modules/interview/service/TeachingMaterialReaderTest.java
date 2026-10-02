package com.autohr.modules.interview.service;

import com.autohr.common.exception.BusinessException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class TeachingMaterialReaderTest {
    private final TeachingMaterialReader reader = new TeachingMaterialReader();
    private MockMultipartFile file(String name, byte[] bytes) { return new MockMultipartFile("files", name, "application/octet-stream", bytes); }

    @Test void extractsTeachingTextFromWordSlidesAndWorkbook() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var doc = new XWPFDocument()) {
            doc.createParagraph().createRun().setText("课程大纲：循环结构");
            doc.createTable(1, 1).getRow(0).getCell(0).setText("教学目标：理解循环");
            doc.write(bytes);
        }
        String word = reader.read(file("大纲.docx", bytes.toByteArray()));
        assertTrue(word.contains("循环结构")); assertTrue(word.contains("理解循环"));
        bytes.reset();
        try (var slides = new XMLSlideShow()) {
            slides.createSlide().createTextBox().setText("课件：条件判断"); slides.write(bytes);
        }
        assertTrue(reader.read(file("课件.pptx", bytes.toByteArray())).contains("条件判断"));
        bytes.reset();
        try (var workbook = new XSSFWorkbook()) {
            workbook.createSheet().createRow(0).createCell(0).setCellValue("教学参考：函数"); workbook.write(bytes);
        }
        assertTrue(reader.read(file("参考.xlsx", bytes.toByteArray())).contains("函数"));
    }

    @Test void rejectsEmptyScannedUnsupportedAndOversizedMaterials() throws Exception {
        assertThrows(BusinessException.class, () -> reader.read(file("空.txt", new byte[0])));
        assertThrows(BusinessException.class, () -> reader.read(file("图片.png", new byte[]{1})));
        assertThrows(BusinessException.class, () -> reader.read(file("长.txt", "a".repeat(60001).getBytes(StandardCharsets.UTF_8))));
        assertThrows(BusinessException.class, () -> reader.read(file("大.txt", new byte[10 * 1024 * 1024 + 1])));
        var bytes = new ByteArrayOutputStream();
        try (var doc = new PDDocument()) { doc.addPage(new PDPage()); doc.save(bytes); }
        assertThrows(BusinessException.class, () -> reader.read(file("扫描.pdf", bytes.toByteArray())));
    }
}
