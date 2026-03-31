package interview.guide.modules.knowledgebase.eval.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * RAG 评测单条 case。
 */
@Getter
@Setter
@Entity
@Table(name = "rag_eval_cases", indexes = {
    @Index(name = "idx_rag_eval_case_dataset", columnList = "dataset_id")
})
public class RagEvalCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dataset_id", nullable = false)
    private RagEvalDatasetEntity dataset;

    @Lob
    @Column(nullable = false)
    private String question;

    @Lob
    private String expectedAnswer;

    @Column(nullable = false, length = 2000)
    private String knowledgeBaseIdsJson;

    @Column(nullable = false)
    private Boolean enabled = true;
}
