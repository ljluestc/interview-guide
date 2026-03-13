package interview.guide.knowledgebase.service;

import interview.guide.knowledgebase.stream.VectorizeStreamProducer;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class KnowledgeBaseBatchImportService {

    private final KnowledgeBasePersistenceService persistenceService;
    private final KnowledgeBaseParseService parseService;
    private final VectorizeStreamProducer streamProducer;

    public BatchImportResult importJsonlDirectory(String directory, String category) {
        log.info("Importing JSONL files from directory: {}, category: {}", directory, category);

        BatchImportResult result = new BatchImportResult();
        Path dir = Path.of(directory);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            result.addError("Directory does not exist: " + directory);
            return result;
        }

        try (var stream = Files.walk(dir, 10)) {
            stream.filter(path -> path.toString().endsWith(".jsonl"))
                    .forEach(path -> {
                        try {
                            result.filesFound++;
                            Map<String, String> info = parseJsonlFile(path);
                            processFile(path, info, result);
                        } catch (Exception e) {
                            log.error("Failed to process JSONL file: {}", path, e);
                            result.addError("Failed to process " + path.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to scan directory: {}", directory, e);
            result.addError("Failed to scan directory: " + e.getMessage());
        }

        log.info("JSONL import completed: found={}, skipped={}, queued={}, errors={}",
                result.filesFound, result.skippedDuplicate, result.queuedForVectorization, result.errors.size());
        return result;
    }

    public BatchImportResult importBooksDirectory(String directory, String defaultCategory) {
        log.info("Importing PDF books from directory: {}, default category: {}", directory, defaultCategory);

        BatchImportResult result = new BatchImportResult();
        Path dir = Path.of(directory);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            result.addError("Directory does not exist: " + directory);
            return result;
        }

        try (var stream = Files.walk(dir, 10)) {
            stream.filter(path -> path.toString().endsWith(".pdf"))
                    .forEach(path -> {
                        try {
                            result.filesFound++;
                            String category = getCategoryFromPath(dir, path, defaultCategory);
                            Map<String, String> info = parsePdfFile(path);
                            info.put("category", category);
                            processFile(path, info, result);
                        } catch (Exception e) {
                            log.error("Failed to process PDF file: {}", path, e);
                            result.addError("Failed to process " + path.getFileName() + ": " + e.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to scan directory: {}", directory, e);
            result.addError("Failed to scan directory: " + e.getMessage());
        }

        log.info("PDF import completed: found={}, skipped={}, queued={}, errors={}",
                result.filesFound, result.skippedDuplicate, result.queuedForVectorization, result.errors.size());
        return result;
    }

    private void processFile(Path path, Map<String, String> info, BatchImportResult result) {
        String fileHash = info.get("fileHash");
        String fileName = info.get("fileName");
        String name = info.get("name");
        String category = info.get("category");
        String contentType = info.get("contentType");
        long fileSize = Long.parseLong(info.get("fileSize", "0"));

        if (fileHash == null) {
            result.addError("No file hash for: " + fileName);
            return;
        }

        var entity = persistenceService.saveLocalKnowledgeBase(
                name != null ? name : fileName,
                category,
                fileName,
                fileHash,
                fileSize,
                contentType
        );

        if (entity == null) {
            result.skippedDuplicate++;
            log.info("Skipped duplicate: {}", fileName);
            return;
        }

        streamProducer.sendVectorizeTask(entity.getId());
        result.queuedForVectorization++;
        log.info("Queued for vectorization: id={}, file={}", entity.getId(), fileName);
    }

    private Map<String, String> parseJsonlFile(Path path) throws Exception {
        Map<String, String> info = new HashMap<>();
        info.put("fileName", path.getFileName().toString());
        info.put("contentType", "application/jsonl");
        info.put("fileSize", String.valueOf(Files.size(path)));

        var bytesDigest = Files.digest(path, MessageDigest.getInstance("SHA-256"));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytesDigest) {
            sb.append(String.format("%02x", b));
        }
        info.put("fileHash", sb.toString());

        info.put("name", path.getFileName().toString().replace(".jsonl", ""));

        return info;
    }

    private Map<String, String> parsePdfFile(Path path) throws Exception {
        Map<String, String> info = new HashMap<>();
        info.put("fileName", path.getFileName().toString());
        info.put("contentType", "application/pdf");
        info.put("fileSize", String.valueOf(Files.size(path)));

        var bytesDigest = Files.digest(path, MessageDigest.getInstance("SHA-256"));
        StringBuilder sb = new StringBuilder();
        for (byte b : bytesDigest) {
            sb.append(String.format("%02x", b));
        }
        info.put("fileHash", sb.toString());

        String name = path.getFileName().toString().replace(".pdf", "");
        info.put("name", name);

        return info;
    }

    private String getCategoryFromPath(Path baseDir, Path filePath, String defaultCategory) {
        try {
            Path relative = baseDir.relativize(filePath.getParent());
            if (relative.getNameCount() > 0) {
                return relative.getName(0).toString();
            }
        } catch (Exception e) {
            log.warn("Failed to derive category from path: {}", filePath);
        }
        return defaultCategory;
    }

    @Data
    public static class BatchImportResult {
        private int filesFound = 0;
        private int skippedDuplicate = 0;
        private int queuedForVectorization = 0;
        private Set<String> errors = new HashSet<>();

        public void addError(String error) {
            errors.add(error);
        }
    }
}