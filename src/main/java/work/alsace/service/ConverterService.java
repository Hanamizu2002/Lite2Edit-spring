package work.alsace.service;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * 服务类，提供 Litematica 文件的转换和远程上传功能
 */
@Service
public class ConverterService {

    // 从配置文件（application.properties 或 application.yml）中获取上传服务器的 URL
    @Value("${arkitektonika.url}")
    private String uploadUrl;

    /**
     * 将 .litematic 文件转换为 WorldEdit 兼容的 .schem 文件
     *
     * @param inputFile 需要转换的 Litematica 文件
     * @return 转换后的 Schematic 文件
     * @throws IOException 当转换失败或输出为空时抛出异常
     */
    public File convertLitematic(File inputFile) throws IOException {
        // 创建存放转换后文件的目录
        File outputDir = new File("converted");
        if (!outputDir.exists()) {
            outputDir.mkdirs(); // 如果目录不存在，则创建
        }

        // 调用 Converter 工具类执行转换，将 litematic 转换为 schem
        List<File> outputFiles = Converter.litematicToWorldEdit(inputFile, outputDir);

        // 如果转换后的文件列表为空，抛出异常
        if (outputFiles.isEmpty()) {
            throw new IOException("Conversion failed or produced no output.");
        }

        // 假设转换后只生成一个文件，返回该文件
        return outputFiles.get(0);
    }

    /**
     * 上传已转换的 .schem 文件到远程服务器
     *
     * @param schematicFile 需要上传的 Schematic 文件
     * @return 服务器返回的响应信息，包含下载和删除密钥
     * @throws IOException 当上传失败时抛出异常
     */
    public Map<String, String> uploadToRemoteServer(File schematicFile) throws IOException {
        // 创建 HTTP POST 请求对象，目标地址从配置文件中获取
        HttpPost post = new HttpPost(uploadUrl);

        // 构建 HTTP Multipart 表单，上传文件的请求体
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.addBinaryBody(
                "schematic",                                   // 请求参数名称
                new FileInputStream(schematicFile),            // 文件输入流
                org.apache.http.entity.ContentType.APPLICATION_OCTET_STREAM, // 指定内容类型
                schematicFile.getName()                        // 设置文件名
        );

        // 构建最终的请求体
        HttpEntity multipart = builder.build();
        post.setEntity(multipart);

        // 发送请求并获取响应
        try (CloseableHttpResponse response = HttpClients.createDefault().execute(post)) {
            // 读取响应内容为字符串
            String responseString = EntityUtils.toString(response.getEntity());

            // 解析 JSON 响应并存入 Map 结构
            Map<String, String> responseMap = new HashMap<>();
            responseMap.put("response", responseString);

            // 上传成功后删除本地文件
            if (schematicFile.exists()) {
                if (schematicFile.delete()) {
                    System.out.println("文件删除成功: " + schematicFile.getAbsolutePath());
                } else {
                    System.err.println("文件删除失败: " + schematicFile.getAbsolutePath());
                }
            }

            return responseMap;  // 返回解析后的响应数据
        }
    }
}