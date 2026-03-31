package interview.guide.modules.knowledgebase.eval.repository;

import interview.guide.modules.knowledgebase.eval.model.RagEvalRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 评测 run 仓库。
 */
public interface RagEvalRunRepository extends JpaRepository<RagEvalRunEntity, Long> {
}
