import { request } from './request';

// ========== 类型定义 ==========

export interface CreateSessionRequest {
  roleType: 'ali-p8' | 'byteance-algo' | 'tencent-backend';
  customJdText?: string;
  resumeId?: number;
  introEnabled?: boolean;
  techEnabled?: boolean;
  projectEnabled?: boolean;
  hrEnabled?: boolean;
  plannedDuration?: number;
}

export interface SessionResponse {
  sessionId: number;
  roleType: string;
  currentPhase: string;
  status: string;
  startTime: string;
  plannedDuration: number;
  webSocketUrl: string;
}

export interface InterviewMessage {
  id: number;
  sessionId: number;
  messageType: string;
  phase: string;
  userRecognizedText: string;
  aiGeneratedText: string;
  timestamp: string;
  sequenceNum: number;
}

export interface Evaluation {
  sessionId: number;
  overallScore: number;
  overallRating: string;
  techKnowledge: { score: number; comment: string };
  projectExp: { score: number; comment: string };
  communication: { score: number; comment: string };
  logicalThinking: { score: number; comment: string };
  improvementSuggestions: string[];
  strengthsSummary: string;
}

/**
 * Session metadata for history list
 */
export interface SessionMeta {
  sessionId: number;
  roleType: string;
  status: string;
  currentPhase: string;
  createdAt: string;
  updatedAt: string;
  actualDuration?: number;
  messageCount: number;
}

/**
 * Session response with WebSocket URL
 */
export interface SessionResponseDTO {
  sessionId: number;
  roleType: string;
  currentPhase: string;
  status: string;
  startTime: string;
  plannedDuration: number;
  webSocketUrl: string;
}

// WebSocket 消息类型
export interface WebSocketAudioMessage {
  type: 'audio';
  data: string; // Base64 编码的音频
  timestamp?: number;
}

export interface WebSocketSubtitleMessage {
  type: 'subtitle';
  text: string;
  isFinal: boolean;
}

export interface WebSocketAudioResponseMessage {
  type: 'audio';
  data: string; // Base64 编码的音频
  text: string;
}

export interface WebSocketTextMessage {
  type: 'text';
  content: string;
}

export type WebSocketMessage =
  | WebSocketAudioMessage
  | WebSocketSubtitleMessage
  | WebSocketAudioResponseMessage
  | WebSocketTextMessage;

// WebSocket 事件处理器
export interface WebSocketEventHandlers {
  onMessage?: (message: WebSocketMessage) => void;
  onSubtitle?: (text: string, isFinal: boolean) => void;
  onAudioResponse?: (audioData: string, text: string) => void;
  onOpen?: () => void;
  onClose?: (event: CloseEvent) => void;
  onError?: (error: Event) => void;
}

// ========== API 函数 ==========

export const voiceInterviewApi = {
  /**
   * 创建新的语音面试会话
   */
  async createSession(data: CreateSessionRequest): Promise<SessionResponse> {
    return request.post<SessionResponse>('/api/voice-interview/sessions', data);
  },

  /**
   * 获取会话详情
   */
  async getSession(sessionId: number): Promise<SessionResponse> {
    return request.get<SessionResponse>(`/api/voice-interview/sessions/${sessionId}`);
  },

  /**
   * 结束会话
   */
  async endSession(sessionId: number): Promise<void> {
    return request.post<void>(`/api/voice-interview/sessions/${sessionId}/end`);
  },

  /**
   * 获取会话消息列表
   */
  async getMessages(sessionId: number): Promise<InterviewMessage[]> {
    return request.get<InterviewMessage[]>(
      `/api/voice-interview/sessions/${sessionId}/messages`
    );
  },

  /**
   * 获取面试评估结果
   */
  async getEvaluation(sessionId: number): Promise<Evaluation> {
    return request.get<Evaluation>(
      `/api/voice-interview/sessions/${sessionId}/evaluation`
    );
  },

  /**
   * 生成面试评估（异步）
   */
  async generateEvaluation(sessionId: number): Promise<Evaluation> {
    return request.post<Evaluation>(
      `/api/voice-interview/sessions/${sessionId}/evaluation`
    );
  },

  /**
   * Pause interview session
   */
  async pauseSession(sessionId: number, reason: string = 'user_initiated'): Promise<void> {
    return request.put(
      `/api/voice-interview/sessions/${sessionId}/pause`,
      { reason }
    );
  },

  /**
   * Resume interview session
   */
  async resumeSession(sessionId: number): Promise<SessionResponseDTO> {
    return request.put<SessionResponseDTO>(
      `/api/voice-interview/sessions/${sessionId}/resume`
    );
  },

  /**
   * Get all sessions
   */
  async getAllSessions(userId?: string, status?: string): Promise<SessionMeta[]> {
    const params = new URLSearchParams();
    if (userId) params.append('userId', userId);
    if (status) params.append('status', status);

    return request.get<SessionMeta[]>(
      `/api/voice-interview/sessions?${params.toString()}`
    );
  },

  /**
   * Get conversation history for a session
   */
  async getConversationHistory(sessionId: number): Promise<InterviewMessage[]> {
    return request.get<InterviewMessage[]>(
      `/api/voice-interview/sessions/${sessionId}/messages`
    );
  },
};

// ========== WebSocket 连接管理类 ==========

export class VoiceInterviewWebSocket {
  private ws: WebSocket | null = null;
  private sessionId: number;
  private url: string;
  private handlers: WebSocketEventHandlers;
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 3;
  private reconnectDelay = 2000;

  constructor(sessionId: number, url: string, handlers: WebSocketEventHandlers) {
    this.sessionId = sessionId;
    this.url = url;
    this.handlers = handlers;
  }

  /**
   * 建立 WebSocket 连接
   */
  connect(): void {
    try {
      console.log('[WebSocket] Attempting to connect to:', this.url);
      console.log('[WebSocket] User agent:', navigator.userAgent);

      // 检测是否使用代理
      const proxyCheck = window.performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming;
      console.log('[WebSocket] Navigation type:', proxyCheck?.type);

      this.ws = new WebSocket(this.url);

      // 监控连接状态变化
      this.ws.onopen = () => {
        console.log('[WebSocket] Connected successfully for session:', this.sessionId);
        console.log('[WebSocket] Ready state:', this.ws?.readyState);
        this.reconnectAttempts = 0;
        this.handlers.onOpen?.();
      };

      this.ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data) as WebSocketMessage;

          // 调用通用消息处理器
          this.handlers.onMessage?.(message);

          // 根据消息类型调用特定处理器
          switch (message.type) {
            case 'subtitle':
              this.handlers.onSubtitle?.(
                message.text,
                (message as WebSocketSubtitleMessage).isFinal
              );
              break;
            case 'audio':
              // 检查是否是 AI 响应（包含 text 字段）
              if ('text' in message) {
                const audioMsg = message as WebSocketAudioResponseMessage;
                this.handlers.onAudioResponse?.(audioMsg.data, audioMsg.text);
              }
              break;
            case 'text':
              // Text-only message (when TTS fails)
              if ('content' in message) {
                this.handlers.onAudioResponse?.('', (message as any).content);
              }
              break;
          }
        } catch (error) {
          console.error('Error parsing WebSocket message:', error);
        }
      };

      this.ws.onclose = (event) => {
        console.log('[WebSocket] Closed for session:', this.sessionId);
        console.log('[WebSocket] Close code:', event.code, '- reason:', event.reason);
        console.log('[WebSocket] Was clean:', event.wasClean);

        // 提供常见错误代码的解决方案
        if (event.code === 1006) {
          console.error('[WebSocket] 连接异常关闭 (code 1006)');
          console.warn('[WebSocket] 可能原因：\n1. 代理/VPN 干扰了 WebSocket 连接\n2. 网络中断\n3. 服务器未响应\n建议：关闭代理/VPN 后重试');
        }

        this.handlers.onClose?.(event);

        // 如果不是主动关闭，尝试重连
        if (!event.wasClean && this.reconnectAttempts < this.maxReconnectAttempts) {
          this.reconnectAttempts++;
          console.log(
            `Attempting to reconnect (${this.reconnectAttempts}/${this.maxReconnectAttempts})...`
          );
          setTimeout(() => this.connect(), this.reconnectDelay);
        }
      };

      this.ws.onerror = (error) => {
        console.error('[WebSocket] Error for session:', this.sessionId);
        console.error('[WebSocket] Error type:', error.type);
        console.error('[WebSocket] Current ready state:', this.ws?.readyState);
        console.error('[WebSocket] URL was:', this.url);

        // 提供代理诊断提示
        console.warn('[WebSocket] 如果使用了代理/VPN，请尝试：\n1. 关闭代理后重试\n2. 或在代理设置中添加 localhost 到绕过列表');

        this.handlers.onError?.(error);
      };
    } catch (error) {
      console.error('Error creating WebSocket connection:', error);
      this.handlers.onError?.(error as Event);
    }
  }

  /**
   * 发送音频数据
   */
  sendAudio(audioData: string): boolean {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const message: WebSocketAudioMessage = {
        type: 'audio',
        data: audioData,
        timestamp: Date.now(),
      };
      this.ws.send(JSON.stringify(message));
      return true;
    }
    console.warn('WebSocket is not connected');
    return false;
  }

  /**
   * 发送控制消息
   */
  sendControl(action: string, data?: Record<string, unknown>): boolean {
    if (this.ws && this.ws.readyState === WebSocket.OPEN) {
      const message = {
        type: 'control',
        action,
        data,
        timestamp: Date.now(),
      };
      this.ws.send(JSON.stringify(message));
      return true;
    }
    console.warn('WebSocket is not connected');
    return false;
  }

  /**
   * 关闭连接
   */
  disconnect(): void {
    if (this.ws) {
      // 不重连
      this.reconnectAttempts = this.maxReconnectAttempts;
      this.ws.close(1000, 'User disconnected');
      this.ws = null;
    }
  }

  /**
   * 获取连接状态
   */
  getReadyState(): number {
    return this.ws?.readyState ?? WebSocket.CLOSED;
  }

  /**
   * 是否已连接
   */
  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }
}

// ========== 便捷函数 ==========

/**
 * 创建并连接 WebSocket
 */
export function connectWebSocket(
  sessionId: number,
  webSocketUrl: string,
  handlers: WebSocketEventHandlers
): VoiceInterviewWebSocket {
  const ws = new VoiceInterviewWebSocket(sessionId, webSocketUrl, handlers);
  ws.connect();
  return ws;
}

export default voiceInterviewApi;
