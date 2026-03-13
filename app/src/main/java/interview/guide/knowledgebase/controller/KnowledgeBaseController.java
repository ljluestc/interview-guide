package interview.guide.knowledgebase.controller;

import interview.guide.knowledgebase.service.KnowledgeBaseBatchImportService;
import interview.guide.knowledgebase.service.KnowledgeBaseBatchImportService.BatchImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/knowledgebase")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseController {

    private final KnowledgeBaseBatchImportService batchImportService;

    @PostMapping("/import/jsonl")
    public ResponseEntity<BatchImportResult> importJsonl(
            @RequestBody BatchImportRequest request) {
        log.info("POST /api/knowledgebase/import/jsonl: directory={}, category={}",
                request.getDirectory(), request.getCategory());
        
        BatchImportResult result = batchImportService.importJsonlDirectory(
                request.getDirectory(), request.getCategory());
        
        return ResponseEntity.ok(result);
    }

    @PostMapping("/import/books")
    public ResponseEntity<BatchImportResult> importBooks(
            @RequestBody BatchImportRequest request) {
        log.info("POST /api/knowledgebase/import/books: directory={}, defaultCategory={}",
                request.getDirectory(), request.getCategory());
        
        BatchImportResult result = batchImportService.importBooksDirectory(
                request.getDirectory(), request.getCategory());
        
        return ResponseEntity.ok(result);
    }
}