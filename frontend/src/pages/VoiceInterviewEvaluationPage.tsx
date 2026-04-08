import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Download, Share2, Award, TrendingUp, MessageCircle, Target, CheckCircle } from 'lucide-react';
import { Evaluation, voiceInterviewApi } from '../api/voiceInterview';

export default function VoiceInterviewEvaluationPage() {
  const { sessionId } = useParams<{ sessionId: string }>();
  const navigate = useNavigate();
  const [evaluation, setEvaluation] = useState<Evaluation | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadEvaluation();
  }, [sessionId]);

  const loadEvaluation = async () => {
    if (!sessionId) return;

    setLoading(true);
    try {
      const data = await voiceInterviewApi.getEvaluation(parseInt(sessionId));
      setEvaluation(data);
    } catch (error) {
      console.error('Failed to load evaluation:', error);
    } finally {
      setLoading(false);
    }
  };

  const getScoreColor = (score: number) => {
    if (score >= 90) return 'text-green-600';
    if (score >= 80) return 'text-blue-600';
    if (score >= 70) return 'text-yellow-600';
    return 'text-red-600';
  };

  const getScoreBgColor = (score: number) => {
    if (score >= 90) return 'from-green-500 to-green-600';
    if (score >= 80) return 'from-blue-500 to-blue-600';
    if (score >= 70) return 'from-yellow-500 to-yellow-600';
    return 'from-red-500 to-red-600';
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100">
        <div className="text-center">
          <div className="w-16 h-16 border-4 border-slate-200 border-t-primary-500 rounded-full animate-spin mx-auto mb-4" />
          <p className="text-slate-600 text-lg">正在生成评估报告...</p>
        </div>
      </div>
    );
  }

  if (!evaluation) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-slate-50 to-slate-100">
        <div className="text-center">
          <p className="text-slate-600 text-lg mb-4">评估报告加载失败</p>
          <button
            onClick={() => navigate('/voice-interview/history')}
            className="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
          >
            返回列表
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-50 to-slate-100">
      {/* Header */}
      <div className="bg-white border-b border-slate-200 sticky top-0 z-10">
        <div className="max-w-6xl mx-auto px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-4">
            <button
              onClick={() => navigate('/voice-interview/history')}
              className="p-2 hover:bg-slate-100 rounded-full transition-colors"
            >
              <ArrowLeft className="w-5 h-5 text-slate-600" />
            </button>
            <div>
              <h1 className="text-xl font-bold text-slate-800">面试评估报告</h1>
              <p className="text-sm text-slate-500">会话 ID: {sessionId}</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {/* TODO: 实现下载功能 */}
            <button className="px-4 py-2 bg-slate-100 text-slate-700 rounded-lg hover:bg-slate-200 flex items-center gap-2 transition-colors">
              <Download className="w-4 h-4" />
              下载报告
            </button>
            {/* TODO: 实现分享功能 */}
            <button className="px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 flex items-center gap-2 transition-colors">
              <Share2 className="w-4 h-4" />
              分享
            </button>
          </div>
        </div>
      </div>

      <div className="max-w-6xl mx-auto px-6 py-8">
        {/* Overall Score Card */}
        <div className="bg-white rounded-2xl shadow-lg p-8 mb-6 border border-slate-200">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-6">
              <div className={`w-32 h-32 rounded-full bg-gradient-to-br ${getScoreBgColor(evaluation.overallScore)} flex items-center justify-center shadow-lg`}>
                <div className="text-center">
                  <div className="text-4xl font-bold text-white">{evaluation.overallScore}</div>
                  <div className="text-xs text-white/80 mt-1">总分</div>
                </div>
              </div>
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <Award className="w-6 h-6 text-yellow-500" />
                  <span className="text-2xl font-bold text-slate-800">{evaluation.overallRating}</span>
                </div>
                <p className="text-slate-600 max-w-lg leading-relaxed">
                  {evaluation.strengthsSummary}
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Dimension Scores */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
          {/* Tech Knowledge */}
          <div className="bg-white rounded-xl shadow-md p-6 border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-blue-100 rounded-lg flex items-center justify-center">
                  <Target className="w-5 h-5 text-blue-600" />
                </div>
                <h3 className="font-semibold text-slate-800">技术知识</h3>
              </div>
              <span className={`text-2xl font-bold ${getScoreColor(evaluation.techKnowledge.score)}`}>
                {evaluation.techKnowledge.score}
              </span>
            </div>
            <div className="w-full bg-slate-100 rounded-full h-2 mb-3">
              <div
                className="bg-blue-500 h-2 rounded-full transition-all"
                style={{ width: `${evaluation.techKnowledge.score}%` }}
              />
            </div>
            <p className="text-sm text-slate-600 leading-relaxed">
              {evaluation.techKnowledge.comment}
            </p>
          </div>

          {/* Project Experience */}
          <div className="bg-white rounded-xl shadow-md p-6 border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-green-100 rounded-lg flex items-center justify-center">
                  <TrendingUp className="w-5 h-5 text-green-600" />
                </div>
                <h3 className="font-semibold text-slate-800">项目经验</h3>
              </div>
              <span className={`text-2xl font-bold ${getScoreColor(evaluation.projectExp.score)}`}>
                {evaluation.projectExp.score}
              </span>
            </div>
            <div className="w-full bg-slate-100 rounded-full h-2 mb-3">
              <div
                className="bg-green-500 h-2 rounded-full transition-all"
                style={{ width: `${evaluation.projectExp.score}%` }}
              />
            </div>
            <p className="text-sm text-slate-600 leading-relaxed">
              {evaluation.projectExp.comment}
            </p>
          </div>

          {/* Communication */}
          <div className="bg-white rounded-xl shadow-md p-6 border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-purple-100 rounded-lg flex items-center justify-center">
                  <MessageCircle className="w-5 h-5 text-purple-600" />
                </div>
                <h3 className="font-semibold text-slate-800">沟通能力</h3>
              </div>
              <span className={`text-2xl font-bold ${getScoreColor(evaluation.communication.score)}`}>
                {evaluation.communication.score}
              </span>
            </div>
            <div className="w-full bg-slate-100 rounded-full h-2 mb-3">
              <div
                className="bg-purple-500 h-2 rounded-full transition-all"
                style={{ width: `${evaluation.communication.score}%` }}
              />
            </div>
            <p className="text-sm text-slate-600 leading-relaxed">
              {evaluation.communication.comment}
            </p>
          </div>

          {/* Logical Thinking */}
          <div className="bg-white rounded-xl shadow-md p-6 border border-slate-200">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 bg-orange-100 rounded-lg flex items-center justify-center">
                  <Target className="w-5 h-5 text-orange-600" />
                </div>
                <h3 className="font-semibold text-slate-800">逻辑思维</h3>
              </div>
              <span className={`text-2xl font-bold ${getScoreColor(evaluation.logicalThinking.score)}`}>
                {evaluation.logicalThinking.score}
              </span>
            </div>
            <div className="w-full bg-slate-100 rounded-full h-2 mb-3">
              <div
                className="bg-orange-500 h-2 rounded-full transition-all"
                style={{ width: `${evaluation.logicalThinking.score}%` }}
              />
            </div>
            <p className="text-sm text-slate-600 leading-relaxed">
              {evaluation.logicalThinking.comment}
            </p>
          </div>
        </div>

        {/* Improvement Suggestions */}
        <div className="bg-white rounded-xl shadow-md p-6 border border-slate-200 mb-6">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 bg-yellow-100 rounded-lg flex items-center justify-center">
              <CheckCircle className="w-5 h-5 text-yellow-600" />
            </div>
            <h3 className="font-semibold text-slate-800 text-lg">改进建议</h3>
          </div>
          <ul className="space-y-3">
            {evaluation.improvementSuggestions.map((suggestion, index) => (
              <li key={index} className="flex gap-3 items-start">
                <div className="w-6 h-6 bg-yellow-100 rounded-full flex items-center justify-center flex-shrink-0 mt-0.5">
                  <span className="text-xs font-bold text-yellow-700">{index + 1}</span>
                </div>
                <p className="text-slate-700 leading-relaxed flex-1">{suggestion}</p>
              </li>
            ))}
          </ul>
        </div>

        {/* TODO: 对话历史回顾 */}
        <div className="bg-white rounded-xl shadow-md p-6 border border-slate-200">
          <div className="flex items-center gap-3 mb-4">
            <div className="w-10 h-10 bg-slate-100 rounded-lg flex items-center justify-center">
              <MessageCircle className="w-5 h-5 text-slate-600" />
            </div>
            <h3 className="font-semibold text-slate-800 text-lg">对话历史回顾</h3>
          </div>
          <div className="text-center py-8 text-slate-500">
            <p>对话历史回顾功能开发中...</p>
            <p className="text-sm mt-2">TODO: 实现对话历史查看功能</p>
          </div>
        </div>
      </div>
    </div>
  );
}
