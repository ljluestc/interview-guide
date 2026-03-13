package interview.guide.knowledgebase.repository;

import interview.guide.knowledgebase.entity.KnowledgeBaseEntity;
import interview.guide.knowledgebase.entity.VectorStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

public interface KnowledgeBaseRepository extends Repository<KnowledgeBaseEntity, UUID> {

    boolean existsByFileHash(String fileHash);

    Optional<KnowledgeBaseEntity> findById(UUID id);

    KnowledgeBaseEntity save(KnowledgeBaseEntity entity);

    @Modifying
    @Transactional
    void deleteById(UUID id);

    @Query("SELECT kb FROM KnowledgeBaseEntity kb WHERE kb.vectorStatus = :status")
    Iterable<KnowledgeBaseEntity> findByVectorStatus(VectorStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE KnowledgeBaseEntity kb SET kb.vectorStatus = :status WHERE kb.id = :id")
    int updateVectorStatusById(UUID id, VectorStatus status);
}