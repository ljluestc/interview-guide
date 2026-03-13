package interview.guide.knowledgebase.stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RStream;
import org.redisson.codec.JsonJacksonCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VectorizeStreamProducer {

    private final RStream<Object, Object> vectorizeStream;
    private final ObjectMapper objectMapper;

    @Value("${redis.stream.vectorize.name:vectorize}")
    private String streamName;

    public void sendVectorizeTask(UUID knowledgeBaseId) {
        Map<String, Object> task = Map.of(
                "knowledgeBaseId", knowledgeBaseId.toString(),
                "timestamp", System.currentTimeMillis()
        );

        try {
            vectorizeStream.add(task);
            log.debug("Sent vectorize task to stream: id={}", knowledgeBaseId);
        } catch (Exception e) {
            log.error("Failed to send vectorize task: id={}", knowledgeBaseId, e);
            throw new RuntimeException("Failed to enqueue vectorize task", e);
        }
    }
}