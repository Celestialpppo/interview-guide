package interview.guide.modules.knowledgebase.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库实体
 */
@Setter
@Getter
@Entity
@Table(name = "knowledge_bases", indexes = {
    @Index(name = "idx_kb_hash", columnList = "fileHash", unique = true),
    @Index(name = "idx_kb_category", columnList = "category")
})
public class KnowledgeBaseEntity {

    // Getters and Setters
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 文件内容的SHA-256哈希值，用于去重
    @Column(nullable = false, unique = true, length = 64)
    private String fileHash;

    // 知识库名称（用户自定义或从文件名提取）
    @Column(nullable = false)
    private String name;

    // 分类/分组（如"Java面试"、"项目文档"等）
    @Column(length = 100)
    private String category;

    // 原始文件名
    @Column(nullable = false)
    private String originalFilename;
    
    // 文件大小（字节）
    private Long fileSize;
    
    // 文件类型
    private String contentType;
    
    // RustFS存储的文件Key
    @Column(length = 500)
    private String storageKey;
    
    // RustFS存储的文件URL
    @Column(length = 1000)
    private String storageUrl;
    
    // 上传时间
    @Column(nullable = false)
    private LocalDateTime uploadedAt;
    
    // 最后访问时间
    private LocalDateTime lastAccessedAt;
    
    // 访问次数
    private Integer accessCount = 0;
    
    // 问题数量（用户针对此知识库提问的次数）
    private Integer questionCount = 0;

    // 向量化状态（新上传时为 PENDING，异步处理完成后变为 COMPLETED）
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private VectorStatus vectorStatus = VectorStatus.PENDING;

    // 向量化错误信息（失败时记录）
    @Column(length = 500)
    private String vectorError;

    // 向量分块数量
    private Integer chunkCount = 0;
    
    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        lastAccessedAt = LocalDateTime.now();
        accessCount = 1;
    }

    public void incrementAccessCount() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }
    
    public void incrementQuestionCount() {
        this.questionCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }

}

