package interview.guide.modules.resume.repository;

import interview.guide.modules.resume.model.ResumeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 简历Repository
 * JPA的任务：
 * 建立对象和表的映射关系
 * 把对象的增删改查转换成 SQL
 * 把 SQL 查询结果再转回对象
 */
@Repository //这个类或接口是数据访问层DAO组件。对数据库增删改查。
// 交给 Spring 管理，注册为一个Bean。
// 它会把底层数据库异常转换成 Spring 的统一数据访问异常体系
public interface ResumeRepository extends JpaRepository<ResumeEntity, Long> { //继承 JPA 已经写好的通用数据库操作能力。
    
    /**
     * 根据文件哈希查找简历（用于去重）
     * Spring Data JPA 会根据你写的方法名，自动推导查询逻辑。
     */
    Optional<ResumeEntity> findByFileHash(String fileHash);
    
    /**
     * 检查文件哈希是否存在
     */
    boolean existsByFileHash(String fileHash);
}
