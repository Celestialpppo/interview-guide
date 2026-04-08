import React, { useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X, Clock, FileText, FileStack, Users, Bot, Sparkles, Server, Cpu } from 'lucide-react';
import { historyApi, ResumeListItem } from '../api/history';

export interface PhaseConfig {
  techEnabled: boolean;
  projectEnabled: boolean;
  hrEnabled: boolean;
  plannedDuration: number;
  customJD?: string;
  resumeId?: number;
  roleType?: string;
  llmProvider?: string;
}

interface PhaseSetupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onStart: (config: PhaseConfig) => void;
  roleType: string;
  onRoleTypeChange?: (roleType: string) => void;
}

const INTERVIEWER_TYPES = [
  {
    value: 'ali-p8',
    label: '阿里 P8 后端面试',
    description: '侧重系统设计、高并发架构、分布式技术栈',
    gradient: 'from-orange-500 to-red-500',
    iconBg: 'bg-orange-100 dark:bg-orange-900/30',
    iconColor: 'text-orange-600 dark:text-orange-400',
  },
  {
    value: 'byteance-algo',
    label: '字节算法工程师面试',
    description: '侧重算法、数据结构、机器学习基础',
    gradient: 'from-blue-500 to-cyan-500',
    iconBg: 'bg-blue-100 dark:bg-blue-900/30',
    iconColor: 'text-blue-600 dark:text-blue-400',
  },
  {
    value: 'tencent-backend',
    label: '腾讯后台开发面试',
    description: '侧重网络编程、操作系统、分布式存储',
    gradient: 'from-green-500 to-emerald-500',
    iconBg: 'bg-green-100 dark:bg-green-900/30',
    iconColor: 'text-green-600 dark:text-green-400',
  },
];

const LLM_PROVIDERS = [
  {
    value: 'dashscope',
    label: '阿里云 DashScope',
    description: '云端模型，稳定可靠',
    icon: Server,
  },
  {
    value: 'lmstudio',
    label: '本地 LM Studio',
    description: '本地部署，隐私安全',
    icon: Cpu,
  },
];

const PHASES = [
  {
    key: 'techEnabled' as const,
    label: '技术问题',
    description: '深入考察技术能力和编程基础',
    baseWeight: 15,
  },
  {
    key: 'projectEnabled' as const,
    label: '项目深挖',
    description: '探讨项目细节、难点和解决方案',
    baseWeight: 15,
  },
  {
    key: 'hrEnabled' as const,
    label: 'HR 问题',
    description: '职业规划、薪资期望、团队协作',
    baseWeight: 5,
  },
];

export default function PhaseSetupModal({
  isOpen,
  onClose,
  onStart,
  roleType,
  onRoleTypeChange,
}: PhaseSetupModalProps) {
  const [config, setConfig] = useState<PhaseConfig>({
    techEnabled: true,
    projectEnabled: true,
    hrEnabled: true,
    plannedDuration: 30,
    roleType: roleType,
    llmProvider: 'dashscope',
  });

  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [loadingResumes, setLoadingResumes] = useState(false);

  useEffect(() => {
    if (isOpen) {
      loadResumes();
    }
  }, [isOpen]);

  const loadResumes = async () => {
    setLoadingResumes(true);
    try {
      const data = await historyApi.getResumes();
      setResumes(data);
    } catch (error) {
      console.error('Failed to load resumes:', error);
    } finally {
      setLoadingResumes(false);
    }
  };

  if (!isOpen) return null;

  const togglePhase = (phase: keyof PhaseConfig) => {
    setConfig((prev) => {
      const newConfig = { ...prev, [phase]: !prev[phase] };
      const baseMinutes = PHASES.reduce(
        (total, p) => total + (newConfig[p.key] ? p.baseWeight : 0), 0
      );
      newConfig.plannedDuration = Math.max(15, Math.min(60, Math.round(baseMinutes / 5) * 5));
      return newConfig;
    });
  };

  const handleDurationChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setConfig((prev) => ({ ...prev, plannedDuration: parseInt(e.target.value) }));
  };

  /** 根据启用阶段的权重比例，计算某阶段的分配分钟数 */
  const getPhaseMinutes = (phaseKey: keyof PhaseConfig): number => {
    const enabledTotalWeight = PHASES.reduce(
      (sum, p) => sum + (config[p.key] ? p.baseWeight : 0), 0
    );
    if (enabledTotalWeight === 0) return 0;
    const phase = PHASES.find(p => p.key === phaseKey);
    if (!phase || !config[phaseKey]) return 0;
    return Math.round(config.plannedDuration * phase.baseWeight / enabledTotalWeight);
  };

  const handleJDChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setConfig((prev) => ({ ...prev, customJD: e.target.value }));
  };

  const handleResumeSelect = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    setConfig((prev) => ({ ...prev, resumeId: val ? parseInt(val, 10) : undefined }));
  };

  const handleRoleTypeChange = (newRoleType: string) => {
    setConfig((prev) => ({ ...prev, roleType: newRoleType }));
    onRoleTypeChange?.(newRoleType);
  };

  const handleLlmProviderChange = (llmProvider: string) => {
    setConfig((prev) => ({ ...prev, llmProvider }));
  };

  const atLeastOneEnabled = PHASES.some((phase) => config[phase.key]);

  const handleStart = async () => {
    if (!atLeastOneEnabled) return;
    try {
      await onStart(config);
    } catch (error) {
      const message = error instanceof Error ? error.message : '启动面试失败';
      alert('启动面试失败：' + message);
    }
  };

  const enabledPhasesCount = PHASES.filter(p => config[p.key]).length;

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/60 backdrop-blur-sm z-50"
          />

          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-2xl w-full max-h-[90vh] overflow-y-auto"
            >
              {/* Header */}
              <div className="px-6 py-5 border-b border-slate-100 dark:border-slate-700/50">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-primary-500 to-primary-600 flex items-center justify-center shadow-lg shadow-primary-500/25">
                      <Sparkles className="w-5 h-5 text-white" />
                    </div>
                    <div>
                      <h2 className="text-lg font-bold text-slate-900 dark:text-white">
                        语音模拟面试
                      </h2>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        配置面试参数，开始实时语音对话
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={onClose}
                    className="p-2 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-700 rounded-lg transition-colors"
                  >
                    <X className="w-5 h-5" />
                  </button>
                </div>
              </div>

              {/* Content */}
              <div className="px-6 py-5 space-y-5">

                {/* Interviewer Type Selection */}
                <div>
                  <label className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                    <Users className="w-4 h-4 text-primary-500" />
                    面试官角色
                  </label>
                  <div className="grid grid-cols-1 gap-2">
                    {INTERVIEWER_TYPES.map((type) => {
                      const selected = config.roleType === type.value;
                      return (
                        <button
                          key={type.value}
                          onClick={() => handleRoleTypeChange(type.value)}
                          className={`
                            w-full flex items-center gap-3 p-3 rounded-xl border-2
                            transition-all duration-200 text-left group
                            ${selected
                              ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20 shadow-sm shadow-primary-500/10'
                              : 'border-slate-150 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                            }
                          `}
                        >
                          <div className={`w-10 h-10 rounded-xl flex items-center justify-center flex-shrink-0 ${type.iconBg}`}>
                            <span className={`text-base font-bold ${type.iconColor}`}>
                              {type.label.charAt(0)}
                            </span>
                          </div>
                          <div className="flex-1 min-w-0">
                            <p className={`font-semibold text-sm ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-900 dark:text-white'}`}>
                              {type.label}
                            </p>
                            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                              {type.description}
                            </p>
                          </div>
                          <div className={`
                            w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0 transition-all
                            ${selected ? 'border-primary-500 bg-primary-500 scale-100' : 'border-slate-300 dark:border-slate-600 scale-90'}
                          `}>
                            {selected && (
                              <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                              </svg>
                            )}
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* LLM Provider Selection */}
                <div>
                  <label className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                    <Bot className="w-4 h-4 text-primary-500" />
                    AI 模型
                  </label>
                  <div className="grid grid-cols-2 gap-2">
                    {LLM_PROVIDERS.map((provider) => {
                      const Icon = provider.icon;
                      const selected = config.llmProvider === provider.value;
                      return (
                        <button
                          key={provider.value}
                          onClick={() => handleLlmProviderChange(provider.value)}
                          className={`
                            flex items-center gap-3 p-3 rounded-xl border-2
                            transition-all duration-200 text-left
                            ${selected
                              ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                              : 'border-slate-150 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                            }
                          `}
                        >
                          <Icon className={`w-5 h-5 flex-shrink-0 ${selected ? 'text-primary-500' : 'text-slate-400'}`} />
                          <div className="min-w-0">
                            <p className={`font-semibold text-sm truncate ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-900 dark:text-white'}`}>
                              {provider.label}
                            </p>
                            <p className="text-[11px] text-slate-500 dark:text-slate-400 truncate">
                              {provider.description}
                            </p>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Resume Selection */}
                <div className="bg-gradient-to-br from-primary-50/80 to-blue-50/80 dark:from-primary-900/20 dark:to-blue-900/10 rounded-xl p-4 border border-primary-100 dark:border-primary-800/30">
                  <div className="flex items-center gap-3 mb-3">
                    <FileStack className="w-5 h-5 text-primary-500" />
                    <div>
                      <p className="font-semibold text-sm text-primary-900 dark:text-primary-100">
                        基于简历面试（推荐）
                      </p>
                      <p className="text-xs text-primary-600/80 dark:text-primary-400/80">
                        面试官将针对你的简历经历进行针对性提问
                      </p>
                    </div>
                  </div>
                  <select
                    value={config.resumeId || ''}
                    onChange={handleResumeSelect}
                    className="w-full px-4 py-2.5 rounded-lg border border-primary-200 dark:border-primary-700/50
                             bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white
                             focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400
                             transition-shadow"
                  >
                    <option value="">不使用简历（通用提问）</option>
                    {loadingResumes ? (
                      <option disabled>加载中...</option>
                    ) : (
                      resumes.map(r => (
                        <option key={r.id} value={r.id}>{r.filename}</option>
                      ))
                    )}
                  </select>
                </div>

                {/* Phase Selection */}
                <div>
                  <label className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                    <Clock className="w-4 h-4 text-primary-500" />
                    面试阶段
                  </label>
                  <div className="grid grid-cols-1 gap-2">
                    {PHASES.map((phase) => {
                      const enabled = config[phase.key];
                      return (
                        <button
                          key={phase.key}
                          onClick={() => togglePhase(phase.key)}
                          className={`
                            w-full flex items-center gap-4 p-3.5 rounded-xl border-2
                            transition-all duration-200 text-left
                            ${enabled
                              ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                              : 'border-slate-150 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                            }
                          `}
                        >
                          <div className={`
                            w-6 h-6 rounded-md border-2 flex items-center justify-center flex-shrink-0 transition-all
                            ${enabled
                              ? 'border-primary-500 bg-primary-500'
                              : 'border-slate-300 dark:border-slate-600'
                            }
                          `}>
                            {enabled && (
                              <svg className="w-3.5 h-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                              </svg>
                            )}
                          </div>
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2">
                              <p className={`font-semibold text-sm ${enabled ? 'text-slate-900 dark:text-white' : 'text-slate-500 dark:text-slate-400'}`}>
                                {phase.label}
                              </p>
                              <span className={`text-[11px] px-1.5 py-0.5 rounded-md font-medium
                                ${enabled
                                  ? 'bg-primary-100 text-primary-700 dark:bg-primary-900/40 dark:text-primary-300'
                                  : 'bg-slate-100 text-slate-500 dark:bg-slate-700 dark:text-slate-400'
                                }
                              `}>
                                ~{getPhaseMinutes(phase.key)}min
                              </span>
                            </div>
                            <p className="text-xs text-slate-500 dark:text-slate-400 mt-0.5">
                              {phase.description}
                            </p>
                          </div>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* Duration */}
                <div className="bg-slate-50/80 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-150 dark:border-slate-700">
                  <div className="flex items-center justify-between mb-3">
                    <div>
                      <p className="font-semibold text-sm text-slate-900 dark:text-white">
                        计划面试时长
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        已选 {enabledPhasesCount} 个阶段
                      </p>
                    </div>
                    <div className="text-2xl font-bold tabular-nums text-primary-600 dark:text-primary-400">
                      {config.plannedDuration}
                      <span className="text-xs font-normal text-slate-400 ml-0.5">min</span>
                    </div>
                  </div>
                  <input
                    type="range"
                    min="15"
                    max="60"
                    step="5"
                    value={config.plannedDuration}
                    onChange={handleDurationChange}
                    className="w-full h-2 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer
                             [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4
                             [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:rounded-full
                             [&::-webkit-slider-thumb]:bg-primary-500 [&::-webkit-slider-thumb]:cursor-pointer
                             [&::-webkit-slider-thumb]:shadow-md [&::-webkit-slider-thumb]:shadow-primary-500/30
                             [&::-webkit-slider-thumb]:transition-transform [&::-webkit-slider-thumb]:hover:scale-125"
                  />
                  <div className="flex justify-between text-[11px] text-slate-400 mt-1.5 tabular-nums">
                    <span>15 min</span>
                    <span>60 min</span>
                  </div>
                </div>

                {/* Custom JD */}
                <details className="group">
                  <summary className="flex items-center gap-3 cursor-pointer p-3 rounded-xl border border-dashed border-slate-200 dark:border-slate-700 hover:border-slate-300 dark:hover:border-slate-600 transition-colors">
                    <FileText className="w-4 h-4 text-slate-400" />
                    <span className="text-sm text-slate-600 dark:text-slate-400">
                      自定义职位描述（可选）
                    </span>
                    <svg className="w-4 h-4 text-slate-400 ml-auto transition-transform group-open:rotate-180" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                    </svg>
                  </summary>
                  <div className="mt-2">
                    <textarea
                      value={config.customJD || ''}
                      onChange={handleJDChange}
                      placeholder="粘贴目标岗位的职位描述，帮助面试官更有针对性地提问..."
                      rows={4}
                      className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700
                               bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white
                               placeholder:text-slate-400 dark:placeholder:text-slate-500
                               focus:outline-none focus:ring-2 focus:ring-primary-500/50 focus:border-primary-400
                               resize-none transition-shadow"
                    />
                  </div>
                </details>
              </div>

              {/* Footer */}
              <div className="px-6 py-4 bg-slate-50/80 dark:bg-slate-900/50 border-t border-slate-100 dark:border-slate-700/50 rounded-b-2xl">
                <div className="flex gap-3">
                  <motion.button
                    onClick={onClose}
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                    className="flex-1 px-5 py-3 border border-slate-200 dark:border-slate-700
                             text-slate-700 dark:text-slate-300 rounded-xl font-medium text-sm
                             hover:bg-slate-100 dark:hover:bg-slate-800 transition-all"
                  >
                    取消
                  </motion.button>
                  <motion.button
                    onClick={handleStart}
                    disabled={!atLeastOneEnabled}
                    whileHover={atLeastOneEnabled ? { scale: 1.02 } : {}}
                    whileTap={atLeastOneEnabled ? { scale: 0.98 } : {}}
                    className={`
                      flex-1 px-5 py-3 rounded-xl font-semibold text-sm transition-all
                      ${atLeastOneEnabled
                        ? 'bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700 text-white shadow-lg shadow-primary-500/25'
                        : 'bg-slate-200 dark:bg-slate-700 text-slate-400 dark:text-slate-500 cursor-not-allowed'
                      }
                    `}
                  >
                    开始面试
                  </motion.button>
                </div>
              </div>
            </motion.div>
          </div>
        </>
      )}
    </AnimatePresence>
  );
}
