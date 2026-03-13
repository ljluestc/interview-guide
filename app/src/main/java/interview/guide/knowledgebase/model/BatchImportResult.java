package interview.guide.knowledgebase.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResult {
    private int filesFound;
    private int skippedDuplicates;
    private int queuedForVectorization;
    @Builder.Default
    private List<String> errors = new ArrayList<>();
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}