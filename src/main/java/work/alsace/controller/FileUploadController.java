package work.alsace.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import work.alsace.service.ConverterService;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;

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
            // 检查文件是否为空
            if (file.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "上传的文件为空，请上传有效的Litematic文件。"));
            }

            // 检查文件类型（可选）
            if (!Objects.requireNonNull(file.getOriginalFilename()).endsWith(".litematic")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "您上传的文件不是有效的Litematic文件，请上传正确格式的文件。"));
            }

            // 限制文件大小（如50MB）
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                        .body(Map.of("error", "文件过大，请尝试分块并重新上传。"));
            }
            // 保存上传的文件
            File uploadedFile = Paths.get("uploads", file.getOriginalFilename()).toFile();
            FileUtils.writeByteArrayToFile(uploadedFile, file.getBytes());

            // 进行转换
            File convertedFile = converterService.convertLitematic(uploadedFile);

            // 上传到远程服务器
            String jsonResponse = converterService.uploadToRemoteServer(convertedFile).get("response");

            // 使用 TypeReference 解析 JSON 字符串为 Map<String, String>
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> responseMap = objectMapper.readValue(jsonResponse, new TypeReference<>() {
            });

            return ResponseEntity.ok(responseMap);
        } catch (Exception e) {
            // 处理特定异常
            if (e.getMessage().contains("disk space")) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "服务器存储空间不足，无法保存您的文件，请稍后重试。"));
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "服务器发生错误，请联系开发人员。错误详情：" + e.getMessage()));
        }
    }
}