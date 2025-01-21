package work.alsace.controller;

import org.apache.commons.io.FileUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import work.alsace.service.ConverterService;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class FileUploadController {

    private final ConverterService converterService;

    public FileUploadController(ConverterService converterService) {
        this.converterService = converterService;
    }

    @PostMapping("/convert")
    public ResponseEntity<Map<String, String>> convertFile(@RequestParam("file") MultipartFile file) {
        try {
            // 保存上传的文件
            File uploadedFile = Paths.get("uploads", file.getOriginalFilename()).toFile();
            FileUtils.writeByteArrayToFile(uploadedFile, file.getBytes());

            // 进行转换
            File convertedFile = converterService.convertLitematic(uploadedFile);

            // 上传到远程服务器
            Map<String, String> response = converterService.uploadToRemoteServer(convertedFile);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}