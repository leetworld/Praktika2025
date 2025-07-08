package ru.jd;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;

@Controller
public class ConverterController {
    private final ConverterService converterService = new ConverterService();

    @GetMapping("/")
    public String index() {
        return "redirect:/index.html";
    }

    @PostMapping("/convert")
    public ResponseEntity<byte[]> convertFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("targetFormat") String targetFormat,
            RedirectAttributes redirectAttributes
    ) {
        try {
            String ext = targetFormat.toLowerCase();
            String result = converterService.convert(file, targetFormat);
            String filename = file.getOriginalFilename();
            String outName = (filename != null ? filename.replaceAll("\\.[^.]+$", "") : "converted") + "." + targetFormat;
            byte[] data;
            if (("pdf".equals(ext) || "jpeg".equals(ext) || "jpg".equals(ext))) {
                data = java.util.Base64.getDecoder().decode(result);
            } else if ("docx".equals(ext)) {
                data = java.util.Base64.getDecoder().decode(result);
            } else {
                data = result.getBytes(StandardCharsets.UTF_8);
            }
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if ("pdf".equals(ext)) mediaType = MediaType.APPLICATION_PDF;
            if ("jpeg".equals(ext) || "jpg".equals(ext)) mediaType = MediaType.IMAGE_JPEG;
            if ("docx".equals(ext)) mediaType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            // Content-Disposition с поддержкой UTF-8
            String asciiName = outName.replaceAll("[^A-Za-z0-9. _-]", "_");
            String encodedName = URLEncoder.encode(outName, "UTF-8").replaceAll("\\+", "%20");
            String contentDisposition = String.format(
                "attachment; filename=\"%s\"; filename*=UTF-8''%s",
                asciiName, encodedName
            );
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .contentType(mediaType)
                    .body(data);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка конвертации: " + e.getMessage());
            return ResponseEntity.badRequest().body(("Ошибка: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
} 