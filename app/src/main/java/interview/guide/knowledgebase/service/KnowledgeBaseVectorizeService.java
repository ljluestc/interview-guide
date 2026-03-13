package interview.guide.knowledgebase.service;

import interview.guide.knowledgebase.entity.KnowledgeBaseEntity;
import interview.guide.knowledgebase.entity.VectorStatus;
import interview.guide.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBaseVectorizeService {

    private final KnowledgeBaseRepository repository;
    private final KnowledgeBasePersistenceService persistenceService;
    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;

    private static final int CHUNK_SIZE = 800;
    private static final int CHUNK_OVERLAP = 100;
    private static final int BATCH_SIZE = 10;

    public int vectorizeDocument(UUID knowledgeBaseId, String textContent) {
        log.info("Vectorizing document: id={}", knowledgeBaseId);

        try {
            KnowledgeBaseEntity entity = repository.findById(knowledgeBaseId)
                    .orElseThrow(() -> new IllegalArgumentException("Entity not found: " + knowledgeBaseId));

            persistenceService.updateVectorStatus(knowledgeBaseId, VectorStatus.PROCESSING);

            // Split text into chunks using Spring AI TextReader
            TextReader textReader = new TextReader(textContent);
            List<Document> documents = textReader.get();
            
            // Manually chunk documents if needed
            List<Document> chunkedDocs = new java.util.ArrayList<>();
            String fullText = textContent;
            int offset = 0;
            while (offset < fullText.length()) {
                int end = Math.min(offset + CHUNK_SIZE, fullText.length());
                String chunk = fullText.substring(offset, end);
                chunkedDocs.add(new Document(chunk, metadata));
                offset += (CHUNK_SIZE - CHUNK_OVERLAP);
                if (offset < 0) offset = 0;
            }
            documents = chunkedDocs;
            List<Document> documents = splitter.split(new Document(textContent));

            int chunkCount = documents.size();
            log.info("Document split into {} chunks", chunkCount);

            List<Document> documentsWithMetadata = new java.util.ArrayList<>();
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("knowledgeBaseId", knowledgeBaseId.toString());
            metadata.put("category", entity.getCategory());
            metadata.put("name", entity.getName());

            for (Document doc : documents) {
                Document enhanced = new Document(doc.getText(), metadata);
                enhanced.getMetadata().putAll(doc.getMetadata());
                documentsWithMetadata.add(enhanced);
            }

            for (int i = 0; i < documentsWithMetadata.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, documentsWithMetadata.size());
                List<Document> batch = documentsWithMetadata.subList(i, end);
                log.debug("Adding batch {}/{} ({} documents)", i / BATCH_SIZE + 1,
                         (documentsWithMetadata.size() + BATCH_SIZE - 1) / BATCH_SIZE, batch.size());
                vectorStore.add(batch);
            }

            persistenceService.updateChunkCount(knowledgeBaseId, chunkCount);
            entity.setEmbeddingModel("DashScope text-embedding-v3");
            entity.setEmbeddingDimension(1024);
            repository.save(entity);

            log.info("Vectorization completed: id={}, chunks={}", knowledgeBaseId, chunkCount);
            return chunkCount;

        } catch (Exception e) {
            log.error("Vectorization failed: id={}", knowledgeBaseId, e);
            persistenceService.markAsFailed(knowledgeBaseId, e.getMessage());
            throw new RuntimeException("Vectorization failed", e);
        }
    }

    @Data
    public static class VectorizeResult {
        private UUID knowledgeBaseId;
        private int chunkCount;
        private VectorStatus status;
        private String errorMessage;
    }
}