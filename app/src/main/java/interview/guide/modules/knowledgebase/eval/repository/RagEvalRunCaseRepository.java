package interview.guide.modules.knowledgebase.eval.repository;

import interview.guide.modules.knowledgebase.eval.model.RagEvalRunCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * run case 仓库。
 */
public interface RagEvalRunCaseRepository extends JpaRepository<RagEvalRunCaseEntity, Long> {

    List<RagEvalRunCaseEntity> findByRunIdOrderByIdAsc(Long runId);
}
