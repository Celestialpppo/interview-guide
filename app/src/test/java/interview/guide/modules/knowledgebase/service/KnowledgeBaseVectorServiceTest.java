package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.repository.VectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * KnowledgeBaseVectorService 单元测试
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>向量化存储（vectorizeAndStore）- 分批处理逻辑、metadata 设置、删除旧数据</li>
 *   <li>相似度搜索（similaritySearch）- 基本搜索、知识库ID过滤、topK限制</li>
 *   <li>删除向量数据（deleteByKnowledgeBaseId）</li>
 * </ul>
 *
 * <p>注意：TextSplitter 未被 Mock，测试依赖 TokenTextSplitter 的真实行为。
 * 这是有意为之，因为分词逻辑是向量化的核心部分，应该进行集成测试。
 * 如需完全隔离，可将 TextSplitter 改为构造函数注入。
 */
@DisplayName("知识库向量服务测试")
@SuppressWarnings("unchecked")
class KnowledgeBaseVectorServiceTest {

    private KnowledgeBaseVectorService vectorService;

    @Mock
    private VectorStore vectorStore;

    @Mock
    private VectorRepository vectorRepository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        vectorService = new KnowledgeBaseVectorService(vectorStore, vectorRepository);
    }

    // ==================== 共享辅助方法 ====================

    /**
     * 生成足够长的内容，确保 TokenTextSplitter 产生 chunks
     * TokenTextSplitter 默认配置下，需要较长的文本才会分块
     */
    private String generateLongContent(int paragraphs) {
        StringBuilder contentBuilder = new StringBuilder();
        for (int i = 0; i < paragraphs; i++) {
            contentBuilder.append("这是第 ").append(i).append(" 段内容。")
                    .append("Spring Boot 是一个优秀的 Java 框架，它简化了 Spring 应用的开发。")
                    .append("通过自动配置和起步依赖，开发者可以快速构建生产级别的应用。")
                    .append("Spring AI 提供了与各种 AI 模型交互的能力，包括 embedding 和 chat 功能。")
                    .append("PostgreSQL 是一个强大的开源关系数据库，支持向量存储和相似度搜索。")
                    .append("通过 pgvector 扩展，可以实现高效的向量索引和检索功能。")
                    .append("知识库系统可以将文档内容向量化，然后进行语义搜索，提高检索的准确性。")
                    .append("\n\n");
        }
        return contentBuilder.toString();
    }

    /**
     * 创建模拟文档列表
     * @param count 文档数量
     * @param kbId 知识库ID（String 类型），null 表示不设置
     */
    private List<Document> createMockDocuments(int count, String kbId) {
        List<Document> documents = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> metadata = new HashMap<>();
            if (kbId != null) {
                metadata.put("kb_id", kbId);
            }
            documents.add(new Document("文档内容 " + i, metadata));
        }
        return documents;
    }

    /**
     * 创建模拟文档列表（无 kb_id）
     */
    private List<Document> createMockDocuments(int count) {
        return createMockDocuments(count, null);
    }

    /**
     * 创建使用 Long 类型 kb_id 的文档（模拟旧数据格式）
     */
    private Document createDocumentWithLongKbId(Long kbId) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("kb_id", kbId);
        return new Document("Long kb_id 文档", metadata);
    }

    /**
     * 创建包含无效 kb_id 的文档
     */
    private Document createDocumentWithInvalidKbId(String invalidKbId) {
        Map<String, Object> metadata = new HashMap<>();
        if (invalidKbId != null) {
            metadata.put("kb_id", invalidKbId);
        }
        return new Document("无效 kb_id 文档", metadata);
    }

    // ==================== 测试类 ====================

    @Nested
    @DisplayName("向量化存储测试")
    class VectorizeAndStoreTests {

        @Test
        @DisplayName("文本向量化存储 - 验证基本流程")
        void testVectorizeSmallContent() {
            Long knowledgeBaseId = 1L;
            String content = generateLongContent(5);

            vectorService.vectorizeAndStore(knowledgeBaseId, content);

            verify(vectorRepository, times(1)).deleteByKnowledgeBaseId(knowledgeBaseId);
            verify(vectorStore, atLeastOnce()).add(anyList());
        }

        @Test
        @DisplayName("大文本分批处理 - 验证每批不超过限制")
        void testVectorizeLargeContentInBatches() {
            Long knowledgeBaseId = 2L;
            String content = generateLongContent(200);

            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);

            vectorService.vectorizeAndStore(knowledgeBaseId, content);

            verify(vectorStore, atLeastOnce()).add(captor.capture());

            List<List<Document>> allBatches = captor.getAllValues();
            for (List<Document> batch : allBatches) {
                assertTrue(batch.size() <= 10,
                        "每批次不应超过 10 个文档，实际: " + batch.size());
            }
        }

        @Test
        @DisplayName("验证 metadata 正确设置 kb_id")
        void testMetadataContainsKnowledgeBaseId() {
            Long knowledgeBaseId = 123L;
            String content = generateLongContent(10);

            ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);

            vectorService.vectorizeAndStore(knowledgeBaseId, content);

            verify(vectorStore, atLeastOnce()).add(captor.capture());

            List<List<Document>> allBatches = captor.getAllValues();
            assertFalse(allBatches.isEmpty(), "应该有文档被添加");

            for (List<Document> batch : allBatches) {
                for (Document doc : batch) {
                    assertEquals(knowledgeBaseId.toString(), doc.getMetadata().get("kb_id"),
                            "metadata 中的 kb_id 应该等于知识库ID的字符串形式");
                }
            }
        }

        @Test
        @DisplayName("向量化前应先删除旧数据")
        void testDeleteOldDataBeforeVectorize() {
            Long knowledgeBaseId = 1L;
            String content = generateLongContent(10);

            vectorService.vectorizeAndStore(knowledgeBaseId, content);

            var inOrder = inOrder(vectorRepository, vectorStore);
            inOrder.verify(vectorRepository).deleteByKnowledgeBaseId(knowledgeBaseId);
            inOrder.verify(vectorStore, atLeastOnce()).add(anyList());
        }

        @Test
        @DisplayName("向量化失败时抛出异常")
        void testVectorizeFailureThrowsException() {
            Long knowledgeBaseId = 1L;
            String content = generateLongContent(10);

            doThrow(new RuntimeException("VectorStore 连接失败"))
                    .when(vectorStore).add(anyList());

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> vectorService.vectorizeAndStore(knowledgeBaseId, content)
            );

            assertTrue(exception.getMessage().contains("向量化知识库失败"));
        }

        @Test
        @DisplayName("空内容处理 - 应该删除旧数据但不添加新数据")
        void testVectorizeEmptyContent() {
            Long knowledgeBaseId = 1L;
            String content = "";

            vectorService.vectorizeAndStore(knowledgeBaseId, content);

            verify(vectorRepository, times(1)).deleteByKnowledgeBaseId(knowledgeBaseId);
            verify(vectorStore, never()).add(anyList());
        }
    }

    @Nested
    @DisplayName("相似度搜索测试")
    class SimilaritySearchTests {

        @Test
        @DisplayName("基本搜索 - 无过滤条件")
        void testBasicSearchWithoutFilter() {
            String query = "Java 开发经验";
            int topK = 5;

            List<Document> mockResults = createMockDocuments(10, null);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, null, topK, 0.0);

            // 正常路径下，service 不做本地 limit，mock 返回多少就得到多少
            assertEquals(10, results.size(), "当前 mock 返回多少条，正常路径就返回多少条");

            ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(vectorStore, times(1)).similaritySearch(captor.capture());

            SearchRequest request = captor.getValue();
            assertEquals(query, request.getQuery());
            assertEquals(topK, request.getTopK());
            assertFalse(request.hasFilterExpression(), "无过滤条件时不应设置 filterExpression");
        }

        @Test
        @DisplayName("搜索结果按知识库ID过滤 - String类型kb_id（回退路径）")
        void testSearchWithKnowledgeBaseIdFilterString() {
            String query = "Spring Boot";
            List<Long> knowledgeBaseIds = List.of(1L, 2L);
            int topK = 10;

            List<Document> mockResults = new ArrayList<>();
            mockResults.addAll(createMockDocuments(3, "1"));
            mockResults.addAll(createMockDocuments(3, "2"));
            mockResults.addAll(createMockDocuments(4, "3"));

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("前置过滤不支持"))
                    .thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, knowledgeBaseIds, topK, 0.0);

            assertEquals(6, results.size(), "应该只返回匹配知识库ID的文档");

            for (Document doc : results) {
                String kbId = doc.getMetadata().get("kb_id").toString();
                assertTrue(kbId.equals("1") || kbId.equals("2"),
                        "结果应该只包含指定知识库的文档");
            }

            verify(vectorStore, times(2)).similaritySearch(any(SearchRequest.class));
        }

        @Test
        @DisplayName("搜索结果按知识库ID过滤 - Long类型kb_id（向后兼容，回退路径）")
        void testSearchWithKnowledgeBaseIdFilterLong() {
            String query = "Python 开发";
            List<Long> knowledgeBaseIds = List.of(100L);
            int topK = 5;

            List<Document> mockResults = new ArrayList<>();
            mockResults.add(createDocumentWithLongKbId(100L));
            mockResults.add(createDocumentWithLongKbId(100L));
            mockResults.add(createDocumentWithLongKbId(200L));

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("前置过滤不支持"))
                    .thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, knowledgeBaseIds, topK, 0.0);

            assertEquals(2, results.size(), "应该只返回 kb_id=100 的文档");
        }

        @Test
        @DisplayName("topK 限制生效（回退路径）")
        void testTopKLimit() {
            String query = "测试查询";
            int topK = 3;

            List<Document> mockResults = createMockDocuments(10, "1");
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("前置过滤不支持"))
                    .thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, List.of(1L), topK, 0.0);

            assertEquals(topK, results.size(), "结果数量应该被 topK 限制");
        }

        @Test
        @DisplayName("搜索失败时抛出异常")
        void testSearchFailureThrowsException() {
            String query = "测试";
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("搜索服务不可用"));

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> vectorService.similaritySearch(query, null, 5, 0.0)
            );

            assertTrue(exception.getMessage().contains("向量搜索失败"));
        }

        @Test
        @DisplayName("空知识库ID列表 - 不进行过滤")
        void testSearchWithEmptyKnowledgeBaseIdList() {
            String query = "查询";
            List<Long> emptyList = List.of();
            int topK = 5;

            List<Document> mockResults = createMockDocuments(10, "1");
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, emptyList, topK, 0.0);

            // 空列表时正常路径不做本地过滤，mock 返回多少就得到多少
            assertEquals(10, results.size(), "空列表时服务层不会本地过滤，mock 返回多少就得到多少");

            ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(vectorStore, times(1)).similaritySearch(captor.capture());

            SearchRequest request = captor.getValue();
            assertEquals(query, request.getQuery());
            assertEquals(topK, request.getTopK());
            assertFalse(request.hasFilterExpression(), "空知识库ID列表时不应设置 filterExpression");
        }

        @Test
        @DisplayName("搜索结果为空")
        void testSearchReturnsEmpty() {
            String query = "不存在的内容";
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

            List<Document> results = vectorService.similaritySearch(query, null, 10, 0.0);

            assertTrue(results.isEmpty(), "搜索结果应该为空");
        }

        @Test
        @DisplayName("过滤后结果为空（回退路径）")
        void testFilteredResultsEmpty() {
            String query = "测试";
            List<Long> knowledgeBaseIds = List.of(999L);

            List<Document> mockResults = createMockDocuments(5, "1");
            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("前置过滤不支持"))
                    .thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, knowledgeBaseIds, 10, 0.0);

            assertTrue(results.isEmpty(), "没有匹配的知识库ID，结果应为空");
        }

        @Test
        @DisplayName("处理无效的 kb_id 格式（回退路径）")
        void testHandleInvalidKbIdFormat() {
            String query = "测试";
            List<Long> knowledgeBaseIds = List.of(1L);

            List<Document> mockResults = new ArrayList<>();
            mockResults.add(createDocumentWithInvalidKbId("not_a_number"));
            mockResults.add(createDocumentWithInvalidKbId(null));
            mockResults.addAll(createMockDocuments(2, "1"));

            when(vectorStore.similaritySearch(any(SearchRequest.class)))
                    .thenThrow(new RuntimeException("前置过滤不支持"))
                    .thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, knowledgeBaseIds, 10, 0.0);

            assertEquals(2, results.size(), "只应返回有效 kb_id 的文档");
        }
    }

    @Nested
    @DisplayName("删除向量数据测试")
    class DeleteVectorDataTests {

        @Test
        @DisplayName("成功删除向量数据")
        void testDeleteByKnowledgeBaseId() {
            Long knowledgeBaseId = 1L;
            when(vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId)).thenReturn(5);

            vectorService.deleteByKnowledgeBaseId(knowledgeBaseId);

            verify(vectorRepository, times(1)).deleteByKnowledgeBaseId(knowledgeBaseId);
        }

        @Test
        @DisplayName("删除失败不抛出异常（静默处理）")
        void testDeleteFailureSilentlyHandled() {
            Long knowledgeBaseId = 1L;
            doThrow(new RuntimeException("数据库错误"))
                    .when(vectorRepository).deleteByKnowledgeBaseId(knowledgeBaseId);

            assertDoesNotThrow(() -> vectorService.deleteByKnowledgeBaseId(knowledgeBaseId));
        }

        @Test
        @DisplayName("删除不存在的知识库数据")
        void testDeleteNonExistentKnowledgeBase() {
            Long knowledgeBaseId = 999L;
            when(vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId)).thenReturn(0);

            vectorService.deleteByKnowledgeBaseId(knowledgeBaseId);

            verify(vectorRepository, times(1)).deleteByKnowledgeBaseId(knowledgeBaseId);
        }
    }

    @Nested
    @DisplayName("边界条件测试")
    class EdgeCaseTests {

        @Test
        @DisplayName("知识库ID为null时 - 应抛出异常并包含有意义的错误信息")
        void testNullKnowledgeBaseId() {
            String content = generateLongContent(5);

            RuntimeException exception = assertThrows(
                    RuntimeException.class,
                    () -> vectorService.vectorizeAndStore(null, content)
            );

            assertTrue(exception.getMessage().contains("向量化知识库失败"),
                    "异常消息应包含'向量化知识库失败'");
        }

        @Test
        @DisplayName("内容为null时 - 应抛出 NullPointerException")
        void testNullContent() {
            Long knowledgeBaseId = 1L;

            assertThrows(
                    NullPointerException.class,
                    () -> vectorService.vectorizeAndStore(knowledgeBaseId, null)
            );
        }

        @Test
        @DisplayName("查询字符串为空")
        void testEmptyQuery() {
            String emptyQuery = "";
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

            List<Document> results = vectorService.similaritySearch(emptyQuery, null, 5, 0.0);

            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("topK 为 0 时实际按 1 处理")
        void testTopKZero() {
            String query = "测试";
            List<Document> mockResults = createMockDocuments(5);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, null, 0, 0.0);

            // 正常路径下，service 只负责把请求参数修正为 1，不会本地截断 mock 返回结果
            assertEquals(5, results.size(), "当前 mock 固定返回 5 条；service 这里只负责修正 SearchRequest.topK");

            ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
            verify(vectorStore, times(1)).similaritySearch(captor.capture());

            SearchRequest request = captor.getValue();
            assertEquals(query, request.getQuery());
            assertEquals(1, request.getTopK(), "topK=0 时应被修正为 1");
            assertFalse(request.hasFilterExpression());
        }

        @Test
        @DisplayName("topK 大于实际结果数")
        void testTopKGreaterThanResults() {
            String query = "测试";
            int topK = 100;
            List<Document> mockResults = createMockDocuments(5);
            when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(mockResults);

            List<Document> results = vectorService.similaritySearch(query, null, topK, 0.0);

            assertEquals(5, results.size(), "应该返回所有可用结果");
        }
    }
}