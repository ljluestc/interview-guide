package interview.guide.knowledgebase.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBaseParseService {

    private final ObjectMapper objectMapper;

    public String parseLocalFile(Path filePath) throws Exception {
        String fileName = filePath.getFileName().toString();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        return switch (extension) {
            case "jsonl" -> parseJsonlFile(filePath);
            case "pdf", "docx", "doc", "txt" -> parseTikaDocument(filePath);
            default -> throw new IllegalArgumentException("Unsupported file type: " + extension);
        };
    }

    private String parseJsonlFile(Path filePath) throws Exception {
        StringBuilder content = new StringBuilder();
        String lineContent;

        try (var reader = Files.newBufferedReader(filePath)) {
            while ((lineContent = reader.readLine()) != null) {
                try {
                    Map<String, Object> json = objectMapper.readValue(lineContent, Map.class);
                    Object question = json.get("question");
                    Object answer = json.get("answer");
                    Object keyPoints = json.get("key_points");

                    content.append("Q: ").append(question != null ? question.toString() : "").append("\n");
                    content.append("A: ").append(answer != null ? answer.toString() : "").append("\n");
                    if (keyPoints != null) {
                        content.append("Key Points: ").append(keyPoints.toString()).append("\n");
                    }
                    content.append("\n---\n\n");
                } catch (Exception e) {
                    log.warn("Failed to parse JSONL line: {}", lineContent);
                }
            }
        }

        return content.toString();
    }

    private String parseTikaDocument(Path filePath) throws Exception {
        Parser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (InputStream stream = Files.newInputStream(filePath)) {
            parser.parse(stream, handler, metadata, context);
        } catch (TikaException e) {
            log.error("Tika parsing failed for file: {}", filePath, e);
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }

        String contentType = metadata.get("Content-Type");
        log.info("Parsed document: {}, type: {}", filePath, contentType);

        return handler.toString();
    }
}