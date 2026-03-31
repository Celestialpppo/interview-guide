package interview.guide.modules.knowledgebase.eval.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RAG 评测数据集。
 */
@Getter
@Setter
@Entity
@Table(name = "rag_eval_datasets", indexes = {
    @Index(name = "idx_rag_eval_dataset_name", columnList = "name")
})
public class RagEvalDatasetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String version;

    @Column(length = 1000)
    private String description;

    @Column(length = 100)
    private String domain;

    @Column(nullable = false, length = 100)
    private String labelSource;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
