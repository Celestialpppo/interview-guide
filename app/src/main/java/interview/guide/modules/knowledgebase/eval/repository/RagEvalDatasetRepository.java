package interview.guide.modules.knowledgebase.eval.repository;

import interview.guide.modules.knowledgebase.eval.model.RagEvalDatasetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 评测数据集仓库。
 */
public interface RagEvalDatasetRepository extends JpaRepository<RagEvalDatasetEntity, Long> {
}
