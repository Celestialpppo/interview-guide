package interview.guide.modules.knowledgebase.eval.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseEntity;
import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseQrelEntity;
import interview.guide.modules.knowledgebase.eval.model.RagEvalDatasetEntity;
import interview.guide.modules.knowledgebase.eval.model.RagEvalDatasetImportResponse;
import interview.guide.modules.knowledgebase.eval.repository.RagEvalCaseQrelRepository;
import interview.guide.modules.knowledgebase.eval.repository.RagEvalCaseRepository;
import interview.guide.modules.knowledgebase.eval.repository.RagEvalDatasetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("RAG 评测数据集导入测试")
class RagEvalDatasetServiceTest {

    private RagEvalDatasetService datasetService;

    @Mock
    private RagEvalDatasetRepository datasetRepository;

    @Mock
    private RagEvalCaseRepository caseRepository;

    @Mock
    private RagEvalCaseQrelRepository qrelRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        datasetService = new RagEvalDatasetService(new ObjectMapper(), datasetRepository, caseRepository, qrelRepository);
        when(datasetRepository.save(any(RagEvalDatasetEntity.class))).thenAnswer(invocation -> {
            RagEvalDatasetEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return entity;
        });
        when(caseRepository.save(any(RagEvalCaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(qrelRepository.save(any(RagEvalCaseQrelEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("应成功导入 JSONL 数据集")
    void shouldImportJsonlDataset() {
        String jsonl = """
            {"question":"什么是 RAG？","expected_answer":"RAG 是检索增强生成。","knowledge_base_ids":[1,2],"gold_chunks":[{"chunk_id":"chunk-1","relevance_grade":3},{"chunk_id":"chunk-2","relevance_grade":1}]}
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "rag-eval.jsonl",
            "application/json",
            jsonl.getBytes()
        );

        RagEvalDatasetImportResponse response = datasetService.importJsonl(
            file, "rag-dataset", "1.0", "desc", "kb", "manual"
        );

        assertEquals(1L, response.datasetId());
        assertEquals(1, response.importedCaseCount());
        assertEquals(2, response.importedQrelCount());

        ArgumentCaptor<RagEvalCaseEntity> caseCaptor = ArgumentCaptor.forClass(RagEvalCaseEntity.class);
        verify(caseRepository).save(caseCaptor.capture());
        assertEquals("什么是 RAG？", caseCaptor.getValue().getQuestion());
        assertEquals("[1,2]", caseCaptor.getValue().getKnowledgeBaseIdsJson());
    }

    @Test
    @DisplayName("缺少 knowledgeBaseIds 时应抛异常")
    void shouldRejectCaseWithoutKnowledgeBaseIds() {
        String jsonl = """
            {"question":"什么是 RAG？","gold_chunks":[{"chunk_id":"chunk-1","relevance_grade":3}]}
            """;
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "bad.jsonl",
            "application/json",
            jsonl.getBytes()
        );

        assertThrows(BusinessException.class, () -> datasetService.importJsonl(
            file, "rag-dataset", "1.0", null, null, null
        ));
    }
}
