import { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';

interface Message {
  role: 'user' | 'ai';
  text: string;
  id: string;
}

interface RealtimeSubtitleProps {
  messages: Message[];
  userText: string;
  aiText: string;
  isAiSpeaking: boolean;
}

export default function RealtimeSubtitle({
  messages,
  userText,
  aiText,
  isAiSpeaking
}: RealtimeSubtitleProps) {
  const [displayedAiText, setDisplayedAiText] = useState('');
  const [isTyping, setIsTyping] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Typewriter effect for current AI text
  useEffect(() => {
    if (aiText && isAiSpeaking) {
      setIsTyping(true);
      setDisplayedAiText('');

      let index = 0;
      const speed = 25; // ms per character

      const typeWriter = () => {
        if (index < aiText.length) {
          setDisplayedAiText(aiText.substring(0, index + 1));
          index++;
          setTimeout(typeWriter, speed);
        } else {
          setIsTyping(false);
        }
      };

      typeWriter();
    } else if (!aiText) {
      setDisplayedAiText('');
      setIsTyping(false);
    }
  }, [aiText, isAiSpeaking]);

  // Auto-scroll to bottom on new messages or text updates
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages, userText, displayedAiText]);

  return (
    <div className="flex flex-col h-full bg-slate-900/40 backdrop-blur-md overflow-hidden">
      {/* Header */}
      <div className="px-5 py-4 border-b border-white/5 flex items-center justify-between bg-slate-950/30">
        <h4 className="text-sm font-medium text-slate-300 tracking-wide">对话实录</h4>
        <div className="flex items-center gap-3">
          {isAiSpeaking && (
            <div className="flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 bg-primary-500 rounded-full animate-pulse" />
              <span className="text-[10px] uppercase tracking-wider text-primary-400 font-semibold">AI 说</span>
            </div>
          )}
          {userText && (
            <div className="flex items-center gap-1.5">
              <span className="w-1.5 h-1.5 bg-green-500 rounded-full animate-pulse" />
              <span className="text-[10px] uppercase tracking-wider text-green-400 font-semibold">你在说</span>
            </div>
          )}
        </div>
      </div>

      {/* Chat History */}
      <div
        ref={scrollRef}
        className="flex-1 overflow-y-auto p-5 space-y-5 scroll-smooth"
      >
        <AnimatePresence initial={false}>
          {/* History Messages */}
          {messages.map((msg) => (
            <motion.div
              key={msg.id}
              initial={{ opacity: 0, y: 10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
            >
              <div
                className={`max-w-[85%] px-4 py-3 rounded-2xl text-sm leading-relaxed ${
                  msg.role === 'user'
                    ? 'bg-primary-600 text-white rounded-tr-sm shadow-md shadow-primary-900/20'
                    : 'bg-slate-800 text-slate-200 rounded-tl-sm shadow-md shadow-black/20'
                }`}
              >
                {msg.text}
              </div>
            </motion.div>
          ))}

          {/* Current AI Response (Active) */}
          {isAiSpeaking && (aiText || displayedAiText) && (
            <motion.div
              key="active-ai"
              initial={{ opacity: 0, y: 10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              className="flex justify-start"
            >
              <div className="max-w-[85%] px-4 py-3 rounded-2xl rounded-tl-sm bg-slate-800 border border-primary-500/30 text-slate-200 text-sm leading-relaxed shadow-lg shadow-primary-900/10">
                {displayedAiText || aiText}
                {isTyping && (
                  <motion.span
                    className="inline-block w-1 h-3.5 bg-primary-500 ml-1.5 translate-y-0.5 rounded-full"
                    animate={{ opacity: [1, 0, 1] }}
                    transition={{ duration: 0.8, repeat: Infinity }}
                  />
                )}
              </div>
            </motion.div>
          )}

          {/* Current User Input (Real-time) */}
          {userText && (
            <motion.div
              key="active-user"
              initial={{ opacity: 0, y: 10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              className="flex justify-end"
            >
              <div className="max-w-[85%] px-4 py-3 rounded-2xl rounded-tr-sm bg-primary-600/80 backdrop-blur-sm border border-primary-500/50 text-white text-sm italic leading-relaxed">
                {userText}
                <span className="ml-1 animate-pulse">...</span>
              </div>
            </motion.div>
          )}

          {/* Empty State */}
          {messages.length === 0 && !userText && !aiText && (
            <motion.div
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              className="h-full flex flex-col items-center justify-center text-slate-500 py-12"
            >
              <div className="w-12 h-12 rounded-full bg-slate-800/50 flex items-center justify-center mb-4 border border-white/5">
                <svg className="w-5 h-5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z" />
                </svg>
              </div>
              <p className="text-sm">面试即将开始，请准备</p>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}
