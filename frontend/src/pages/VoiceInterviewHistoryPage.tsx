import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Clock, Play, FileText } from 'lucide-react';
import { voiceInterviewApi, SessionMeta } from '../api/voiceInterview';

export default function VoiceInterviewHistoryPage() {
  const navigate = useNavigate();
  const [sessions, setSessions] = useState<SessionMeta[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadSessions();
  }, []);

  const loadSessions = async () => {
    try {
      const data = await voiceInterviewApi.getAllSessions();
      setSessions(data);
    } catch (error) {
      console.error('Failed to load sessions:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleResume = async (sessionId: number) => {
    try {
      await voiceInterviewApi.resumeSession(sessionId);
      navigate(`/voice-interview/${sessionId}`);
    } catch (error) {
      console.error('Failed to resume session:', error);
      alert('恢复会话失败');
    }
  };

  const formatDuration = (seconds?: number) => {
    if (!seconds) return '进行中';
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}分${secs}秒`;
  };

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      IN_PROGRESS: 'bg-blue-100 text-blue-700',
      PAUSED: 'bg-yellow-100 text-yellow-700',
      COMPLETED: 'bg-green-100 text-green-700',
      FAILED: 'bg-red-100 text-red-700'
    };

    const labels: Record<string, string> = {
      IN_PROGRESS: '进行中',
      PAUSED: '已暂停',
      COMPLETED: '已完成',
      FAILED: '失败'
    };

    return (
      <span className={`px-2 py-0.5 rounded-full text-xs ${styles[status] || 'bg-gray-100 text-gray-700'}`}>
        {labels[status] || status}
      </span>
    );
  };

  const getRoleLabel = (roleType: string) => {
    const roleMap: Record<string, string> = {
      'ali-p8': '阿里P8后端面试',
      'byteance-algo': '字节算法工程师面试',
      'tencent-backend': '腾讯后台开发面试'
    };
    return roleMap[roleType] || roleType;
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-primary-500 mx-auto"></div>
          <p className="mt-4 text-slate-600">加载中...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-100">
      <div className="max-w-6xl mx-auto p-6">
        <div className="mb-6">
          <h1 className="text-2xl font-bold text-slate-800">面试记录</h1>
          <p className="text-slate-600 mt-1">查看历史面试记录，继续未完成的面试</p>
        </div>

        {sessions.length === 0 ? (
          <div className="bg-white rounded-xl shadow-sm p-12 text-center">
            <FileText className="w-16 h-16 text-slate-300 mx-auto mb-4" />
            <p className="text-slate-600">暂无面试记录</p>
            <button
              onClick={() => navigate('/')}
              className="mt-4 px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
            >
              开始面试
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {sessions.map((session) => (
              <div
                key={session.sessionId}
                className="bg-white rounded-xl shadow-sm p-4 border border-slate-200 hover:shadow-md transition-shadow"
              >
                <div className="flex items-center justify-between">
                  <div className="flex-1">
                    <div className="flex items-center gap-3">
                      <h3 className="font-semibold text-lg text-slate-800">
                        {getRoleLabel(session.roleType)}
                      </h3>
                      {getStatusBadge(session.status)}
                    </div>

                    <div className="flex items-center gap-4 text-sm text-slate-600 mt-2">
                      <span className="flex items-center gap-1">
                        <Clock className="w-4 h-4" />
                        {new Date(session.createdAt).toLocaleString('zh-CN')}
                      </span>
                      <span>时长: {formatDuration(session.actualDuration)}</span>
                    </div>

                    <div className="mt-2 pt-2 border-t border-slate-100">
                      <p className="text-sm text-slate-600">
                        共 {session.messageCount} 条对话 · 当前阶段: {session.currentPhase}
                      </p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 ml-4">
                    {session.status === 'PAUSED' && (
                      <button
                        onClick={() => handleResume(session.sessionId)}
                        className="px-4 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600 flex items-center gap-2 transition-colors"
                      >
                        <Play className="w-4 h-4" />
                        继续面试
                      </button>
                    )}
                    {session.status === 'COMPLETED' && (
                      <button
                        onClick={() => navigate(`/voice-interview/${session.sessionId}/evaluation`)}
                        className="px-4 py-2 bg-slate-200 text-slate-700 rounded-lg hover:bg-slate-300 transition-colors"
                      >
                        查看报告
                      </button>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
