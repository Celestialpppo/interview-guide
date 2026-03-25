package interview.guide.modules.knowledgebase.service;

import interview.guide.common.exception.BusinessException;
import interview.guide.common.exception.ErrorCode;
import interview.guide.modules.knowledgebase.model.QueryRequest;
import interview.guide.modules.knowledgebase.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 知识库查询服务
 * 基于向量搜索的RAG问答
 */
@Slf4j
@Service
public class KnowledgeBaseQueryService {
    private static final String NO_RESULT_RESPONSE = "抱歉，在选定的知识库中未检索到相关信息。请换一个更具体的关键词或补充上下文后再试。";
    private static final Pattern SHORT_TOKEN_PATTERN = Pattern.compile("^[\\p{L}\\p{N}_-]{2,20}$");
    private static final int STREAM_PROBE_CHARS = 120;

    private final ChatClient chatClient;
    private final KnowledgeBaseVectorService vectorService;
    private final KnowledgeBaseListService listService;
    private final KnowledgeBaseCountService countService;
    private final PromptTemplate systemPromptTemplate;
    private final PromptTemplate userPromptTemplate;
    private final PromptTemplate rewritePromptTemplate;
    private final boolean rewriteEnabled;
    private final int shortQueryLength;
    private final int topkShort;
    private final int topkMedium;
    private final int topkLong;
    private final double minScoreShort;
    private final double minScoreDefault;

    private record SearchParams(int topK, double minScore) {
    }

    private record QueryContext(String originalQuestion, List<String> candidateQueries, SearchParams searchParams) {
    }

    public KnowledgeBaseQueryService(
            ChatClient.Builder chatClientBuilder,
            KnowledgeBaseVectorService vectorService,
            KnowledgeBaseListService listService,
            KnowledgeBaseCountService countService,
            @Value("classpath:prompts/knowledgebase-query-system.st") Resource systemPromptResource,
            @Value("classpath:prompts/knowledgebase-query-user.st") Resource userPromptResource,
            @Value("classpath:prompts/knowledgebase-query-rewrite.st") Resource rewritePromptResource,
            @Value("${app.ai.rag.rewrite.enabled:true}") boolean rewriteEnabled,
            @Value("${app.ai.rag.search.short-query-length:4}") int shortQueryLength,
            @Value("${app.ai.rag.search.topk-short:20}") int topkShort,
            @Value("${app.ai.rag.search.topk-medium:12}") int topkMedium,
            @Value("${app.ai.rag.search.topk-long:8}") int topkLong,
            @Value("${app.ai.rag.search.min-score-short:0.18}") double minScoreShort,
            @Value("${app.ai.rag.search.min-score-default:0.28}") double minScoreDefault) throws IOException {
        this.chatClient = chatClientBuilder.build();
        this.vectorService = vectorService;
        this.listService = listService;
        this.countService = countService;
        this.systemPromptTemplate = new PromptTemplate(systemPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.userPromptTemplate = new PromptTemplate(userPromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.rewritePromptTemplate = new PromptTemplate(rewritePromptResource.getContentAsString(StandardCharsets.UTF_8));
        this.rewriteEnabled = rewriteEnabled;
        this.shortQueryLength = shortQueryLength;
        this.topkShort = topkShort;
        this.topkMedium = topkMedium;
        this.topkLong = topkLong;
        this.minScoreShort = minScoreShort;
        this.minScoreDefault = minScoreDefault;
    }

    /**
     * 基于单个知识库回答用户问题
     *
     * @param knowledgeBaseId 知识库ID
     * @param question 用户问题
     * @return AI回答
     */
    public String answerQuestion(Long knowledgeBaseId, String question) {
        return answerQuestion(List.of(knowledgeBaseId), question);
    }

    /**
     * 基于多个知识库回答用户问题（RAG）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @return AI回答
     */
    public String answerQuestion(List<Long> knowledgeBaseIds, String question) {
        log.info("收到知识库提问: kbIds={}, question={}", knowledgeBaseIds, question);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizeQuestion(question).isBlank()) {
            return NO_RESULT_RESPONSE;
        }

        // 1. 验证知识库是否存在并更新问题计数（合并数据库操作）
        countService.updateQuestionCounts(knowledgeBaseIds);

        // 2. Query rewrite + 动态参数检索（RAG）
        QueryContext queryContext = buildQueryContext(question); //根据问题来构造query上下文
        List<Document> relevantDocs = retrieveRelevantDocs(queryContext, knowledgeBaseIds); //拿到检索过后相似的向量所对应的内容

        if (!hasEffectiveHit(question, relevantDocs)) {
            return NO_RESULT_RESPONSE;
        }

        // 3. 构建上下文（合并检索到的文档）
        String context = relevantDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        log.debug("检索到 {} 个相关文档片段", relevantDocs.size());

        // 4. 构建提示词
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(context, question); //实际上也就是把检索出来的文本片段加入提示词罢了,因为大模型是个黑箱

        try {
            // 5. 调用AI生成回答
            String answer = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
            answer = normalizeAnswer(answer);

            log.info("知识库问答完成: kbIds={}", knowledgeBaseIds);
            return answer;

        } catch (Exception e) {
            log.error("知识库问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.KNOWLEDGE_BASE_QUERY_FAILED, "知识库查询失败：" + e.getMessage());
        }
    }

    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return systemPromptTemplate.render();
    }

    /**
     * 构建用户提示词
     */
    private String buildUserPrompt(String context, String question) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("context", context);
        variables.put("question", question);
        return userPromptTemplate.render(variables);
    }

    /**
     * 查询知识库并返回完整响应
     */
    public QueryResponse queryKnowledgeBase(QueryRequest request) {
        String answer = answerQuestion(request.knowledgeBaseIds(), request.question()); //查询知识库并获得相应

        // 获取知识库名称（多个知识库用逗号分隔）
        List<String> kbNames = listService.getKnowledgeBaseNames(request.knowledgeBaseIds());
        String kbNamesStr = String.join("、", kbNames);

        // 使用第一个知识库ID作为主要标识（兼容前端）
        Long primaryKbId = request.knowledgeBaseIds().getFirst();

        return new QueryResponse(answer, primaryKbId, kbNamesStr);
    }

    /**
     * 流式查询知识库（SSE）
     *
     * @param knowledgeBaseIds 知识库ID列表
     * @param question 用户问题
     * @return 流式响应
     */
    public Flux<String> answerQuestionStream(List<Long> knowledgeBaseIds, String question) {
        log.info("收到知识库流式提问: kbIds={}, question={}", knowledgeBaseIds, question);
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || normalizeQuestion(question).isBlank()) {
            return Flux.just(NO_RESULT_RESPONSE);
        }

        try {
            // 1. 验证知识库是否存在并更新问题计数
            countService.updateQuestionCounts(knowledgeBaseIds);

            // 2. Query rewrite + 动态参数检索
            QueryContext queryContext = buildQueryContext(question);
            List<Document> relevantDocs = retrieveRelevantDocs(queryContext, knowledgeBaseIds);

            if (!hasEffectiveHit(question, relevantDocs)) {
                //为了避免短query输入大模型，大模型给出有效信息不足等回答，将query进行长短识别
                //如果是长query就直接返回true，否则就要检查这个短 query 本身，是否字面出现在检索出的文档文本里，这保证材料至少不是明显弱相关。
                return Flux.just(NO_RESULT_RESPONSE);
            }

            // 3. 构建上下文
            String context = relevantDocs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n")); //用 --- 分隔

            log.debug("检索到 {} 个相关文档片段", relevantDocs.size());

            // 4. 构建提示词
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(context, question);

            // 5. 流式调用 + 探测窗口归一化：既保留流式速度，又避免无信息长文
            Flux<String> responseFlux = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()//构建出来的是一个 Flux<String> 发布源
                    .content();

            log.info("开始流式输出知识库回答(探测窗口): kbIds={}", knowledgeBaseIds);
            return normalizeStreamOutput(responseFlux)
                .doOnComplete(() -> log.info("流式输出完成: kbIds={}", knowledgeBaseIds))
                .onErrorResume(e -> {
                    log.error("流式输出失败: kbIds={}, error={}", knowledgeBaseIds, e.getMessage(), e);
                    return Flux.just("【错误】知识库查询失败：AI服务暂时不可用，请稍后重试。");
                });

        } catch (Exception e) {
            log.error("知识库流式问答失败: {}", e.getMessage(), e);
            return Flux.just("【错误】知识库查询失败：" + e.getMessage());
        }
    }

    private QueryContext buildQueryContext(String originalQuestion) {
        String normalizedQuestion = normalizeQuestion(originalQuestion); //把问题字符串格式化
        String rewrittenQuestion = rewriteQuestion(normalizedQuestion); //用大模型重写原问题
        Set<String> candidates = new LinkedHashSet<>(); //重写后的问题 + 原问题
        candidates.add(rewrittenQuestion);
        candidates.add(normalizedQuestion);

        SearchParams searchParams = resolveSearchParams(normalizedQuestion); //根据问题决定搜索参数，包括topk和打分最低阈值
        return new QueryContext(normalizedQuestion, new ArrayList<>(candidates), searchParams); //返回query的上下文，包含问题和搜索参数
    }

    private String normalizeQuestion(String question) {
        return question == null ? "" : question.trim();
    }

    //检索相关的doc
    private List<Document> retrieveRelevantDocs(QueryContext queryContext, List<Long> knowledgeBaseIds) {
        for (String candidateQuery : queryContext.candidateQueries()) {
            if (candidateQuery.isBlank()) {
                continue;
            }
            // similaritySearch 返回的是“最多 topK 个、且满足阈值和过滤条件的、与 query 语义最相近的 Document”
            List<Document> docs = vectorService.similaritySearch(
                candidateQuery,
                knowledgeBaseIds,
                queryContext.searchParams().topK(),
                queryContext.searchParams().minScore()
            );
            log.info("检索候选 query='{}'，命中 {} 条", candidateQuery, docs.size());
            if (hasEffectiveHit(candidateQuery, docs)) {
                return docs;
            }
        }
        return List.of();
    }

    private SearchParams resolveSearchParams(String question) {
        // 将 question 中所有空格、tab、换行等空白字符全部删掉，再计算剩余字符个数，并赋值给 compactLength。
        int compactLength = question.replaceAll("\\s+", "").length();
        if (compactLength <= shortQueryLength) {
            return new SearchParams(topkShort, minScoreShort); //短问题取向量库的向量个数更多，因为短问题信息少，语义不完整，所以放宽召回范围
        }
        if (compactLength <= 12) {
            return new SearchParams(topkMedium, minScoreDefault);
        }
        return new SearchParams(topkLong, minScoreDefault);
    }

    private String rewriteQuestion(String question) {
        if (!rewriteEnabled || question.isBlank()) {
            return question;
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("question", question);
            String rewritePrompt = rewritePromptTemplate.render(variables); //render是把prompt中的对应key占位符替换为variables中的kv
            String rewritten = chatClient.prompt()
                .user(rewritePrompt)
                .call()
                .content();//调用一次大模型，重写prompt
            if (rewritten == null || rewritten.isBlank()) {
                return question;
            }
            String normalized = rewritten.trim();
            log.info("Query rewrite: origin='{}', rewritten='{}'", question, normalized);
            return normalized;
        } catch (Exception e) {
            log.warn("Query rewrite 失败，使用原问题继续检索: {}", e.getMessage());
            return question;
        }
    }

    /**
     * 检索命中不等于可回答。
     * 不是短 token 问题就直接命中;
     * 对短 token 场景增加一次命中确认，就是检查该问题是否有在检索出的doc里出现;
     * 避免把弱相关片段交给模型后生成大段“信息不足说明”。
     */
    private boolean hasEffectiveHit(String question, List<Document> docs) {
        if (docs == null || docs.isEmpty()) { // docs是检索出来的相关向量
            return false;
        }

        String normalized = normalizeQuestion(question);
        if (!isShortTokenQuery(normalized)) {
            return true;
        }

        String loweredToken = normalized.toLowerCase();
        for (Document doc : docs) {
            String text = doc.getText();
            if (text != null && text.toLowerCase().contains(loweredToken)) {
                return true;
            }
        }

        log.info("短 query 命中确认失败，视为无有效结果: question='{}', docs={}", normalized, docs.size());
        return false;
    }

    // 检查question的格式: 只能由“字母、数字、下划线、连字符”组成，且字符长度必须在 2 到 20 之间。也就是短问题的字符长度是2-20(一个中文算一个字符)
    private boolean isShortTokenQuery(String question) {
        if (question == null) {
            return false;
        }
        String compact = question.trim();
        return SHORT_TOKEN_PATTERN.matcher(compact).matches();
    }

    private String normalizeAnswer(String answer) {
        if (answer == null || answer.isBlank()) {
            return NO_RESULT_RESPONSE;
        }
        String normalized = answer.trim();
        if (isNoResultLike(normalized)) {
            return NO_RESULT_RESPONSE;
        }
        return normalized;
    }

    private boolean isNoResultLike(String text) {
        return text.contains("没有找到相关信息")
            || text.contains("未检索到相关信息")
            || text.contains("信息不足")
            || text.contains("超出知识库范围")
            || text.contains("无法根据提供内容回答");
    }

    /**
     * 先观察前一小段流式内容，快速识别“无信息”模板。
     * - 命中无信息：立即输出固定模板并结束，防止长篇拒答
     * - 非无信息：尽快释放缓冲并继续实时透传
     */
    private Flux<String> normalizeStreamOutput(Flux<String> rawFlux) {
        return Flux.create(sink -> { //新建了一个包装后的 Flux,加了拦截与修正
            StringBuilder probeBuffer = new StringBuilder();
            /*
            lambda 捕获的局部变量必须是必须是 final 或 effectively final
            lambda 本质上会被编译成某个函数式接口抽象方法的实现。
            当 lambda 使用方法中的局部变量时，它并不是直接持有这个局部变量本身，而是捕获它当时的值。
            由于局部变量存活在当前方法的栈帧里，方法执行结束后该局部变量就不存在了，而 lambda 可能在之后才执行，所以它只能保存一个副本。
            如果这个局部变量后面还允许变化，那么 lambda 中保存的值就会和外部变量的新值产生不一致，语义上会变得混乱。
            因此，Java 要求被 lambda 捕获的局部变量必须是 final 或 effectively final。
            AtomicBoolean本身不会变, 它是一个包装对象, 包装的的值会变
             */
            AtomicBoolean passthrough = new AtomicBoolean(false);
            AtomicBoolean completed = new AtomicBoolean(false);
            final Disposable[] disposableRef = new Disposable[1]; //这个也是,Disposable[]是不可变的,但是里面的元素可以变

            disposableRef[0] = rawFlux.subscribe( //rawFlux是一个发布者,
                chunk -> { //订阅了如下的逻辑
                    if (completed.get() || sink.isCancelled()) { //如果结束了或者这个flux被取消订阅了
                        return;
                    }
                    if (passthrough.get()) { //还在传输
                        sink.next(chunk);
                        return;
                    }

                    probeBuffer.append(chunk);
                    String probeText = probeBuffer.toString();
                    if (isNoResultLike(probeText)) {
                        completed.set(true);
                        sink.next(NO_RESULT_RESPONSE);
                        sink.complete();
                        if (disposableRef[0] != null) {
                            disposableRef[0].dispose();
                        }
                        return;
                    }

                    if (probeBuffer.length() >= STREAM_PROBE_CHARS) {
                        passthrough.set(true);
                        sink.next(probeText);
                        probeBuffer.setLength(0);
                    }
                }, //onNext
                sink::error,//onError
                () -> {
                    if (completed.get() || sink.isCancelled()) {
                        return;
                    }
                    if (!passthrough.get()) {
                        sink.next(normalizeAnswer(probeBuffer.toString()));
                    }
                    sink.complete();
                }//onComplete
            );

            sink.onCancel(() -> {
                if (disposableRef[0] != null) {
                    disposableRef[0].dispose();
                }
            });
        });
    }

}

