import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Clock, PhoneOff, AlertCircle, Bot, Mic, ArrowLeft } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import AudioRecorder from '../components/AudioRecorder';
import RealtimeSubtitle from '../components/RealtimeSubtitle';
import { skillApi, type SkillDTO } from '../api/skill';
import { getTemplateName } from '../utils/voiceInterview';
import {
  voiceInterviewApi,
  connectWebSocket,
  VoiceInterviewWebSocket,
} from '../api/voiceInterview';

export default function VoiceInterviewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const entryState = (location.state as {
    voiceConfig?: {
      skillId: string;
      difficulty?: string;
      techEnabled: boolean;
      projectEnabled: boolean;
      hrEnabled: boolean;
      plannedDuration: number;
      resumeId?: number;
      llmProvider?: string;
    };
  } | null) || {};
  const presetVoiceConfig = entryState.voiceConfig;
  const queryParams = new URLSearchParams(location.search);
  const urlSkillId = queryParams.get('skillId') || undefined;
  const effectiveSkillId = presetVoiceConfig?.skillId ?? urlSkillId ?? 'java-backend';

  // UI state
  const [isRecording, setIsRecording] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [currentPhase, setCurrentPhase] = useState('INTRO');
  const [connectionStatus, setConnectionStatus] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');

  // Data state
  const [userText, setUserText] = useState('');
  const [aiText, setAiText] = useState('');
  const [messages, setMessages] = useState<{ role: 'user' | 'ai'; text: string; id: string }[]>([]);
  const [isAiSpeaking, setIsAiSpeaking] = useState(false);
  const [aiAudio, setAiAudio] = useState('');
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [templateName, setTemplateName] = useState<string>('');

  // Skills for template name lookup
  const [skills, setSkills] = useState<SkillDTO[]>([]);

  // Refs
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const wsRef = useRef<VoiceInterviewWebSocket | null>(null);
  const audioPlayerRef = useRef<HTMLAudioElement>(null);
  const pauseTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const autoStartRef = useRef(false);
  const blockMicToServerRef = useRef(false);

  // Load skills for template name display
  useEffect(() => {
    skillApi.listSkills().then(setSkills).catch(console.error);
  }, []);

  // Derive template name from skills
  useEffect(() => {
    if (skills.length > 0 && effectiveSkillId) {
      setTemplateName(getTemplateName(effectiveSkillId, skills));
    }
  }, [skills, effectiveSkillId]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
      if (wsRef.current) {
        wsRef.current.disconnect();
      }
    };
  }, []);

  // Start interview timer
  useEffect(() => {
    if (sessionId && connectionStatus === 'connected') {
      startTimer();
    } else if (timerRef.current) {
      clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [sessionId, connectionStatus]);

  // Auto-play audio when aiAudio changes
  useEffect(() => {
    if (aiAudio && audioPlayerRef.current) {
      const playPromise = audioPlayerRef.current.play();
      if (playPromise !== undefined) {
        playPromise.catch(() => {
          setError('请点击页面任意位置以启用音频播放');
        });
      }
    }
  }, [aiAudio]);

  const startTimer = () => {
    timerRef.current = setInterval(() => {
      setCurrentTime((prev) => prev + 1);
    }, 1000);
  };

  const formatTime = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  const getPhaseLabel = (phase: string) => {
    const phaseMap: Record<string, string> = {
      INTRO: '自我介绍',
      TECH: '技术问题',
      PROJECT: '项目深挖',
      HR: 'HR问题',
    };
    return phaseMap[phase] || phase;
  };

  const handlePhaseConfig = useCallback(async (config: {
    skillId: string;
    difficulty?: string;
    techEnabled: boolean;
    projectEnabled: boolean;
    hrEnabled: boolean;
    plannedDuration: number;
    resumeId?: number;
    llmProvider?: string;
  }) => {
    setError(null);
    setConnectionStatus('connecting');

    try {
      const session = await voiceInterviewApi.createSession({
        skillId: config.skillId,
        difficulty: config.difficulty,
        introEnabled: false,
        techEnabled: config.techEnabled,
        projectEnabled: config.projectEnabled,
        hrEnabled: config.hrEnabled,
        plannedDuration: config.plannedDuration,
        resumeId: config.resumeId,
        llmProvider: config.llmProvider,
      });

      setSessionId(session.sessionId);
      setCurrentPhase(session.currentPhase);

      const wsUrl = session.webSocketUrl || `ws://localhost:8080/ws/voice-interview/${session.sessionId}`;

      setTimeout(() => {
        try {
          wsRef.current = connectWebSocket(
            session.sessionId,
            wsUrl,
            {
              onOpen: () => {
                setConnectionStatus('connected');
              },
              onMessage: (_message) => {},
              onSubtitle: (text, isFinal) => {
                setUserText(text);
                if (isFinal && text.trim()) {
                  setMessages(prev => [
                    ...prev,
                    { role: 'user', text: text.trim(), id: Date.now().toString() }
                  ]);
                  setUserText('');
                }
              },
              onAudioResponse: (audioData, text) => {
                setAiAudio(audioData);
                setAiText(text);
                setIsAiSpeaking(true);
                blockMicToServerRef.current = !!(audioData && audioData.length > 0);
              },
              onClose: (event) => {
                setConnectionStatus('disconnected');
                if (event.code !== 1000) {
                  setError('连接已断开，请刷新页面重试');
                }
              },
              onError: () => {
                setError('WebSocket 连接错误，请检查网络后重试');
                setConnectionStatus('disconnected');
              },
            }
          );
        } catch (error) {
          setError('无法建立 WebSocket 连接: ' + (error instanceof Error ? error.message : '未知错误'));
          setConnectionStatus('disconnected');
        }
      }, 500);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : '创建面试会话失败，请重试';
      setError(errorMessage);
      setConnectionStatus('disconnected');
      alert('创建会话失败：' + errorMessage);
    }
  }, []);

  useEffect(() => {
    if (!presetVoiceConfig || autoStartRef.current) {
      return;
    }
    autoStartRef.current = true;
    handlePhaseConfig({
      skillId: presetVoiceConfig.skillId,
      difficulty: presetVoiceConfig.difficulty,
      techEnabled: presetVoiceConfig.techEnabled,
      projectEnabled: presetVoiceConfig.projectEnabled,
      hrEnabled: presetVoiceConfig.hrEnabled,
      plannedDuration: presetVoiceConfig.plannedDuration,
      resumeId: presetVoiceConfig.resumeId,
      llmProvider: presetVoiceConfig.llmProvider,
    });
  }, [handlePhaseConfig, presetVoiceConfig]);

  const handleAudioData = (audioData: string) => {
    if (blockMicToServerRef.current) {
      return;
    }
    if (wsRef.current && wsRef.current.isConnected()) {
      wsRef.current.sendAudio(audioData);
    } else {
      setError('未连接到服务器，请刷新页面重试');
    }
  };

  const handleSpeechStart = () => {
    blockMicToServerRef.current = false;
    if (audioPlayerRef.current && isAiSpeaking) {
      audioPlayerRef.current.pause();
      audioPlayerRef.current.currentTime = 0;
      setIsAiSpeaking(false);
    }
  };

  const handleSpeechEnd = () => {};

  const handlePause = async (type: 'short' | 'long') => {
    if (!sessionId) return;

    if (type === 'short') {
      setIsRecording(false);
      pauseTimeoutRef.current = setTimeout(() => {
        handleLongPause();
      }, 5 * 60 * 1000);
    } else {
      await handleLongPause();
    }
  };

  const handleLongPause = async () => {
    if (pauseTimeoutRef.current) {
      clearTimeout(pauseTimeoutRef.current);
      pauseTimeoutRef.current = null;
    }
    if (wsRef.current) {
      wsRef.current.disconnect();
    }
    if (isRecording) {
      setIsRecording(false);
    }
    if (!sessionId) return;
    try {
      await voiceInterviewApi.pauseSession(sessionId);
      navigate('/interviews');
    } catch (error) {
      alert('暂停失败，请重试');
    }
  };

  const handleEndInterview = async () => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
    }
    if (wsRef.current) {
      wsRef.current.disconnect();
    }
    if (sessionId) {
      try {
        await voiceInterviewApi.endSession(sessionId);
      } catch (error) {
        console.error('Failed to end session:', error);
      }
    }
    navigate('/interviews');
  };

  const handleCloseModal = () => {
    navigate('/history');
  };

  return (
    <div className="min-h-screen bg-slate-900 text-white overflow-hidden flex flex-col">
      {/* Phase setup modal - now using UnifiedInterviewModal */}
      {!autoStartRef.current && !presetVoiceConfig && (
        <div className="absolute inset-0 z-50 flex items-center justify-center bg-slate-900/95">
          <div className="text-center">
            <AlertCircle className="w-16 h-16 text-yellow-500 mx-auto mb-4" />
            <p className="text-slate-300 text-lg mb-4">未检测到面试配置</p>
            <button
              onClick={handleCloseModal}
              className="px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
            >
              返回首页重新开始
            </button>
          </div>
        </div>
      )}

      {/* Header / Top Bar */}
      <div className="px-6 py-4 flex items-center justify-between bg-slate-900/50 backdrop-blur-md border-b border-white/10 z-10">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/interviews')}
            className="p-2 hover:bg-white/10 rounded-full transition-colors text-slate-400 hover:text-white mr-2"
            title="返回面试记录"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="w-10 h-10 bg-primary-600 rounded-xl flex items-center justify-center shadow-lg shadow-primary-500/20">
            <Mic className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">{templateName || effectiveSkillId}</h1>
            <div className="flex items-center gap-2">
              <span className="text-xs px-2 py-0.5 bg-primary-500/20 text-primary-400 rounded-full border border-primary-500/30">
                {getPhaseLabel(currentPhase)}
              </span>
              <div className="flex items-center gap-1.5 ml-2">
                <div className={`w-1.5 h-1.5 rounded-full ${
                  connectionStatus === 'connected' ? 'bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.6)]' :
                  connectionStatus === 'connecting' ? 'bg-yellow-500 animate-pulse' :
                  'bg-red-500'
                }`} />
                <span className="text-[10px] text-slate-400 uppercase tracking-widest font-semibold">
                  {connectionStatus === 'connected' ? 'Online' : 'Connecting'}
                </span>
              </div>
            </div>
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 px-4 py-2 bg-white/5 rounded-full border border-white/10">
            <Clock className="w-4 h-4 text-slate-400" />
            <span className="font-mono text-sm tabular-nums text-slate-200">{formatTime(currentTime)}</span>
          </div>
        </div>
      </div>

      {/* Main Content Area */}
      <div className="flex-1 relative flex overflow-hidden">
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-primary-600/10 blur-[120px] rounded-full pointer-events-none" />

        <div className="flex-1 flex flex-col items-center justify-center p-8 relative z-10">
          {/* AI Avatar Area */}
          <div className="relative mb-16">
            <motion.div
              animate={isAiSpeaking ? {
                scale: [1, 1.05, 1],
                boxShadow: [
                  "0 0 0 0px rgba(59, 130, 246, 0)",
                  "0 0 0 20px rgba(59, 130, 246, 0.15)",
                  "0 0 0 0px rgba(59, 130, 246, 0)"
                ]
              } : {}}
              transition={{ repeat: Infinity, duration: 2 }}
              className={`w-48 h-48 md:w-64 md:h-64 rounded-full bg-gradient-to-br from-slate-800 to-slate-950
                         border-4 ${isAiSpeaking ? 'border-primary-500' : 'border-slate-800'}
                         flex items-center justify-center relative z-10 shadow-2xl transition-colors duration-500`}
            >
              <Bot className={`w-24 h-24 md:w-32 md:h-32 ${isAiSpeaking ? 'text-primary-400' : 'text-slate-600'} transition-colors`} strokeWidth={1.5} />

              {isAiSpeaking && (
                <>
                  <div className="absolute inset-0 rounded-full border-2 border-primary-500/50 animate-ping" />
                  <div className="absolute -inset-4 rounded-full border border-primary-500/20 animate-pulse" />
                </>
              )}
            </motion.div>

            <div className="absolute -bottom-4 left-1/2 -translate-x-1/2 bg-slate-800 border border-slate-700 px-5 py-1.5 rounded-full shadow-xl z-20">
              <span className="text-[11px] font-bold text-slate-300 uppercase tracking-widest">Interviewer</span>
            </div>
          </div>

          {/* Active Subtitles */}
          <div className="w-full max-w-3xl min-h-[140px] flex flex-col items-center justify-center text-center px-8 py-6 bg-slate-800/40 backdrop-blur-xl border border-white/5 rounded-3xl shadow-2xl">
             <AnimatePresence mode="wait">
               {isAiSpeaking || aiText ? (
                 <motion.p
                   key="ai-active"
                   initial={{ opacity: 0, y: 10 }}
                   animate={{ opacity: 1, y: 0 }}
                   exit={{ opacity: 0 }}
                   className="text-xl md:text-2xl font-medium text-white leading-relaxed"
                 >
                   {aiText || "思考中..."}
                 </motion.p>
               ) : userText ? (
                 <motion.p
                   key="user-active"
                   initial={{ opacity: 0, y: 10 }}
                   animate={{ opacity: 1, y: 0 }}
                   exit={{ opacity: 0 }}
                   className="text-xl md:text-2xl font-medium text-primary-400 italic leading-relaxed"
                 >
                   {userText}
                 </motion.p>
               ) : connectionStatus === 'connected' ? (
                 <motion.p
                   key="idle"
                   initial={{ opacity: 0 }}
                   animate={{ opacity: 0.5 }}
                   className="text-lg text-slate-500"
                 >
                   {isRecording ? '正在聆听...' : '点击下方麦克风开始发言'}
                 </motion.p>
               ) : (
                 <motion.p
                   key="connecting"
                   initial={{ opacity: 0 }}
                   animate={{ opacity: 0.5 }}
                   className="text-lg text-slate-500"
                 >
                   正在连接面试官...
                 </motion.p>
               )}
             </AnimatePresence>
          </div>
        </div>

        {/* Right: History Sidebar */}
        <div className="w-80 lg:w-96 bg-slate-950/50 backdrop-blur-xl border-l border-white/10 flex flex-col hidden md:flex">
          <RealtimeSubtitle
            messages={messages}
            userText={userText}
            aiText={aiText}
            isAiSpeaking={isAiSpeaking}
          />
        </div>
      </div>

      {/* Footer Controls */}
      {(presetVoiceConfig || autoStartRef.current) && connectionStatus !== 'disconnected' && (
        <div className="absolute bottom-12 left-1/2 -translate-x-1/2 z-50">
          <div className="flex items-center gap-6 px-10 py-5 bg-slate-900/80 backdrop-blur-2xl border border-white/10 rounded-full shadow-[0_20px_50px_rgba(0,0,0,0.5)]">
            <button
              onClick={() => {
                const choice = window.confirm('暂停面试？\n确定 = 短暂停（5分钟）\n取消 = 离开并保存');
                handlePause(choice ? 'short' : 'long');
              }}
              disabled={connectionStatus !== 'connected'}
              className="w-12 h-12 rounded-full flex items-center justify-center bg-slate-800 hover:bg-slate-700 border border-white/5 transition-all text-slate-400 hover:text-white"
              title="暂停"
            >
              <Clock className="w-5 h-5" />
            </button>

            <div className="relative group">
              <div className={`absolute -inset-4 bg-primary-500/20 rounded-full blur-xl transition-opacity duration-500 ${isRecording ? 'opacity-100' : 'opacity-0'}`} />
              <div className="relative">
                <AudioRecorder
                  isRecording={isRecording}
                  onRecordingChange={setIsRecording}
                  onAudioData={handleAudioData}
                  onSpeechStart={handleSpeechStart}
                  onSpeechEnd={handleSpeechEnd}
                />
              </div>
            </div>

            <button
              onClick={handleEndInterview}
              disabled={connectionStatus !== 'connected'}
              className="w-12 h-12 rounded-full flex items-center justify-center bg-red-500/20 hover:bg-red-500 border border-red-500/50 transition-all text-red-500 hover:text-white"
              title="结束面试"
            >
              <PhoneOff className="w-5 h-5" />
            </button>
          </div>

          <div className="absolute -top-8 left-1/2 -translate-x-1/2 whitespace-nowrap text-[11px] font-medium tracking-widest uppercase text-slate-500">
             {isRecording ? (
              <span className="text-primary-400 flex items-center gap-2">
                <span className="w-1.5 h-1.5 bg-primary-400 rounded-full animate-ping" />
                正在聆听
              </span>
            ) : (
              '点击麦克风发言'
            )}
          </div>
        </div>
      )}

      {/* Hidden audio player */}
      {aiAudio && (
        <audio
          ref={audioPlayerRef}
          src={`data:audio/wav;base64,${aiAudio}`}
          onEnded={() => {
            setIsAiSpeaking(false);
            blockMicToServerRef.current = false;
            if (aiText.trim()) {
              setMessages(prev => [
                ...prev,
                { role: 'ai', text: aiText.trim(), id: (Date.now() + 1).toString() }
              ]);
              setAiText('');
            }
          }}
          onPlay={() => setIsAiSpeaking(true)}
          autoPlay
          style={{ display: 'none' }}
        />
      )}

      {/* Floating Errors */}
      {error && (
        <div className="fixed top-24 left-1/2 -translate-x-1/2 z-50 animate-bounce">
          <div className="bg-red-600 text-white px-6 py-3 rounded-full shadow-2xl flex items-center gap-3 border border-red-500/50">
            <AlertCircle className="w-5 h-5" />
            <span className="text-sm font-bold">{error}</span>
          </div>
        </div>
      )}
    </div>
  );
}
