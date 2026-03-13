package interview.guide.knowledgebase.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    @Bean
    public RedissonClient redissonClient(RedissonConnectionFactory connectionFactory) {
        return connectionFactory.getRedisson();
    }

    @Bean
    public org.redisson.api.RStream<Object, Object> vectorizeStream(RedissonClient redissonClient) {
        return redissonClient.<Object, Object>getStream("vectorize");
    }

    @Bean
    public JsonJacksonCodec jsonJacksonCodec(ObjectMapper objectMapper) {
        return new JsonJacksonCodec(objectMapper);
    }
}