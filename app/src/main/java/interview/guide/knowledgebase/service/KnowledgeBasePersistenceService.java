package interview.guide.knowledgebase.service;

import interview.guide.knowledgebase.entity.KnowledgeBaseEntity;
import interview.guide.knowledgebase.entity.VectorStatus;
import interview.guide.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.UUID;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBasePersistenceService {

    private final KnowledgeBaseRepository repository;

    public KnowledgeBaseEntity saveLocalKnowledgeBase(
            String name,
            String category,
            String filename,
            String fileHash,
            Long fileSize,
            String contentType
    ) {
        if (repository.existsByFileHash(fileHash)) {
            log.info("Skipping duplicate file: {}, hash: {}", filename, fileHash);
            return null;
        }

        KnowledgeBaseEntity entity = KnowledgeBaseEntity.builder()
                .name(name)
                .category(category)
                .originalFilename(filename)
                .fileHash(fileHash)
                .fileSize(fileSize)
                .contentType(contentType)
                .vectorStatus(VectorStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        KnowledgeBaseEntity saved = repository.save(entity);
        log.info("Created knowledge base entity: id={}, name={}", saved.getId(), name);
        return saved;
    }

    public KnowledgeBaseEntity updateVectorStatus(UUID id, VectorStatus status) {
        KnowledgeBaseEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + id));
        entity.setVectorStatus(status);
        entity.setUpdatedAt(LocalDateTime.now());
        return repository.save(entity);
    }

    public KnowledgeBaseEntity updateChunkCount(UUID id, int chunkCount) {
        KnowledgeBaseEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + id));
        entity.setChunkCount(chunkCount);
        entity.setVectorStatus(VectorStatus.COMPLETED);
        entity.setUpdatedAt(LocalDateTime.now());
        return repository.save(entity);
    }

    public void markAsFailed(UUID id, String errorMessage) {
        KnowledgeBaseEntity entity = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + id));
        entity.setVectorStatus(VectorStatus.FAILED);
        entity.setErrorMessage(errorMessage);
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        log.error("Marked as failed: id={}, error={}", id, errorMessage);
    }
}