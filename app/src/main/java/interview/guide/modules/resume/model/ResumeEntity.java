package interview.guide.modules.resume.model;

import interview.guide.common.model.AsyncTaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 简历实体
 * Resume Entity for deduplication and persistence
 */
@Getter
@Setter
@NoArgsConstructor
@Entity //这个类是 JPA 管理的实体，要映射到数据库表。
@Table(name = "resumes", indexes = {
        @Index(name = "idx_resume_hash", columnList = "fileHash", unique = true)
})
public class ResumeEntity {

    @Id //主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)//主键值由数据库自增生成。
    private Long id;

    // 文件内容的SHA-256哈希值，用于去重
    @Column(nullable = false, unique = true, length = 64)//一列的定义，数据库字段类型，JPA 通常会根据 Java 属性类型自动推断。
    private String fileHash;

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

    // 解析后的简历文本
    @Column(columnDefinition = "TEXT")
    private String resumeText;

    // 上传时间
    @Column(nullable = false)
    private LocalDateTime uploadedAt;

    // 最后访问时间
    private LocalDateTime lastAccessedAt;

    // 访问次数
    private Integer accessCount = 0;

    // 分析状态（新上传时为 PENDING，异步分析完成后变为 COMPLETED）
    @Enumerated(EnumType.STRING) //枚举字段存数据库时，用字符串保存，而不是数字下标。
    @Column(length = 20)
    private AsyncTaskStatus analyzeStatus = AsyncTaskStatus.PENDING;

    // 分析错误信息（失败时记录）
    @Column(length = 500)
    private String analyzeError;

    @PrePersist //在对象第一次插入数据库之前，自动执行这个方法。
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
        lastAccessedAt = LocalDateTime.now();
        accessCount = 1;
    }

    public void incrementAccessCount() {
        this.accessCount++;
        this.lastAccessedAt = LocalDateTime.now();
    }
}