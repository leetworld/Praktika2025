package ru.jd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.springframework.web.multipart.MultipartFile;

import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.convert.out.pdf.PdfConversion;
import org.docx4j.convert.out.pdf.viaXSLFO.Conversion;
import org.docx4j.convert.out.pdf.viaXSLFO.PdfSettings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;

public class ConverterService {
    public String convert(MultipartFile file, String targetFormat) throws Exception {
        String ext = getExtension(file.getOriginalFilename());
        // DOC to PDF
        if ("doc".equals(ext) && "pdf".equals(targetFormat)) {
            return convertDocToPdf(file);
        }
        // DOCX to PDF
        if ("docx".equals(ext) && "pdf".equals(targetFormat)) {
            return convertDocxToPdf(file);
        }
        // PDF to DOCX
        if ("pdf".equals(ext) && ("docx".equals(targetFormat) || "doc".equals(targetFormat))) {
            return convertPdfToDocx(file);
        }
        // PNG to JPEG
        if ("png".equals(ext) && ("jpeg".equals(targetFormat) || "jpg".equals(targetFormat))) {
            return convertPngToJpeg(file);
        }
        String input = new String(file.getBytes());
        ObjectMapper sourceMapper = getMapper(ext);
        ObjectMapper targetMapper = getMapper(targetFormat);
        if (sourceMapper == null || targetMapper == null) {
            throw new IllegalArgumentException("Unsupported format: " + ext + " or " + targetFormat);
        }
        JsonNode tree = sourceMapper.readTree(input);
        if (targetMapper instanceof XmlMapper) {
            return ((XmlMapper) targetMapper).writer().withRootName("root").writeValueAsString(tree);
        }
        return targetMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tree);
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "json";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private ObjectMapper getMapper(String ext) {
        switch (ext) {
            case "json":
                return new ObjectMapper();
            case "xml":
                return new XmlMapper();
            case "yml":
            case "yaml":
                return new YAMLMapper();
            default:
                return null;
        }
    }

    private String convertDocToPdf(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             HWPFDocument doc = new HWPFDocument(is);
             PDDocument pdfDoc = new PDDocument()) {
            WordExtractor extractor = new WordExtractor(doc);
            String text = extractor.getText().replace("\t", "    ");
            PDPage page = new PDPage(PDRectangle.A4);
            pdfDoc.addPage(page);
            // Загружаем TTF-шрифт
            InputStream fontStream = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
            PDType0Font font = PDType0Font.load(pdfDoc, fontStream);
            PDPageContentStream contentStream = new PDPageContentStream(pdfDoc, page);
            contentStream.setFont(font, 12);
            contentStream.beginText();
            contentStream.newLineAtOffset(50, 750);
            for (String line : text.split("\n")) {
                contentStream.showText(line);
                contentStream.newLineAtOffset(0, -15);
            }
            contentStream.endText();
            contentStream.close();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            pdfDoc.save(baos);
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private String convertDocxToPdf(MultipartFile file) throws Exception {
        // Попытка через docx4j
        try (InputStream is = file.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            WordprocessingMLPackage wordMLPackage = WordprocessingMLPackage.load(is);
            PdfConversion conversion = new Conversion(wordMLPackage);
            conversion.output(baos, new org.docx4j.convert.out.pdf.viaXSLFO.PdfSettings());
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            // Fallback на старую реализацию (только текст)
            try (InputStream is = file.getInputStream();
                 XWPFDocument docx = new XWPFDocument(is);
                 PDDocument pdfDoc = new PDDocument()) {
                StringBuilder text = new StringBuilder();
                docx.getParagraphs().forEach(p -> text.append(p.getText()).append("\n"));
                String textStr = text.toString().replace("\t", "    ");
                PDPage page = new PDPage(PDRectangle.A4);
                pdfDoc.addPage(page);
                InputStream fontStream = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
                PDType0Font font = PDType0Font.load(pdfDoc, fontStream);
                PDPageContentStream contentStream = new PDPageContentStream(pdfDoc, page);
                contentStream.setFont(font, 12);
                contentStream.beginText();
                contentStream.newLineAtOffset(50, 750);
                for (String line : textStr.split("\n")) {
                    contentStream.showText(line);
                    contentStream.newLineAtOffset(0, -15);
                }
                contentStream.endText();
                contentStream.close();
                ByteArrayOutputStream fallbackBaos = new ByteArrayOutputStream();
                pdfDoc.save(fallbackBaos);
                return java.util.Base64.getEncoder().encodeToString(fallbackBaos.toByteArray());
            }
        }
    }

    private String convertPdfToDocx(MultipartFile file) throws Exception {
        try (PDDocument pdfDoc = PDDocument.load(file.getInputStream());
             XWPFDocument docx = new XWPFDocument()) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdfDoc);
            for (String line : text.split("\r?\n")) {
                XWPFParagraph para = docx.createParagraph();
                para.createRun().setText(line);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            docx.write(baos);
            return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
        }
    }

    private String convertPngToJpeg(MultipartFile file) throws Exception {
        BufferedImage image = ImageIO.read(file.getInputStream());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", baos);
        return java.util.Base64.getEncoder().encodeToString(baos.toByteArray());
    }
} 