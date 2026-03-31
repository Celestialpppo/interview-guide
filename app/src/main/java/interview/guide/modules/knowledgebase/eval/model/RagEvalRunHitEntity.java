package interview.guide.modules.knowledgebase.eval.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 单次 case 检索命中明细。
 */
@Getter
@Setter
@Entity
@Table(name = "rag_eval_run_hits", indexes = {
    @Index(name = "idx_rag_eval_run_hit_case", columnList = "run_case_id"),
    @Index(name = "idx_rag_eval_run_hit_chunk", columnList = "chunkId")
})
public class RagEvalRunHitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_case_id", nullable = false)
    private RagEvalRunCaseEntity runCase;

    @Column(nullable = false)
    private Integer rank;

    @Column(nullable = false, length = 128)
    private String chunkId;

    private Double similarityScore;

    private Integer relevanceGrade;

    @Column(nullable = false)
    private Boolean matchedGold = false;

    @Column(length = 500)
    private String sourceName;

    @Lob
    private String chunkPreview;

    @Lob
    private String metadataJson;
}
