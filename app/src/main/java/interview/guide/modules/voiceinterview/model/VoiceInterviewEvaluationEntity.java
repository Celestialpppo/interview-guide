package interview.guide.modules.voiceinterview.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "voice_interview_evaluations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VoiceInterviewEvaluationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", unique = true)
    private Long sessionId;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "overall_rating")
    private String overallRating;

    @Column(name = "tech_knowledge_score")
    private Integer techKnowledgeScore;

    @Column(name = "tech_knowledge_comment", columnDefinition = "TEXT")
    private String techKnowledgeComment;

    @Column(name = "project_exp_score")
    private Integer projectExpScore;

    @Column(name = "project_exp_comment", columnDefinition = "TEXT")
    private String projectExpComment;

    @Column(name = "communication_score")
    private Integer communicationScore;

    @Column(name = "communication_comment", columnDefinition = "TEXT")
    private String communicationComment;

    @Column(name = "logical_thinking_score")
    private Integer logicalThinkingScore;

    @Column(name = "logical_thinking_comment", columnDefinition = "TEXT")
    private String logicalThinkingComment;

    @Column(name = "improvement_suggestions", columnDefinition = "TEXT")
    private String improvementSuggestions; // JSON array

    @Column(name = "strengths_summary", columnDefinition = "TEXT")
    private String strengthsSummary;

    @Column(name = "interviewer_role")
    private String interviewerRole;

    @Column(name = "interview_date")
    private LocalDateTime interviewDate;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
