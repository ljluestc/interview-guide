package interview.guide.modules.knowledgebase.stream;

import interview.guide.modules.knowledgebase.service.KnowledgeBasePersistenceService;
import interview.guide.modules.knowledgebase.service.KnowledgeBaseVectorizeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer for Redis Stream - receives vectorize tasks and processes them
 * Implements backpressure by consuming at a steady pace
 */
@Slf4j
@Component
public class VectorizeStreamConsumer {

    @Autowired
    private KnowledgeBaseVectorizeService vectorizeService;

    @Autowired
    private KnowledgeBasePersistenceService persistenceService;

    /**
     * Consume vectorize tasks from Redis Stream
     * 
     * @param task The vectorize task from the stream
     */
    @StreamListener("stream:vectorize")
    public void consume(VectorizeStreamProducer.VectorizeTask task) {
        try {
            log.info("Received vectorize task for knowledgeBaseId: {}", task.getKnowledgeBaseId());
            
            // Execute vectorize operation
            vectorizeService.vectorizeAndStore(task.getKnowledgeBaseId(), task.getContent());
            
            // Update persistence status if needed
            persistenceService.updateVectorStatus(task.getKnowledgeBaseId(), "COMPLETED");
            
            log.info("Completed vectorization for knowledgeBaseId: {}", task.getKnowledgeBaseId());
            
        } catch (Exception e) {
            log.error("Error processing vectorize task for knowledgeBaseId: {}", task.getKnowledgeBaseId(), e);
            // Optionally: update persistence status to FAILED
            try {
                persistenceService.updateVectorStatus(task.getKnowledgeBaseId(), "FAILED");
            } catch (Exception persistenceError) {
                log.error("Failed to update vectorization status for knowledgeBaseId: {}", task.getKnowledgeBaseId(), persistenceError);
            }
        }
    }
}