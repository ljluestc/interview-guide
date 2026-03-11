package interview.guide.modules.knowledgebase.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.infrastructure.file.FileHashService;
import interview.guide.modules.knowledgebase.listener.VectorizeStreamProducer;
import interview.guide.modules.knowledgebase.model.BatchImportResult;
import interview.guide.modules.knowledgebase.model.BatchImportResult.ImportedFileInfo;
import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 知识库批量导入服务
 * 支持从本地 JSONL Q&A 文件和文档目录（PDF、PPTX、DOCX、MD）批量导入到 pgvector
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseBatchImportService {

    private final KnowledgeBasePersistenceService persistenceService;
    private final KnowledgeBaseParseService parseService;
    private final KnowledgeBaseRepository knowledgeBaseRepository;
    private final FileHashService fileHashService;
    private final VectorizeStreamProducer vectorizeStreamProducer;
    private final ObjectMapper objectMapper;

    private static final String DEFAULT_JSONL_CATEGORY = "GenAI Interview RAG";

    // ==================== JSONL 导入 ====================

    /**
     * 从目录中批量导入 JSONL Q&A 文件
     * 每个 JSONL 文件作为一个知识库条目，所有 Q&A 拼接后统一分块向量化
     *
     * @param directory JSONL 文件所在目录
     * @param category  分类名称（可选，默认 "GenAI Interview RAG"）
     * @return 导入结果
     */
    public BatchImportResult importJsonlDirectory(String directory, String category) {
        Path dirPath = Path.of(directory);
        if (!Files.isDirectory(dirPath)) {
            return new BatchImportResult(0, 0, 0, 0, List.of(), List.of("目录不存在: " + directory));
        }

        String effectiveCategory = (category != null && !category.isBlank()) ? category : DEFAULT_JSONL_CATEGORY;
        List<Path> jsonlFiles;
        try (Stream<Path> stream = Files.list(dirPath)) {
            jsonlFiles = stream
                .filter(p -> p.toString().endsWith(".jsonl"))
                .sorted()
                .toList();
        } catch (IOException e) {
            log.error("扫描 JSONL 目录失败: {}", e.getMessage(), e);
            return new BatchImportResult(0, 0, 0, 0, List.of(), List.of("扫描目录失败: " + e.getMessage()));
        }

        log.info("扫描到 {} 个 JSONL 文件: {}", jsonlFiles.size(), directory);

        int imported = 0, skipped = 0, failed = 0;
        List<ImportedFileInfo> importedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Path file : jsonlFiles) {
            try {
                ImportResult result = importSingleJsonlFile(file, effectiveCategory);
                switch (result) {
                    case ImportResult.Success s -> {
                        imported++;
                        importedFiles.add(s.info());
                    }
                    case ImportResult.Skipped ignored -> skipped++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(file.getFileName().toString() + ": " + e.getMessage());
                log.error("导入 JSONL 文件失败: {}", file, e);
            }
        }

        log.info("JSONL 导入完成: total={}, imported={}, skipped={}, failed={}", jsonlFiles.size(), imported, skipped, failed);
        return new BatchImportResult(jsonlFiles.size(), imported, skipped, failed, importedFiles, errors);
    }

    private ImportResult importSingleJsonlFile(Path file, String category) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file);
        String fileHash = fileHashService.calculateHash(fileBytes);

        // 去重检查
        if (knowledgeBaseRepository.existsByFileHash(fileHash)) {
            log.info("JSONL 文件已存在，跳过: {}", file.getFileName());
            return new ImportResult.Skipped();
        }

        // 解析 JSONL 并拼接为文档文本
        String content = parseJsonlToDocument(fileBytes);
        if (content.isBlank()) {
            throw new IllegalStateException("JSONL 文件内容为空");
        }

        String filename = file.getFileName().toString();
        String name = formatJsonlName(filename);

        // 保存知识库元数据
        KnowledgeBaseEntity saved = persistenceService.saveLocalKnowledgeBase(
            name, category, filename, fileBytes.length, "application/jsonl", fileHash
        );

        // 发送向量化任务
        vectorizeStreamProducer.sendVectorizeTask(saved.getId(), content);
        log.info("JSONL 文件已入队向量化: kbId={}, name={}, entries={}",
            saved.getId(), name, content.chars().filter(c -> c == '\n').count());

        return new ImportResult.Success(new ImportedFileInfo(saved.getId(), name, category, filename));
    }

    /**
     * 将 JSONL 文件内容解析为用于向量化的文档文本
     * 每条 Q&A 格式: Question: ...\nAnswer: ...\nKey Points: ...
     */
    private String parseJsonlToDocument(byte[] fileBytes) throws IOException {
        String[] lines = new String(fileBytes).split("\n");
        StringBuilder doc = new StringBuilder();

        for (String line : lines) {
            if (line.isBlank()) continue;
            try {
                JsonNode node = objectMapper.readTree(line);
                StringBuilder entry = new StringBuilder();

                String question = textOrEmpty(node, "question");
                String answer = textOrEmpty(node, "answer");

                if (!question.isEmpty()) {
                    entry.append("Question: ").append(question).append("\n");
                }
                if (!answer.isEmpty()) {
                    entry.append("Answer: ").append(answer).append("\n");
                }

                // key_points 数组
                JsonNode keyPoints = node.get("key_points");
                if (keyPoints != null && keyPoints.isArray() && !keyPoints.isEmpty()) {
                    entry.append("Key Points:\n");
                    for (JsonNode kp : keyPoints) {
                        String text = kp.asText("");
                        if (!text.isEmpty()) {
                            entry.append("- ").append(text).append("\n");
                        }
                    }
                }

                // example 字段
                String example = textOrEmpty(node, "example");
                if (!example.isEmpty() && !"N/A".equals(example)) {
                    entry.append("Example: ").append(example).append("\n");
                }

                if (!entry.isEmpty()) {
                    if (!doc.isEmpty()) {
                        doc.append("\n---\n\n");
                    }
                    doc.append(entry);
                }
            } catch (Exception e) {
                log.warn("跳过无法解析的 JSONL 行: {}", e.getMessage());
            }
        }

        return doc.toString();
    }

    private String textOrEmpty(JsonNode node, String field) {
        JsonNode f = node.get(field);
        return (f != null && !f.isNull()) ? f.asText("") : "";
    }

    private String formatJsonlName(String filename) {
        // rag_interview_hub_qa.jsonl -> "RAG Interview Hub Q&A"
        String base = filename.replace(".jsonl", "")
            .replace("_", " ");
        // Capitalize first letter of each word
        String[] words = base.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!sb.isEmpty()) sb.append(" ");
                // 特殊缩写处理
                if (word.equalsIgnoreCase("qa")) {
                    sb.append("Q&A");
                } else if (word.equalsIgnoreCase("rag") || word.equalsIgnoreCase("ai") || word.equalsIgnoreCase("genai")) {
                    sb.append(word.toUpperCase());
                } else {
                    sb.append(Character.toUpperCase(word.charAt(0)))
                      .append(word.substring(1).toLowerCase());
                }
            }
        }
        return sb.toString();
    }

    // ==================== Books / Documents 导入 ====================

    private static final List<String> DOC_EXTENSIONS = List.of(".pdf", ".pptx", ".docx", ".md");

    /**
     * 从目录中批量导入文档（PDF、PPTX、DOCX、Markdown）
     * 递归扫描目录，每个文件作为一个知识库条目
     *
     * @param directory           文档根目录
     * @param useSubdirAsCategory 是否使用子目录名作为分类
     * @param defaultCategory     根目录下文件的默认分类（当无子目录或 useSubdirAsCategory=false 时使用）
     * @return 导入结果
     */
    public BatchImportResult importBooksDirectory(String directory, boolean useSubdirAsCategory, String defaultCategory) {
        Path dirPath = Path.of(directory);
        if (!Files.isDirectory(dirPath)) {
            return new BatchImportResult(0, 0, 0, 0, List.of(), List.of("目录不存在: " + directory));
        }

        List<Path> docFiles;
        try (Stream<Path> stream = Files.walk(dirPath)) {
            docFiles = stream
                .filter(Files::isRegularFile)
                .filter(p -> {
                    String lower = p.toString().toLowerCase();
                    return DOC_EXTENSIONS.stream().anyMatch(lower::endsWith);
                })
                .sorted()
                .toList();
        } catch (IOException e) {
            log.error("扫描文档目录失败: {}", e.getMessage(), e);
            return new BatchImportResult(0, 0, 0, 0, List.of(), List.of("扫描目录失败: " + e.getMessage()));
        }

        log.info("扫描到 {} 个文档文件 (pdf/pptx/docx/md): {}", docFiles.size(), directory);

        int imported = 0, skipped = 0, failed = 0;
        List<ImportedFileInfo> importedFiles = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (Path file : docFiles) {
            try {
                String category = useSubdirAsCategory ? resolveCategory(dirPath, file) : null;
                if (category == null && defaultCategory != null && !defaultCategory.isBlank()) {
                    category = defaultCategory.trim();
                }
                ImportResult result = importSingleBook(file, category);
                switch (result) {
                    case ImportResult.Success s -> {
                        imported++;
                        importedFiles.add(s.info());
                    }
                    case ImportResult.Skipped ignored -> skipped++;
                }
            } catch (Exception e) {
                failed++;
                errors.add(file.getFileName().toString() + ": " + e.getMessage());
                log.error("导入文档失败: {}", file, e);
            }
        }

        log.info("文档导入完成: total={}, imported={}, skipped={}, failed={}", docFiles.size(), imported, skipped, failed);
        return new BatchImportResult(docFiles.size(), imported, skipped, failed, importedFiles, errors);
    }

    private ImportResult importSingleBook(Path file, String category) throws IOException {
        byte[] fileBytes = Files.readAllBytes(file);
        String fileHash = fileHashService.calculateHash(fileBytes);

        // 去重检查
        if (knowledgeBaseRepository.existsByFileHash(fileHash)) {
            log.info("文档已存在，跳过: {}", file.getFileName());
            return new ImportResult.Skipped();
        }

        // 使用 Tika 解析文档内容（支持 PDF、PPTX、DOCX、MD 等）
        String filename = file.getFileName().toString();
        String content = parseService.parseLocalFile(file);
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("无法从文档提取文本内容");
        }

        String name = formatDocName(filename);
        String contentType = detectContentType(filename);

        // 保存知识库元数据
        KnowledgeBaseEntity saved = persistenceService.saveLocalKnowledgeBase(
            name, category, filename, fileBytes.length, contentType, fileHash
        );

        // 发送向量化任务
        vectorizeStreamProducer.sendVectorizeTask(saved.getId(), content);
        log.info("文档已入队向量化: kbId={}, name={}, category={}, contentLength={}",
            saved.getId(), name, category, content.length());

        return new ImportResult.Success(new ImportedFileInfo(saved.getId(), name, category, filename));
    }

    /**
     * 根据文件相对于根目录的路径，提取第一级子目录名作为分类
     * 例: /books/System Design/foo.pdf → "System Design"
     */
    private String resolveCategory(Path rootDir, Path file) {
        Path relative = rootDir.relativize(file.getParent());
        if (relative.getNameCount() > 0) {
            return relative.getName(0).toString();
        }
        return null;
    }

    private String formatDocName(String filename) {
        String lower = filename.toLowerCase();
        for (String ext : DOC_EXTENSIONS) {
            if (lower.endsWith(ext)) {
                return filename.substring(0, filename.length() - ext.length());
            }
        }
        return filename;
    }

    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".md")) return "text/markdown";
        return "application/octet-stream";
    }

    // ==================== 内部结果类型 ====================

    private sealed interface ImportResult {
        record Success(ImportedFileInfo info) implements ImportResult {}
        record Skipped() implements ImportResult {}
    }
}
