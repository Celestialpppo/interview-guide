package interview.guide.modules.knowledgebase.service;

import interview.guide.modules.knowledgebase.model.KnowledgeBaseEntity;
import interview.guide.modules.knowledgebase.repository.KnowledgeBaseRepository;
import interview.guide.modules.knowledgebase.repository.VectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识库向量存储服务
 * 负责文档分块、向量化和检索。
 */
@Slf4j
@Service
public class KnowledgeBaseVectorService {

    /**
     * 阿里云 DashScope Embedding API 批量大小限制
     */
    private static final int MAX_BATCH_SIZE = 10;

    private final VectorStore vectorStore;
    private final TextSplitter textSplitter;
    private final VectorRepository vectorRepository;
    private final KnowledgeBaseRepository knowledgeBaseRepository;

    public KnowledgeBaseVectorService(
        VectorStore vectorStore,
        VectorRepository vectorRepository,
        KnowledgeBaseRepository knowledgeBaseRepository
    ) {
        this.vectorStore = vectorStore;
        this.vectorRepository = vectorRepository;
        this.knowledgeBaseRepository = knowledgeBaseRepository;
        this.textSplitter = new TokenTextSplitter();
    }

    /**
     * 将知识库内容向量化并存储。
     */
    @Transactional
    public void vectorizeAndStore(Long knowledgeBaseId, String content) {
        log.info("开始向量化知识库: kbId={}, contentLength={}", knowledgeBaseId, content.length());
        try {
            KnowledgeBaseEntity knowledgeBase = knowledgeBaseRepository.findById(knowledgeBaseId)
                .orElseThrow(() -> new IllegalStateException("知识库不存在: " + knowledgeBaseId));

            deleteByKnowledgeBaseId(knowledgeBaseId);

            List<Document> chunks = textSplitter.apply(List.of(new Document(content)));
            log.info("文本分块完成: {} 个chunks", chunks.size());

            enrichChunkMetadata(knowledgeBase, content, chunks);

            int totalChunks = chunks.size();
            int batchCount = (totalChunks + MAX_BATCH_SIZE - 1) / MAX_BATCH_SIZE;
            log.info("开始分批向量化: 总共 {} 个chunks，分 {} 批处理，每批最多 {} 个",
                totalChunks, batchCount, MAX_BATCH_SIZE);

            for (int i = 0; i < batchCount; i++) {
                int start = i * MAX_BATCH_SIZE;
                int end = Math.min(start + MAX_BATCH_SIZE, totalChunks);
                List<Document> batch = chunks.subList(start, end);
                log.debug("处理第 {}/{} 批: chunks {}-{}", i + 1, batchCount, start + 1, end);
                vectorStore.add(batch);
            }

            knowledgeBase.setChunkCount(totalChunks);
            knowledgeBaseRepository.save(knowledgeBase);
            log.info("知识库向量化完成: kbId={}, chunks={}, batches={}",
                knowledgeBaseId, totalChunks, batchCount);
        } catch (Exception e) {
            log.error("向量化知识库失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
            throw new RuntimeException("向量化知识库失败: " + e.getMessage(), e);
        }
    }

    /**
     * 基于多个知识库进行相似度搜索。
     */
    public List<Document> similaritySearch(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        log.info("向量相似度搜索: query={}, kbIds={}, topK={}, minScore={}",
            query, knowledgeBaseIds, topK, minScore);

        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK, 1));

            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                builder.filterExpression(buildKbFilterExpression(knowledgeBaseIds));
            }

            List<Document> results = vectorStore.similaritySearch(builder.build());
            if (results == null) {
                return List.of();
            }

            log.info("搜索完成: 找到 {} 个相关文档", results.size());
            return results;
        } catch (Exception e) {
            log.warn("向量搜索前置过滤失败，回退到本地过滤: {}", e.getMessage());
            return similaritySearchFallback(query, knowledgeBaseIds, topK, minScore);
        }
    }

    private List<Document> similaritySearchFallback(String query, List<Long> knowledgeBaseIds, int topK, double minScore) {
        try {
            SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(Math.max(topK * 3, topK));
            if (minScore > 0) {
                builder.similarityThreshold(minScore);
            }

            List<Document> allResults = vectorStore.similaritySearch(builder.build());
            if (allResults == null || allResults.isEmpty()) {
                return List.of();
            }

            if (knowledgeBaseIds != null && !knowledgeBaseIds.isEmpty()) {
                allResults = allResults.stream()
                    .filter(doc -> isDocInKnowledgeBases(doc, knowledgeBaseIds))
                    .toList();
            }

            List<Document> results = allResults.stream()
                .limit(topK)
                .collect(Collectors.toList());

            log.info("回退检索完成: 找到 {} 个相关文档", results.size());
            return results;
        } catch (Exception e) {
            log.error("向量搜索失败: {}", e.getMessage(), e);
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }

    private boolean isDocInKnowledgeBases(Document doc, List<Long> knowledgeBaseIds) {
        Object kbId = doc.getMetadata().get("kb_id");
        if (kbId == null) {
            return false;
        }
        try {
            Long kbIdLong = kbId instanceof Long
                ? (Long) kbId
                : Long.parseLong(kbId.toString());
            return knowledgeBaseIds.contains(kbIdLong);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String buildKbFilterExpression(List<Long> knowledgeBaseIds) {
        String values = knowledgeBaseIds.stream()
            .filter(Objects::nonNull)
            .map(String::valueOf)
            .map(id -> "'" + id + "'")
            .collect(Collectors.joining(", "));
        return "kb_id in [" + values + "]";
    }

    private void enrichChunkMetadata(KnowledgeBaseEntity knowledgeBase, String content, List<Document> chunks) {
        int searchFrom = 0;
        String sourceName = knowledgeBase.getOriginalFilename() != null && !knowledgeBase.getOriginalFilename().isBlank()
            ? knowledgeBase.getOriginalFilename()
            : knowledgeBase.getName();
        String sourceType = knowledgeBase.getContentType() != null ? knowledgeBase.getContentType() : "text/plain";

        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            String chunkText = chunk.getText() != null ? chunk.getText() : "";
            int charStart = resolveChunkStart(content, chunkText, searchFrom);
            int charEnd = charStart >= 0 ? charStart + chunkText.length() : -1;
            if (charEnd >= 0) {
                searchFrom = charEnd;
            }

            chunk.getMetadata().put("kb_id", knowledgeBase.getId().toString());
            chunk.getMetadata().put("chunk_id", buildChunkId(knowledgeBase, i, chunkText));
            chunk.getMetadata().put("chunk_index", i);
            chunk.getMetadata().put("source_name", sourceName);
            chunk.getMetadata().put("source_type", sourceType.toLowerCase(Locale.ROOT));
            chunk.getMetadata().put("section", "");
            chunk.getMetadata().put("page_no", -1);
            chunk.getMetadata().put("char_start", charStart);
            chunk.getMetadata().put("char_end", charEnd);
            chunk.getMetadata().put("file_hash", knowledgeBase.getFileHash());
        }
    }

    private int resolveChunkStart(String content, String chunkText, int searchFrom) {
        if (content == null || chunkText == null || chunkText.isBlank()) {
            return -1;
        }
        int start = content.indexOf(chunkText, Math.max(searchFrom, 0));
        if (start >= 0) {
            return start;
        }
        return content.indexOf(chunkText);
    }

    private String buildChunkId(KnowledgeBaseEntity knowledgeBase, int chunkIndex, String chunkText) {
        String source = knowledgeBase.getFileHash() != null
            ? knowledgeBase.getFileHash()
            : knowledgeBase.getId().toString();
        return sha256Hex(source + ":" + chunkIndex + ":" + (chunkText != null ? chunkText : ""));
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("生成 chunk_id 失败", e);
        }
    }

    /**
     * 删除指定知识库的所有向量数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        try {
            vectorRepository.deleteByKnowledgeBaseId(knowledgeBaseId);
        } catch (Exception e) {
            log.error("删除向量数据失败: kbId={}, error={}", knowledgeBaseId, e.getMessage(), e);
        }
    }
}
