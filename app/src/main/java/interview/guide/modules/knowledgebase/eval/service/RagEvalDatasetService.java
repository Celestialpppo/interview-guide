package interview.guide.modules.knowledgebase.eval.service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.ObjectMapper;
import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseEntity;
import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseQrelEntity;
import interview.guide.modules.knowledgebase.eval.model.RagEvalDatasetEntity;
import interview.guide.modules.knowledgebase.eval.model.RagEvalDatasetImportResponse;
import interview.guide.modules.knowledgebase.eval.repository.RagEvalCaseQrelRepository;
import interview.guide.modules.knowledgebase.eval.repository.RagEvalCaseRepository;
import interview.guide.modules.knowledgebase.eval.repository.RagEvalDatasetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 评测数据集管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagEvalDatasetService {

    private final ObjectMapper objectMapper;
    private final RagEvalDatasetRepository datasetRepository;
    private final RagEvalCaseRepository caseRepository;
    private final RagEvalCaseQrelRepository qrelRepository;

    private record ImportedQrelLine(
        @JsonAlias({"chunk_id", "chunkId"})
        String chunkId,
        @JsonAlias({"relevance_grade", "relevanceGrade"})
        Integer relevanceGrade
    ) {
    }

    private record ImportedCaseLine(
        String question,
        @JsonAlias({"expected_answer", "expectedAnswer", "answer"})
        String expectedAnswer,
        @JsonAlias({"knowledge_base_ids", "knowledgeBaseIds"})
        List<Long> knowledgeBaseIds,
        @JsonAlias({"gold_chunks", "goldChunks", "qrels"})
        List<ImportedQrelLine> goldChunks,
        Boolean enabled
    ) {
    }

    @Transactional(rollbackFor = Exception.class)
    public RagEvalDatasetImportResponse importJsonl(
        MultipartFile file,
        String name,
        String version,
        String description,
        String domain,
        String labelSource
    ) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.RAG_EVAL_DATASET_IMPORT_FAILED, "评测数据集文件不能为空");
        }

        RagEvalDatasetEntity dataset = new RagEvalDatasetEntity();
        dataset.setName(name != null && !name.isBlank() ? name.trim() : stripExtension(file.getOriginalFilename()));
        dataset.setVersion(version != null && !version.isBlank() ? version.trim() : "1.0");
        dataset.setDescription(description != null ? description.trim() : null);
        dataset.setDomain(domain != null ? domain.trim() : null);
        dataset.setLabelSource(labelSource != null && !labelSource.isBlank()
            ? labelSource.trim()
            : "llm_prelabel_plus_human_review");
        dataset = datasetRepository.save(dataset);

        int importedCaseCount = 0;
        int importedQrelCount = 0;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                ImportedCaseLine imported = objectMapper.readValue(line, ImportedCaseLine.class);
                validateImportedCase(imported);

                RagEvalCaseEntity evalCase = new RagEvalCaseEntity();
                evalCase.setDataset(dataset);
                evalCase.setQuestion(imported.question().trim());
                evalCase.setExpectedAnswer(imported.expectedAnswer() != null ? imported.expectedAnswer().trim() : "");
                evalCase.setKnowledgeBaseIdsJson(objectMapper.writeValueAsString(imported.knowledgeBaseIds()));
                evalCase.setEnabled(imported.enabled() == null || imported.enabled());
                evalCase = caseRepository.save(evalCase);
                importedCaseCount++;

                for (ImportedQrelLine goldChunk : imported.goldChunks()) {
                    RagEvalCaseQrelEntity qrel = new RagEvalCaseQrelEntity();
                    qrel.setEvalCase(evalCase);
                    qrel.setChunkId(goldChunk.chunkId().trim());
                    qrel.setRelevanceGrade(normalizeRelevanceGrade(goldChunk.relevanceGrade()));
                    qrelRepository.save(qrel);
                    importedQrelCount++;
                }
            }
        } catch (IOException e) {
            log.error("导入评测数据集失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.RAG_EVAL_DATASET_IMPORT_FAILED, "评测数据集导入失败：" + e.getMessage());
        }

        return new RagEvalDatasetImportResponse(
            dataset.getId(),
            dataset.getName(),
            dataset.getVersion(),
            importedCaseCount,
            importedQrelCount
        );
    }

    private void validateImportedCase(ImportedCaseLine imported) {
        if (imported == null || imported.question() == null || imported.question().isBlank()) {
            throw new BusinessException(ErrorCode.RAG_EVAL_DATASET_IMPORT_FAILED, "存在空问题的评测 case");
        }
        if (imported.knowledgeBaseIds() == null || imported.knowledgeBaseIds().isEmpty()) {
            throw new BusinessException(ErrorCode.RAG_EVAL_DATASET_IMPORT_FAILED, "评测 case 缺少 knowledgeBaseIds");
        }
        if (imported.goldChunks() == null || imported.goldChunks().isEmpty()) {
            throw new BusinessException(ErrorCode.RAG_EVAL_DATASET_IMPORT_FAILED, "评测 case 缺少 goldChunks/qrels");
        }
        boolean invalidQrel = imported.goldChunks().stream()
            .anyMatch(item -> item == null || item.chunkId() == null || item.chunkId().isBlank());
        if (invalidQrel) {
            throw new BusinessException(ErrorCode.RAG_EVAL_DATASET_IMPORT_FAILED, "评测 qrel 中存在空 chunkId");
        }
    }

    private int normalizeRelevanceGrade(Integer relevanceGrade) {
        int value = relevanceGrade != null ? relevanceGrade : 1;
        return Math.max(0, Math.min(3, value));
    }

    private String stripExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            return "rag-eval-dataset";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
    }
}
