package interview.guide.modules.knowledgebase.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 向量存储Repository
 * 负责向量数据的增删改查操作
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class VectorRepository {
    //直接使用JDBC来执行SQL
    private final JdbcTemplate jdbcTemplate;
    
    /**
     * 删除指定知识库的所有向量数据
     * 使用 SQL 直接删除，利用数据库索引和删除能力
     * <p>
     * Spring AI PgVectorStore 默认表名为 vector_store，元数据存储在 metadata 字段（JSONB类型）
     * 
     * @param knowledgeBaseId 知识库ID
     * @return 删除的行数
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByKnowledgeBaseId(Long knowledgeBaseId) {
        log.info("开始删除知识库向量数据: kbId={}", knowledgeBaseId);
        
        /* 
         * 注意：
         * 1. metadata 字段是 json 类型，不支持 jsonb_exists 函数。jsonb_exists 是一个 只针对 jsonb 的存在性检查函数。
         * 2. 使用 metadata->>'key' IS NOT NULL 来替代键存在性检查，这在 json/jsonb 下都有效。
         *    分三步看：1.先从 metadata 里取字段 kb_id_long 2.取出来后变成文本text 3.判断这个结果是不是 NULL，是就返回true用于where判断
         * 3. 这种写法完全避开了 PostgreSQL 的 '?' 操作符，不会引起 JDBC 占位符冲突。
         *    这里说的 ?，不是 SQL 里的参数占位符，而是 PostgreSQL 的一个 jsonb 存在性运算符，检查某个字符串是否存在于 JSONB 顶层 key 或数组元素中
         *    但是SQL中的 ? 是 JDBC 的参数占位符，可能会识别错误。所以就不使用 PostgreSQL 的 ? 操作符，避免冲突。
         */
        /*
            ->> 是 PostgreSQL 里取 JSON / JSONB 字段并返回文本（text） 的运算符。
            jsonb 会把 JSON 转成分解后的二进制结构，提高查询效率。并且支持更多索引和专用运算符
            ? 是 JDBC 预编译 SQL 的参数占位符。
            ::bigint 是 PostgreSQL 的类型转换（cast）语法。
         */
        String sql = """
            DELETE FROM vector_store
            WHERE metadata->>'kb_id' = ?
               OR (metadata->>'kb_id_long' IS NOT NULL AND (metadata->>'kb_id_long')::bigint = ?)
            """;
        
        try {
            // 第一个参数转为 String 匹配 kb_id，第二个参数保持 Long 匹配 kb_id_long
            // JdbcTemplate.update(...) 用于执行：
            // INSERT, UPDATE, DELETE
            int deletedRows = jdbcTemplate.update(sql, knowledgeBaseId.toString(), knowledgeBaseId);
            
            if (deletedRows > 0) {
                log.info("成功删除知识库向量数据: kbId={}, 删除行数={}", knowledgeBaseId, deletedRows);
            } else {
                log.info("未找到相关向量数据，无需删除: kbId={}", knowledgeBaseId);
            }
            
            return deletedRows;
            
        } catch (Exception e) {
            log.error("执行删除向量 SQL 失败: kbId={}, error={}", knowledgeBaseId, e.getMessage());
            // 抛出异常以触发事务回滚
            throw new RuntimeException("删除向量数据失败", e);
        }
    }    
}

