package com.dayz.sc.storage.service;

import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.storage.config.StorageProperties;
import com.dayz.sc.storage.model.entity.StorageObject;
import com.dayz.sc.storage.model.enums.StorageObjectStatus;
import com.dayz.sc.storage.model.vo.DownloadUrlResponse;
import com.dayz.sc.storage.repository.StorageObjectRepository;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import jakarta.annotation.PreDestroy;
import org.jodconverter.core.office.OfficeException;
import org.jodconverter.core.office.OfficeUtils;
import org.jodconverter.local.LocalConverter;
import org.jodconverter.local.office.LocalOfficeManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * .doc → .docx 文件转换服务。
 * <p>
 * 使用 JODConverter + LibreOffice 进行格式转换，转换结果缓存在 MinIO 的
 * {@code temp/converted/{fileId}.docx} 路径下，避免重复转换。
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Service
public class DocConversionService {

    private static final String CONVERTED_PREFIX = "temp/converted/";
    private static final String EXT_DOC = ".doc";
    private static final String EXT_DOCX = ".docx";
    private static final String MINIO_ERROR_NO_SUCH_KEY = "NoSuchKey";

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final StorageObjectRepository storageObjectRepository;
    private final StorageProperties storageProperties;

    private volatile LocalOfficeManager officeManager;

    public DocConversionService(MinioClient minioClient,
                                @Qualifier("presignMinioClient") MinioClient presignMinioClient,
                                StorageObjectRepository storageObjectRepository,
                                StorageProperties storageProperties) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.storageObjectRepository = storageObjectRepository;
        this.storageProperties = storageProperties;
    }

    /**
     * 将 .doc 文件转换为 .docx 并返回下载 URL。
     *
     * @param fileId 原始 .doc 文件的存储对象 ID
     * @return 转换后的 .docx 文件下载 URL
     */
    public DownloadUrlResponse convertDocToDocx(UUID fileId) {
        StorageObject object = storageObjectRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.STORAGE_OBJECT_NOT_FOUND));
        if (!StorageObjectStatus.READY.name().equals(object.getStatus())) {
            throw new BusinessException(ErrorCodes.NOT_FOUND);
        }

        String filename = object.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(EXT_DOC) || filename.toLowerCase().endsWith(EXT_DOCX)) {
            throw new BusinessException(ErrorCodes.BAD_REQUEST, "仅支持 .doc 文件转换");
        }

        String bucket = object.getBucket();
        String convertedKey = CONVERTED_PREFIX + fileId + EXT_DOCX;

        // 检查缓存：转换后的文件是否已存在
        if (objectExists(bucket, convertedKey)) {
            return new DownloadUrlResponse(presignedGetUrl(bucket, convertedKey), null);
        }

        // 下载原始 .doc 文件到临时目录
        Path tempDoc = null;
        Path tempDocx = null;
        try {
            tempDoc = Files.createTempFile("doc-convert-", ".doc");
            tempDocx = Files.createTempFile("doc-convert-", ".docx");

            try (InputStream is = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(object.getObjectKey())
                    .build())) {
                Files.copy(is, tempDoc, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }

            // 使用 JODConverter + LibreOffice 转换
            LocalConverter converter = LocalConverter.builder()
                    .officeManager(getOfficeManager())
                    .build();
            converter.convert(tempDoc.toFile()).to(tempDocx.toFile()).execute();

            // 上传转换后的 .docx 到 MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(convertedKey)
                    .stream(Files.newInputStream(tempDocx), Files.size(tempDocx), -1L)
                    .contentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    .build());

            return new DownloadUrlResponse(presignedGetUrl(bucket, convertedKey), null);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "文档转换失败: " + e.getMessage());
        } finally {
            safeDelete(tempDoc);
            safeDelete(tempDocx);
        }
    }

    private synchronized LocalOfficeManager getOfficeManager() throws OfficeException {
        if (officeManager == null || !officeManager.isRunning()) {
            officeManager = LocalOfficeManager.builder()
                    .officeHome(storageProperties.getConversion().getOfficeHome())
                    .maxTasksPerProcess(storageProperties.getConversion().getMaxTasksPerProcess())
                    .taskExecutionTimeout(storageProperties.getConversion().getTaskTimeoutMs())
                    .build();
            officeManager.start();
        }
        return officeManager;
    }

    @PreDestroy
    public void destroy() {
        if (officeManager != null) {
            OfficeUtils.stopQuietly(officeManager);
        }
    }

    private boolean objectExists(String bucket, String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .build());
            return true;
        } catch (ErrorResponseException e) {
            if (MINIO_ERROR_NO_SUCH_KEY.equals(e.errorResponse().code())) {
                return false;
            }
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "检查文件失败");
        } catch (Exception e) {
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "检查文件失败");
        }
    }

    private String presignedGetUrl(String bucket, String objectKey) {
        try {
            return presignMinioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(io.minio.Http.Method.GET)
                    .bucket(bucket)
                    .object(objectKey)
                    .expiry(storageProperties.getExpiry().getDownloadMinutes(), TimeUnit.MINUTES)
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCodes.SYSTEM_ERROR, "生成下载链接失败");
        }
    }

    private void safeDelete(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }
}
