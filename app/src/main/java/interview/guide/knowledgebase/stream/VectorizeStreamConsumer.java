package interview.guide.knowledgebase.stream;

import interview.guide.knowledgebase.service.KnowledgeBaseParseService;
import interview.guide.knowledgebase.service.KnowledgeBaseVectorizeService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLiveObjectClient;
import org.redisson.api.RStream;
import org.redisson.client.codec.Codec;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "stream.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class VectorizeStreamConsumer {

    private final RStream<Object, Object> vectorizeStream;
    private final KnowledgeBaseParseService parseService;
    private final KnowledgeBaseVectorizeService vectorizeService;

    @Value("${redis.stream.vectorize.name:vectorize}")
    private String streamName;

    @Value("${redis.stream.consumer.group:vectorize-consumers}")
    private String groupName;

    @Value("${redis.stream.consumer.name:consumer-1}")
    private String consumerName;

    private ExecutorService executorService;
    private volatile boolean running = false;

    @PostConstruct
    public void start() {
        executorService = Executors.newSingleThreadExecutor();
        running = true;
        executorService.submit(this::consumeTask);
        log.info("VectorizeStreamConsumer started: stream={}, group={}, consumer={}", 
                streamName, groupName, consumerName);
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (executorService != null) {
            executorService.shutdown();
        }
        log.info("VectorizeStreamConsumer stopped");
    }

    @Async
    void consumeTask() {
        while (running) {
            try {
                task().run();
            } catch (Exception e) {
                log.error("Error consuming vectorize task", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private Runnable task() {
        return () -> {
            try {
                Map<String, Object> message = (Map<String, Object>) vectorizeStream.readGroup(groupName, consumerName);
                if (message != null) {
                    processMessage(message);
                }
            } catch (Exception e) {
                log.error("Failed to read from stream", e);
            }
        };
    }

    private void processMessage(Map<String, Object> message) {
        String knowledgeBaseIdStr = (String) message.get("knowledgeBaseId");
        if (knowledgeBaseIdStr == null) {
            log.warn("Missing knowledgeBaseId in message: {}", message);
            return;
        }

        try {
            UUID knowledgeBaseId = UUID.fromString(knowledgeBaseIdStr);
            log.info("Processing vectorize task: id={}", knowledgeBaseId);

            String textContent = parseService.parseLocalFile(
                    java.io.FileSystems.getDefault().getPath(
                            "/tmp", "vectorize_" + knowledgeBaseId + ".txt")
                            .toAbsolutePath());

            vectorizeService.vectorizeDocument(knowledgeBaseId, textContent);

            log.info("Completed vectorize task: id={}", knowledgeBaseId);

        } catch (Exception e) {
            log.error("Failed to process vectorize task: id={}", knowledgeBaseIdStr, e);
        }
    }
}