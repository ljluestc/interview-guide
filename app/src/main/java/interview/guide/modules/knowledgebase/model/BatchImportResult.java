package interview.guide.modules.knowledgebase.model;

import java.util.List;

/**
 * 批量导入结果
 *
 * @param totalFiles   扫描到的文件总数
 * @param imported     成功入队向量化的文件数
 * @param skipped      跳过的文件数（已存在/去重）
 * @param failed       导入失败的文件数
 * @param importedFiles 成功导入的文件信息列表
 * @param errors       错误信息列表
 */
public record BatchImportResult(
    int totalFiles,
    int imported,
    int skipped,
    int failed,
    List<ImportedFileInfo> importedFiles,
    List<String> errors
) {
    /**
     * 导入的文件信息
     */
    public record ImportedFileInfo(
        Long knowledgeBaseId,
        String name,
        String category,
        String originalFilename
    ) {}
}
