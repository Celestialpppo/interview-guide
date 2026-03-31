package interview.guide.modules.knowledgebase.eval.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RAG 评测执行任务。
 */
@Getter
@Setter
@Entity
@Table(name = "rag_eval_runs", indexes = {
    @Index(name = "idx_rag_eval_run_dataset", columnList = "dataset_id"),
    @Index(name = "idx_rag_eval_run_status", columnList = "status")
})
public class RagEvalRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private RagEvalDatasetEntity dataset;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RagEvalRunStatus status = RagEvalRunStatus.PENDING;

    @Lob
    private String retrieverConfigJson;

    @Lob
    private String generatorConfigJson;

    @Lob
    private String judgeConfigJson;

    @Lob
    private String summaryMetricsJson;

    @Column(length = 1000)
    private String failureMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime finishedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
