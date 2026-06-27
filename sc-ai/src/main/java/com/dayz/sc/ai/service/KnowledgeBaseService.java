package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.KnowledgeDoc;
import com.dayz.sc.ai.model.enums.DocStatus;
import com.dayz.sc.ai.model.vo.KnowledgeDocVO;
import com.dayz.sc.ai.repository.KnowledgeDocRepository;
import com.dayz.sc.common.error.BusinessException;
import com.dayz.sc.common.error.ErrorCodes;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import com.dayz.sc.common.feign.dto.StorageObjectInfo;
import com.dayz.sc.common.response.ApiResponse;
import com.dayz.sc.common.util.UuidV7Generator;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 知识库管理：拉取 sc-storage 文件 → Tika 解析 → 切分 → 写入向量库
 *
 * @author DaYZ
 * @since 2026-06-16
 */
@Slf4j
@Service
public class KnowledgeBaseService {

    /**
     * 向量库文档元数据键：所属用户
     */
    public static final String META_USER_ID = "userId";
    /**
     * 向量库文档元数据键：所属知识库文档
     */
    public static final String META_DOC_ID = "docId";
    public static final String META_SOURCE_TYPE = "sourceType";
    public static final String META_SOURCE_TYPE_KNOWLEDGE_DOC = "KNOWLEDGE_DOC";

    private final KnowledgeDocRepository knowledgeDocRepository;
    private final StorageInternalClient storageInternalClient;
    private final ObjectProvider<@NonNull VectorStore> vectorStoreProvider;
    private final AiProperties aiProperties;
    private final AiProviderCallGuard aiProviderCallGuard;
    private final String vectorIndexName;
    private final String vectorPrefix;

    /**
     * vectorStore 以 {@link ObjectProvider} 延迟获取：避免在缺少 API Key 时因向量库
     * schema 初始化（需调用 embedding）而拖垮整个服务启动
     */
    public KnowledgeBaseService(KnowledgeDocRepository knowledgeDocRepository,
                                StorageInternalClient storageInternalClient,
                                ObjectProvider<@NonNull VectorStore> vectorStoreProvider,
                                AiProperties aiProperties,
                                AiProviderCallGuard aiProviderCallGuard,
                                @Value("${spring.ai.vectorstore.redis.index-name:edupivot-ai-idx}") String vectorIndexName,
                                @Value("${spring.ai.vectorstore.redis.prefix:edupivot:ai:vec:}") String vectorPrefix) {
        this.knowledgeDocRepository = knowledgeDocRepository;
        this.storageInternalClient = storageInternalClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.aiProperties = aiProperties;
        this.aiProviderCallGuard = aiProviderCallGuard;
        this.vectorIndexName = vectorIndexName;
        this.vectorPrefix = vectorPrefix;
    }

    /**
     * 同步入库：拉取文件、解析、切分、向量化并写入返回文档记录 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public UUID ingest(UUID storageObjectId, UUID userId) {
        StorageObjectInfo info = unwrap(storageInternalClient.getFile(storageObjectId));
        String downloadUrl = unwrap(storageInternalClient.getDownloadUrl(storageObjectId));

        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(UuidV7Generator.generate());
        doc.setUserId(userId);
        doc.setStorageObjectId(storageObjectId);
        doc.setFilename(info.fileName());
        doc.setStatus(DocStatus.PENDING.name());
        doc.setChunkCount(0);
        knowledgeDocRepository.save(doc);

        try {
            List<Document> chunks = parseAndSplit(downloadUrl);
            for (Document chunk : chunks) {
                chunk.getMetadata().put(META_USER_ID, userId.toString());
                chunk.getMetadata().put(META_DOC_ID, doc.getId().toString());
                chunk.getMetadata().put(META_SOURCE_TYPE, META_SOURCE_TYPE_KNOWLEDGE_DOC);
            }
            aiProviderCallGuard.run(() -> vectorStoreProvider.getObject().add(chunks));

            doc.setChunkCount(chunks.size());
            doc.setStatus(DocStatus.INDEXED.name());
            knowledgeDocRepository.update(doc);
            log.info("AI knowledge document indexed docId={} storageObjectId={} chunkCount={} vectorIndex={} vectorPrefix={}",
                    doc.getId(), storageObjectId, chunks.size(), vectorIndexName, vectorPrefix);
            return doc.getId();
        } catch (Exception e) {
            log.error("知识库文档入库失败 docId={}, storageObjectId={}", doc.getId(), storageObjectId, e);
            doc.setStatus(DocStatus.FAILED.name());
            doc.setErrorMessage(e.getMessage());
            knowledgeDocRepository.update(doc);
            throw new BusinessException(ErrorCodes.AI_DOCUMENT_PROCESS_FAILED);
        }
    }

    public List<KnowledgeDocVO> list(UUID userId, int page, int size) {
        return knowledgeDocRepository.findByUserId(userId, page, size).stream()
                .map(d -> new KnowledgeDocVO(
                        d.getId(),
                        d.getFilename(),
                        d.getStatus(),
                        d.getChunkCount() == null ? 0 : d.getChunkCount(),
                        d.getCreatedAt()))
                .toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(UUID docId, UUID userId) {
        KnowledgeDoc doc = knowledgeDocRepository.findByIdAndUserId(docId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCodes.AI_KNOWLEDGE_DOC_NOT_FOUND));
        doc.setDeletedAt(Instant.now());
        knowledgeDocRepository.update(doc);
        knowledgeDocRepository.deleteByIdAndUserId(docId, userId);
        try {
            vectorStoreProvider.getObject().delete("%s == '%s' && %s == '%s' && %s == '%s'"
                    .formatted(META_SOURCE_TYPE, META_SOURCE_TYPE_KNOWLEDGE_DOC, META_DOC_ID, docId, META_USER_ID, userId));
        } catch (Exception e) {
            log.warn("AI knowledge document vector deletion failed docId={} userId={}", docId, userId, e);
        }
    }

    private List<Document> parseAndSplit(String downloadUrl) throws Exception {
        UrlResource resource = new UrlResource(URI.create(downloadUrl));
        TikaDocumentReader reader = new TikaDocumentReader(resource);
        List<Document> documents = reader.get();
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(aiProperties.getKnowledgeBase().getChunkSize())
                .build();
        return splitter.apply(documents);
    }

    private <T> T unwrap(ApiResponse<@NonNull T> response) {
        if (response == null || response.code() != 0 || response.data() == null) {
            throw new BusinessException(ErrorCodes.STORAGE_OBJECT_NOT_FOUND);
        }
        return response.data();
    }
}
