package interview.guide.modules.knowledgebase.model;

import jakarta.validation.constraints.NotBlank;

/**
 * 批量导入请求
 */
public sealed interface BatchImportRequest {

    /**
     * JSONL 文件导入请求
     *
     * @param directory JSONL 文件所在目录路径
     * @param category  导入后的分类名称（可选，默认 "GenAI Interview RAG"）
     */
    record JsonlImportRequest(
        @NotBlank(message = "目录路径不能为空")
        String directory,
        String category
    ) implements BatchImportRequest {}

    /**
     * 书籍（PDF）导入请求
     *
     * @param directory           书籍目录路径（递归扫描）
     * @param useSubdirAsCategory 是否使用子目录名作为分类（默认 true）
     * @param defaultCategory     根目录下文件的默认分类（当 useSubdirAsCategory=false 或文件在根目录时使用）
     */
    record BooksImportRequest(
        @NotBlank(message = "目录路径不能为空")
        String directory,
        boolean useSubdirAsCategory,
        String defaultCategory
    ) implements BatchImportRequest {}
}
