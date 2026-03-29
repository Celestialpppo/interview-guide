import { useState, useEffect, useRef } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Clock, PhoneOff, AlertCircle, Bot, Mic, ArrowLeft } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import AudioRecorder from '../components/AudioRecorder';
import RealtimeSubtitle from '../components/RealtimeSubtitle';
import PhaseSetupModal, { PhaseConfig } from '../components/PhaseSetupModal';
import {
  voiceInterviewApi,
  CreateSessionRequest,
  connectWebSocket,
  VoiceInterviewWebSocket,
} from '../api/voiceInterview';

export default function VoiceInterviewPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryParams = new URLSearchParams(location.search);
  const roleType = queryParams.get('role') as CreateSessionRequest['roleType'];

  // UI state
  const [showPhaseModal, setShowPhaseModal] = useState(true);
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

  // Refs
  const timerRef = useRef<NodeJS.Timeout | null>(null);
  const wsRef = useRef<VoiceInterviewWebSocket | null>(null);
  const audioPlayerRef = useRef<HTMLAudioElement>(null);
  const pauseTimeoutRef = useRef<NodeJS.Timeout | null>(null);

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
    if (!showPhaseModal && sessionId && connectionStatus === 'connected') {
      startTimer();
    } else if (timerRef.current) {
      clearInterval(timerRef.current);
    }

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
      }
    };
  }, [showPhaseModal, sessionId, connectionStatus]);

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

  const getRoleLabel = (role: string) => {
    const roleMap: Record<string, string> = {
      'ali-p8': '阿里P8后端面试',
      'byteance-algo': '字节算法工程师面试',
      'tencent-backend': '腾讯后台开发面试',
    };
    return roleMap[role] || role;
  };

  const handlePhaseConfig = async (config: PhaseConfig) => {
    console.log('handlePhaseConfig called with config:', config);
    console.log('roleType:', roleType);

    if (!roleType) {
      setError('无效的面试角色');
      return;
    }

    setError(null);
    setConnectionStatus('connecting');

    try {
      console.log('Creating session...');
      const session = await voiceInterviewApi.createSession({
        roleType: roleType,
        introEnabled: false,
        techEnabled: config.techEnabled,
        projectEnabled: config.projectEnabled,
        hrEnabled: config.hrEnabled,
        plannedDuration: config.plannedDuration,
        customJdText: config.customJD,
        resumeId: config.resumeId,
      });

      console.log('Session created:', session);
      setSessionId(session.sessionId);
      setCurrentPhase(session.currentPhase);

      // Prepare WebSocket URL
      const wsUrl = session.webSocketUrl || `ws://localhost:8080/ws/voice-interview/${session.sessionId}`;
      console.log('WebSocket URL prepared:', wsUrl);

      // Close modal first
      setShowPhaseModal(false);
      console.log('Modal closed, main interface should now be visible');

      // 延迟连接 WebSocket，确保 DOM 已经准备好
      setTimeout(() => {
        console.log('Connecting WebSocket to:', wsUrl);

        try {
          wsRef.current = connectWebSocket(
            session.sessionId,
            wsUrl,
            {
              onOpen: () => {
                console.log('WebSocket connected successfully');
                setConnectionStatus('connected');
              },
              onMessage: (message) => {
                console.log('WebSocket message received:', message);
              },
              onSubtitle: (text, isFinal) => {
                // Update user text in real-time
                console.log('Subtitle received:', text, 'isFinal:', isFinal);
                setUserText(text);

                if (isFinal && text.trim()) {
                  setMessages(prev => [
                    ...prev,
                    { role: 'user', text: text.trim(), id: Date.now().toString() }
                  ]);
                  setUserText(''); // Clear real-time text after committing to history
                }
              },
              onAudioResponse: (audioData, text) => {
                // Play AI audio and show AI subtitle
                console.log('Audio response received, text:', text);
                setAiAudio(audioData);
                setAiText(text);
                setIsAiSpeaking(true);
              },
              onClose: (event) => {
                console.log('WebSocket closed:', event);
                setConnectionStatus('disconnected');
                if (event.code !== 1000) {
                  console.error('WebSocket closed unexpectedly:', event.code, event.reason);
                  setError('连接已断开，请刷新页面重试');
                }
              },
              onError: (errorEvent) => {
                console.error('WebSocket error event:', errorEvent);
                const error = errorEvent as ErrorEvent;
                console.error('WebSocket error details:', {
                  type: error.type,
                  target: error.target,
                  bubbles: error.bubbles,
                  cancelable: error.cancelable
                });
                setError('WebSocket 连接错误，请检查网络后重试');
                setConnectionStatus('disconnected');
              },
            }
          );
        } catch (error) {
          console.error('Failed to create WebSocket connection:', error);
          setError('无法建立 WebSocket 连接: ' + (error instanceof Error ? error.message : '未知错误'));
          setConnectionStatus('disconnected');
        }
      }, 500); // 延迟 500ms 确保页面完全加载
    } catch (error) {
      console.error('Failed to create session:', error);
      const errorMessage = error instanceof Error ? error.message : '创建面试会话失败，请重试';
      setError(errorMessage);
      setConnectionStatus('disconnected');
      alert('创建会话失败：' + errorMessage);
    }
  };

  const handleAudioData = (audioData: string) => {
    if (wsRef.current && wsRef.current.isConnected()) {
      wsRef.current.sendAudio(audioData);
    } else {
      setError('未连接到服务器，请刷新页面重试');
    }
  };

  const handleSpeechStart = () => {
    console.log('[Page] User speaking, interrupt AI');

    // Immediately stop AI audio
    if (audioPlayerRef.current && isAiSpeaking) {
      audioPlayerRef.current.pause();
      audioPlayerRef.current.currentTime = 0;
      setIsAiSpeaking(false);
    }
  };

  const handleSpeechEnd = () => {
    console.log('[Page] User stopped speaking');
  };

  const handlePause = async (type: 'short' | 'long') => {
    if (!sessionId) return;

    if (type === 'short') {
      // Short pause: keep connection, start 5-minute countdown
      setIsRecording(false);

      pauseTimeoutRef.current = setTimeout(() => {
        // Auto-convert to long pause after 5 minutes
        handleLongPause();
      }, 5 * 60 * 1000);

    } else {
      // Long pause: disconnect and save
      await handleLongPause();
    }
  };

  const handleLongPause = async () => {
    // Clear timeout
    if (pauseTimeoutRef.current) {
      clearTimeout(pauseTimeoutRef.current);
      pauseTimeoutRef.current = null;
    }

    // Disconnect WebSocket
    if (wsRef.current) {
      wsRef.current.disconnect();
    }

    // Stop recording
    if (isRecording) {
      setIsRecording(false);
    }

    // Save to backend
    if (!sessionId) {
      console.error('No session ID available');
      return;
    }

    try {
      await voiceInterviewApi.pauseSession(sessionId);
      navigate('/voice-interview/history');
    } catch (error) {
      console.error('Failed to pause session:', error);
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

    // Navigate to evaluation page
    navigate(`/voice-interview/${sessionId}/evaluation`);
  };

  const handleCloseModal = () => {
    // Just close the modal, don't navigate
    setShowPhaseModal(false);
  };

  // Validation
  if (!roleType) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-100">
        <div className="text-center">
          <AlertCircle className="w-16 h-16 text-red-500 mx-auto mb-4" />
          <p className="text-slate-600 text-lg">无效的面试角色</p>
          <button
            onClick={() => navigate('/')}
            className="mt-4 px-6 py-2 bg-primary-500 text-white rounded-lg hover:bg-primary-600"
          >
            返回首页
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-900 text-white overflow-hidden flex flex-col">
      {/* Phase setup modal */}
      <PhaseSetupModal
        isOpen={showPhaseModal}
        onClose={handleCloseModal}
        onStart={handlePhaseConfig}
        roleType={getRoleLabel(roleType)}
      />

      {/* Header / Top Bar */}
      <div className="px-6 py-4 flex items-center justify-between bg-slate-900/50 backdrop-blur-md border-b border-white/10 z-10">
        <div className="flex items-center gap-4">
          <button
            onClick={() => navigate('/upload')}
            className="p-2 hover:bg-white/10 rounded-full transition-colors text-slate-400 hover:text-white mr-2"
            title="返回首页"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="w-10 h-10 bg-primary-600 rounded-xl flex items-center justify-center shadow-lg shadow-primary-500/20">
            <Mic className="w-5 h-5 text-white" />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">{getRoleLabel(roleType)}</h1>
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
        {/* Global ambient glow */}
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[600px] h-[600px] bg-primary-600/10 blur-[120px] rounded-full pointer-events-none" />

        {/* Left: AI Avatar and Live Subtitles (Call Room View) */}
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

              {/* Speaking Indicator Rings */}
              {isAiSpeaking && (
                <>
                  <div className="absolute inset-0 rounded-full border-2 border-primary-500/50 animate-ping" />
                  <div className="absolute -inset-4 rounded-full border border-primary-500/20 animate-pulse" />
                </>
              )}
            </motion.div>

            {/* AI Role Badge */}
            <div className="absolute -bottom-4 left-1/2 -translate-x-1/2 bg-slate-800 border border-slate-700 px-5 py-1.5 rounded-full shadow-xl z-20">
              <span className="text-[11px] font-bold text-slate-300 uppercase tracking-widest">Interviewer</span>
            </div>
          </div>

          {/* Active Subtitles (Center Overlay) */}
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
               ) : (
                 <motion.p
                   key="idle"
                   initial={{ opacity: 0 }}
                   animate={{ opacity: 0.5 }}
                   className="text-lg text-slate-500"
                 >
                   等待对话开始...
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

      {/* Footer Controls (Floating Dock) */}
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
        
        {/* Subtle status hint */}
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

      {/* Hidden audio player */}
      {aiAudio && (
        <audio
          ref={audioPlayerRef}
          src={`data:audio/mp3;base64,${aiAudio}`}
          onEnded={() => {
            setIsAiSpeaking(false);
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
