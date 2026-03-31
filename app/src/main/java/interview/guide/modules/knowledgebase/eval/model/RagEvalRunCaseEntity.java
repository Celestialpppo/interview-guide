package interview.guide.modules.knowledgebase.eval.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * 单次评测任务下的 case 结果。
 */
@Getter
@Setter
@Entity
@Table(name = "rag_eval_run_cases", indexes = {
    @Index(name = "idx_rag_eval_run_case_run", columnList = "run_id"),
    @Index(name = "idx_rag_eval_run_case_case", columnList = "eval_case_id")
})
public class RagEvalRunCaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private RagEvalRunEntity run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "eval_case_id", nullable = false)
    private RagEvalCaseEntity evalCase;

    @Lob
    private String originalQuery;

    @Lob
    private String rewrittenQuery;

    @Lob
    private String resolvedQuery;

    @Lob
    private String answerText;

    @Lob
    private String retrievalMetricsJson;

    @Lob
    private String generationMetricsJson;

    @Column(nullable = false)
    private Boolean overallPass = false;

    @Lob
    private String rawJudgeJson;
}
