package interview.guide.knowledgebase.model;

import lombok.Data;

@Data
public class BatchImportRequest {
    private String directory;
    private String category;
    private String defaultCategory;
}