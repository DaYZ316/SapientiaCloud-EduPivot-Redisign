package com.dayz.sc.storage.util;

import com.dayz.sc.storage.model.enums.StorageUsage;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;

/**
 * MinIO 对象路径构建工具。
 * <p>
 * 临时路径：{@code temp/{objectId}.{ext}} — 上传阶段使用，由 MinIO 生命周期规则自动过期。
 * 最终路径：{@code {usage}/{scopeId}/{year}/{objectId}.{ext}} — completeUpload 后永久存储
 *
 * @author DaYZ
 * @since 2026-06-11
 */
public final class StorageObjectKeyBuilder {

    private static final String SAFE_EXTENSION_PATTERN = "[a-z0-9]{1,12}";

    private StorageObjectKeyBuilder() {
    }

    /**
     * 构建临时上传路径。
     */
    public static String tempKey(UUID objectId, String fileName, String contentType) {
        return "temp/" + objectId + extensionFor(fileName, contentType);
    }

    /**
     * 构建最终存储路径。
     */
    public static String finalKey(StorageUsage usage, UUID scopeId, UUID objectId,
                                  String fileName, String contentType) {
        String scope = scopeId == null ? "global" : scopeId.toString();
        String extension = extensionFor(fileName, contentType);
        return usage.name().toLowerCase(Locale.ROOT).replace('_', '-')
                + "/" + scope
                + "/" + Instant.now().atZone(ZoneId.systemDefault()).getYear()
                + "/" + objectId + extension;
    }

    /**
     * 从文件名或 Content-Type 推断扩展名。
     */
    public static String extensionFor(String fileName, String contentType) {
        if (fileName != null) {
            String clean = fileName.replace('\\', '/');
            int slash = clean.lastIndexOf('/');
            String base = slash >= 0 ? clean.substring(slash + 1) : clean;
            int dot = base.lastIndexOf('.');
            if (dot >= 0 && dot < base.length() - 1) {
                String ext = base.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ext.matches(SAFE_EXTENSION_PATTERN)) {
                    return "." + ext;
                }
            }
        }
        return switch (contentType == null ? "" : contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "application/zip", "application/x-zip-compressed" -> ".zip";
            case "application/json" -> ".json";
            case "text/csv" -> ".csv";
            case "text/markdown" -> ".md";
            case "text/plain" -> ".txt";
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx";
            case "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> ".pptx";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx";
            default -> "";
        };
    }
}
