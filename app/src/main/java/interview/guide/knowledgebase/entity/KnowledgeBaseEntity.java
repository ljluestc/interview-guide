package interview.guide.knowledgebase.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "knowledge_base", indexes = {
    @Index(name = "idx_file_hash", columnList = "file_hash"),
    @Index(name = "idx_vector_status", columnList = "vector_status")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(nullable = false, length = 200)
    private String category;

    @Column(length = 500, name = "original_filename")
    private String originalFilename;

    @Column(name = "file_hash", unique = true, nullable = false, length = 64)
    private String fileHash;

    @Column(nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(length = 500, name = "storage_key")
    private String storageKey;

    @Column(length = 1000, name = "storage_url")
    private String storageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private VectorStatus vectorStatus = VectorStatus.PENDING;

    @Column(name = "chunk_count")
    private Integer chunkCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    @Column(length = 5000, columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 50)
    private String embeddingModel;

    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;
}