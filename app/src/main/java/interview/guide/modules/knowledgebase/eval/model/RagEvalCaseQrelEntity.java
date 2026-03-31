package interview.guide.modules.knowledgebase.eval.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * RAG case 的 gold relevance 标注。
 */
@Getter
@Setter
@Entity
@Table(name = "rag_eval_case_qrels", indexes = {
    @Index(name = "idx_rag_eval_qrel_case", columnList = "eval_case_id"),
    @Index(name = "idx_rag_eval_qrel_chunk", columnList = "chunkId")
})
public class RagEvalCaseQrelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eval_case_id", nullable = false)
    private RagEvalCaseEntity evalCase;

    @Column(nullable = false, length = 128)
    private String chunkId;

    @Column(nullable = false)
    private Integer relevanceGrade;
}
