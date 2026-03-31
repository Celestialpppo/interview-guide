package interview.guide.modules.knowledgebase.eval.repository;

import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseQrelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * qrel 仓库。
 */
public interface RagEvalCaseQrelRepository extends JpaRepository<RagEvalCaseQrelEntity, Long> {

    List<RagEvalCaseQrelEntity> findByEvalCaseIdOrderByIdAsc(Long evalCaseId);
}
