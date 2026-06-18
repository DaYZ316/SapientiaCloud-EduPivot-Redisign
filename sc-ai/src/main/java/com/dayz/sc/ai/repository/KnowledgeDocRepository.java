package com.dayz.sc.ai.repository;

import com.dayz.sc.ai.model.entity.KnowledgeDoc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KnowledgeDocRepository {

    void save(KnowledgeDoc doc);

    Optional<KnowledgeDoc> findByIdAndUserId(UUID id, UUID userId);

    List<KnowledgeDoc> findByUserId(UUID userId, int page, int size);

    void update(KnowledgeDoc doc);

    boolean deleteByIdAndUserId(UUID id, UUID userId);
}
