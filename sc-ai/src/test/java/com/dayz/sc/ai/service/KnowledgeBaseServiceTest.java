package com.dayz.sc.ai.service;

import com.dayz.sc.ai.config.AiProperties;
import com.dayz.sc.ai.model.entity.KnowledgeDoc;
import com.dayz.sc.ai.repository.KnowledgeDocRepository;
import com.dayz.sc.common.feign.client.StorageInternalClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class KnowledgeBaseServiceTest {

    @Test
    @SuppressWarnings("unchecked")
    void deleteShouldHideDocumentEvenWhenVectorDeletionFails() {
        KnowledgeDocRepository knowledgeDocRepository = mock(KnowledgeDocRepository.class);
        StorageInternalClient storageInternalClient = mock(StorageInternalClient.class);
        ObjectProvider<@org.jspecify.annotations.NonNull VectorStore> vectorStoreProvider = mock(ObjectProvider.class);
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStoreProvider.getObject()).thenReturn(vectorStore);

        KnowledgeBaseService service = new KnowledgeBaseService(
                knowledgeDocRepository,
                storageInternalClient,
                vectorStoreProvider,
                new AiProperties(),
                new AiProviderCallGuard(),
                "idx",
                "prefix:");
        UUID docId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        KnowledgeDoc doc = new KnowledgeDoc();
        doc.setId(docId);
        doc.setUserId(userId);
        when(knowledgeDocRepository.findByIdAndUserId(docId, userId)).thenReturn(Optional.of(doc));
        when(knowledgeDocRepository.deleteByIdAndUserId(docId, userId)).thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("redis down")).when(vectorStore).delete(anyString());

        assertThatCode(() -> service.delete(docId, userId)).doesNotThrowAnyException();

        verify(knowledgeDocRepository).update(doc);
        verify(knowledgeDocRepository).deleteByIdAndUserId(docId, userId);
        verify(vectorStore).delete(anyString());
    }
}
