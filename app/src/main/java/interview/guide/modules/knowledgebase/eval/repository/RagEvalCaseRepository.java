package interview.guide.modules.knowledgebase.eval.repository;

import interview.guide.modules.knowledgebase.eval.model.RagEvalCaseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 评测 case 仓库。
 */
public interface RagEvalCaseRepository extends JpaRepository<RagEvalCaseEntity, Long> {

    List<RagEvalCaseEntity> findByDatasetIdAndEnabledTrueOrderByIdAsc(Long datasetId);
}
