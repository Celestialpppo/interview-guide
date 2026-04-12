package interview.guide.modules.interview.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 面试答案实体
 */
@Setter
@Getter
@Entity
@Table(name = "interview_answers",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_interview_answer_session_question", columnNames = {"session_id", "question_index"})
    }, //表级别定义唯一约束，(session_id, question_index) 这对值组合唯一。
    indexes = {
        @Index(name = "idx_interview_answer_session_question", columnList = "session_id,question_index")
    })
public class InterviewAnswerEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // 关联的会话
    @ManyToOne(fetch = FetchType.LAZY) //一个InterviewSessionEntity会对应多个InterviewAnswerEntity
    @JoinColumn(name = "session_id", nullable = false) //指向InterviewSessionEntity的主键
    private InterviewSessionEntity session;
    
    // 问题索引
    @Column(name = "question_index")
    private Integer questionIndex;
    
    // 问题内容
    @Column(columnDefinition = "TEXT")
    private String question;
    
    // 问题类别
    private String category;
    
    // 用户答案
    @Column(columnDefinition = "TEXT")
    private String userAnswer;
    
    // 得分 (0-100)
    private Integer score;
    
    // 反馈
    @Column(columnDefinition = "TEXT")
    private String feedback;
    
    // 参考答案
    @Column(columnDefinition = "TEXT")
    private String referenceAnswer;
    
    // 关键点 (JSON)
    @Column(columnDefinition = "TEXT")
    private String keyPointsJson;
    
    // 回答时间
    @Column(nullable = false)
    private LocalDateTime answeredAt;
    
    @PrePersist
    protected void onCreate() {
        answeredAt = LocalDateTime.now();
    }

}
