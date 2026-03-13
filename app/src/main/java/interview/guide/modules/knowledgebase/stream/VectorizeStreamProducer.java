package interview.guide.modules.knowledgebase.stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Producer for Redis Stream - publishes vectorize tasks to "stream:vectorize"
 * Enables asynchronous vectorization of large documents via message queues
 */
@Component
public class VectorizeStreamProducer {

    @Autowired
    private StreamBridge<String, VectorizeTask> streamBridge;

    /**
     * Publish a vectorize task to the stream for asynchronous processing
     * 
     * @param knowledgeBaseId The ID of the knowledge base
     * @param content The content to vectorize
     */
    public void publishVectorizeTask(Long knowledgeBaseId, String content) {
        VectorizeTask task = new VectorizeTask();
        task.setKnowledgeBaseId(knowledgeBaseId);
        task.setContent(content);

        // Publish to "stream:vectorize" stream
        streamBridge.send("stream:vectorize", task);

        System.out.println("Published vectorize task to stream for knowledgeBaseId: " + knowledgeBaseId);
    }

    /**
     * Task description for the Redis Stream
     */
    public static class VectorizeTask {
        private Long knowledgeBaseId;
        private String content;

        // Getters and Setters
        public Long getKnowledgeBaseId() {
            return knowledgeBaseId;
        }

        public void setKnowledgeBaseId(Long knowledgeBaseId) {
            this.knowledgeBaseId = knowledgeBaseId;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            String contentPreview = content != null ? content.substring(0, Math.min(50, content.length())) + "..." : "null";
            return "VectorizeTask{" +
                   "knowledgeBaseId=" + knowledgeBaseId +
                   ", content='" + contentPreview +
                   '}';
        }
    }
}