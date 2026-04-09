package interview.guide.modules.interview.model;

/**
 * 面试问题DTO
 * type 由 Skill category key 驱动（如 MYSQL、CSS、DYNAMIC_PROGRAMMING 等），不再使用枚举
 */
public record InterviewQuestionDTO(
    int questionIndex,
    String question,
    String type,           // Skill category key，如 "MYSQL"、"CSS"、"DP"
    String category,       // 展示用标签，如 "MySQL"、"CSS"、"动态规划"
    String userAnswer,
    Integer score,
    String feedback,
    boolean isFollowUp,
    Integer parentQuestionIndex
) {
    public static InterviewQuestionDTO create(int index, String question, String type, String category) {
        return new InterviewQuestionDTO(index, question, type, category, null, null, null, false, null);
    }

    public static InterviewQuestionDTO create(int index, String question, String type, String category,
                                               boolean isFollowUp, Integer parentQuestionIndex) {
        return new InterviewQuestionDTO(index, question, type, category, null, null, null, isFollowUp, parentQuestionIndex);
    }

    public InterviewQuestionDTO withAnswer(String answer) {
        return new InterviewQuestionDTO(questionIndex, question, type, category, answer, score, feedback, isFollowUp, parentQuestionIndex);
    }

    public InterviewQuestionDTO withEvaluation(int score, String feedback) {
        return new InterviewQuestionDTO(questionIndex, question, type, category, userAnswer, score, feedback, isFollowUp, parentQuestionIndex);
    }
}
