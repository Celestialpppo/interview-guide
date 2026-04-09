import {useEffect, useRef, useState} from 'react';
import {motion} from 'framer-motion';
import {interviewApi} from '../api/interview';
import ConfirmDialog from '../components/ConfirmDialog';
import InterviewChatPanel from '../components/InterviewChatPanel';
import type {InterviewQuestion, InterviewSession} from '../types/interview';
import type {Difficulty} from '../components/UnifiedInterviewModal';

interface Message {
  type: 'interviewer' | 'user';
  content: string;
  category?: string;
  questionIndex?: number;
}

interface InterviewProps {
  resumeText: string;
  resumeId?: number;
  initialConfig?: {
    questionCount?: number;
    llmProvider?: string;
    skillId?: string;
    difficulty?: Difficulty;
  };
  onBack: () => void;
  onInterviewComplete: () => void;
}

export default function Interview({
  resumeText,
  resumeId,
  initialConfig,
  onBack,
  onInterviewComplete,
}: InterviewProps) {
  const [session, setSession] = useState<InterviewSession | null>(null);
  const [currentQuestion, setCurrentQuestion] = useState<InterviewQuestion | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [answer, setAnswer] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [error, setError] = useState('');
  const [isCreating, setIsCreating] = useState(false);
  const [showCompleteConfirm, setShowCompleteConfirm] = useState(false);
  const startedRef = useRef(false);

  const questionCount = initialConfig?.questionCount ?? 8;
  const llmProvider = initialConfig?.llmProvider ?? 'dashscope';
  const skillId = initialConfig?.skillId ?? 'java-backend';
  const difficulty = initialConfig?.difficulty ?? 'mid';

  // 自动开始面试（检查未完成会话 → 恢复或新建）
  useEffect(() => {
    if (!startedRef.current) {
      startedRef.current = true;
      startInterview();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const startInterview = async () => {
    setIsCreating(true);
    setError('');

    try {
      // 如果有 resumeId，先检查是否有未完成的面试可以恢复
      if (resumeId) {
        const unfinished = await interviewApi.findUnfinishedSession(resumeId);
        if (unfinished) {
          restoreSession(unfinished);
          return;
        }
      }

      // 创建新面试
      const newSession = await interviewApi.createSession({
        resumeText,
        questionCount,
        resumeId,
        llmProvider,
        skillId,
        difficulty,
      });

      setSession(newSession);

      if (newSession.questions.length > 0) {
        const firstQuestion = newSession.questions[0];
        setCurrentQuestion(firstQuestion);
        setMessages([{
          type: 'interviewer',
          content: firstQuestion.question,
          category: firstQuestion.category,
          questionIndex: 0
        }]);
      }
    } catch (err) {
      setError('创建面试失败，请重试');
      console.error(err);
    } finally {
      setIsCreating(false);
    }
  };

  const restoreSession = (sessionToRestore: InterviewSession) => {
    setSession(sessionToRestore);

    const currentQ = sessionToRestore.questions[sessionToRestore.currentQuestionIndex];
    if (currentQ) {
      setCurrentQuestion(currentQ);

      if (currentQ.userAnswer) {
        setAnswer(currentQ.userAnswer);
      }

      // 恢复消息历史
      const restoredMessages: Message[] = [];
      for (let i = 0; i <= sessionToRestore.currentQuestionIndex; i++) {
        const q = sessionToRestore.questions[i];
        restoredMessages.push({
          type: 'interviewer',
          content: q.question,
          category: q.category,
          questionIndex: i
        });
        if (q.userAnswer) {
          restoredMessages.push({
            type: 'user',
            content: q.userAnswer
          });
        }
      }
      setMessages(restoredMessages);
    }
  };

  const handleSubmitAnswer = async () => {
    if (!answer.trim() || !session || !currentQuestion) return;

    setIsSubmitting(true);

    const userMessage: Message = {
      type: 'user',
      content: answer
    };
    setMessages(prev => [...prev, userMessage]);

    try {
      const response = await interviewApi.submitAnswer({
        sessionId: session.sessionId,
        questionIndex: currentQuestion.questionIndex,
        answer: answer.trim()
      });

      setAnswer('');

      if (response.hasNextQuestion && response.nextQuestion) {
        setCurrentQuestion(response.nextQuestion);
        setMessages(prev => [...prev, {
          type: 'interviewer',
          content: response.nextQuestion!.question,
          category: response.nextQuestion!.category,
          questionIndex: response.nextQuestion!.questionIndex
        }]);
      } else {
        onInterviewComplete();
      }
    } catch (err) {
      setError('提交答案失败，请重试');
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleCompleteEarly = async () => {
    if (!session) return;

    setIsSubmitting(true);
    try {
      await interviewApi.completeInterview(session.sessionId);
      setShowCompleteConfirm(false);
      onInterviewComplete();
    } catch (err) {
      setError('提前交卷失败，请重试');
      console.error(err);
    } finally {
      setIsSubmitting(false);
    }
  };

  // 加载中
  if (isCreating) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <div className="w-10 h-10 border-3 border-slate-200 border-t-primary-500 rounded-full mx-auto mb-4 animate-spin" />
          <p className="text-slate-500 dark:text-slate-400">正在生成面试题目...</p>
        </div>
      </div>
    );
  }

  // 错误状态
  if (error && !session) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="text-center">
          <p className="text-red-500 dark:text-red-400 mb-4">{error}</p>
          <div className="flex gap-3 justify-center">
            <button
              onClick={startInterview}
              className="px-5 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
            >
              重试
            </button>
            <button
              onClick={onBack}
              className="px-5 py-2 bg-slate-200 dark:bg-slate-700 text-slate-700 dark:text-slate-300 rounded-lg hover:bg-slate-300 dark:hover:bg-slate-600"
            >
              返回
            </button>
          </div>
        </div>
      </div>
    );
  }

  if (!session || !currentQuestion) return null;

  return (
    <div className="pb-10">
      {/* 页面头部 */}
      <motion.div
        className="text-center mb-10"
        initial={{ opacity: 0, y: -20 }}
        animate={{ opacity: 1, y: 0 }}
      >
        <h1 className="text-3xl font-bold text-slate-900 dark:text-white mb-2 flex items-center justify-center gap-3">
          <div className="w-12 h-12 bg-gradient-to-br from-primary-500 to-primary-600 rounded-xl flex items-center justify-center">
            <svg className="w-6 h-6 text-white" viewBox="0 0 24 24" fill="none">
              <path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <path d="M19 10v2a7 7 0 0 1-14 0v-2" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <line x1="12" y1="19" x2="12" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
              <line x1="8" y1="23" x2="16" y2="23" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </div>
          模拟面试
        </h1>
        <p className="text-slate-500 dark:text-slate-400">认真回答每个问题，展示您的实力</p>
      </motion.div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ duration: 0.3 }}
      >
        <InterviewChatPanel
          session={session}
          currentQuestion={currentQuestion}
          messages={messages}
          answer={answer}
          onAnswerChange={setAnswer}
          onSubmit={handleSubmitAnswer}
          onCompleteEarly={handleCompleteEarly}
          isSubmitting={isSubmitting}
          showCompleteConfirm={showCompleteConfirm}
          onShowCompleteConfirm={setShowCompleteConfirm}
        />
      </motion.div>

      {/* 提前交卷确认对话框 */}
      <ConfirmDialog
        open={showCompleteConfirm}
        title="提前交卷"
        message="确定要提前交卷吗？未回答的问题将按0分计算。"
        confirmText="确定交卷"
        cancelText="取消"
        confirmVariant="warning"
        loading={isSubmitting}
        onConfirm={handleCompleteEarly}
        onCancel={() => setShowCompleteConfirm(false)}
      />
    </div>
  );
}
