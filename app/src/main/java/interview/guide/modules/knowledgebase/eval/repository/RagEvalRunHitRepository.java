package interview.guide.modules.knowledgebase.eval.repository;

import interview.guide.modules.knowledgebase.eval.model.RagEvalRunHitEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * run hit 仓库。
 */
public interface RagEvalRunHitRepository extends JpaRepository<RagEvalRunHitEntity, Long> {

    List<RagEvalRunHitEntity> findByRunCaseIdOrderByRankAsc(Long runCaseId);
}
