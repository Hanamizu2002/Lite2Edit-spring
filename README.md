# Lite2Edit Web Service

Lite2Edit Web Service 是一个基于 Spring Boot 的 RESTful API，提供将 `.litematic` 文件转换为 `.schem` 文件的功能，并支持将转换后的文件上传到远程服务器。

该项目基于 [GoldenDelicios/Lite2Edit](https://github.com/GoldenDelicios/Lite2Edit) 项目，提供了更易于集成的 Web API 解决方案。

---

## 功能特点

- 将 `.litematic` 文件转换为 WorldEdit 兼容的 `.schem` 文件。
- 通过 HTTP 接口上传转换后的 `.schem` 文件到指定服务器。
- 自动删除转换后的本地文件，节省存储空间。

---

## API 说明

### 1. 文件上传与转换

**接口地址：**

POST /api/v1/convert

**请求参数：**

| 参数名  | 类型          | 说明                    | 必填 |
|------|-------------|-----------------------|----|
| file | `multipart` | 需要转换的 `.litematic` 文件 | 是  |

**请求示例：**

```bash
curl --location --request POST 'http://localhost:8080/api/v1/convert' \
--form 'file=@/path/to/file.litematic'
```

响应示例：
```json
{
  "download_key": "db6186c8795740379d26fc61ecba1a24",
  "delete_key": "11561161dffe4a1298992ce063be5ff9"
}
```

响应字段说明：

| 字段           | 类型     | 说明           |
|--------------|--------|--------------|
| download_key | String | 远程服务器返回的下载密钥 |
| delete_key   | String | 远程服务器返回的删除密钥 |

# 配置文件

在 application.properties 或 application.yml 中，用户需要配置上传服务器的地址，例如：

application.properties 示例：

```properties
arkitektonika.url=http://localhost:3000/upload
```
# 引用项目

本项目使用了以下开源库：
- Lite2Edit by GoldenDelicios - 原始的文件转换工具。
- Arkitektonika - 用于处理schem文件到云端。
- Jkson - 用于解析 JSON 数据。
- Spring Boot - 作为 REST API 服务框架。
- Apache HttpClient - 用于处理 HTTP 文件上传。

# 运行项目

1. 使用 Maven 构建

mvn clean package

2. 运行应用

java -jar target/lite2edit-web-service-1.0.0.jar

# 贡献

欢迎提交 issues 或 pull requests 以改进本项目。如果你有任何疑问或建议，请联系项目维护者。

# 许可证

本项目遵循 MIT 许可证，详细信息请查看 LICENSE 文件。
