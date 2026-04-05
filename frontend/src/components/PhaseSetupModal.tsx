import React, { useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { X, Clock, FileText, FileStack, Users, Bot } from 'lucide-react';
import { historyApi, ResumeListItem } from '../api/history';

export interface PhaseConfig {
  techEnabled: boolean;
  projectEnabled: boolean;
  hrEnabled: boolean;
  plannedDuration: number;
  customJD?: string;
  resumeId?: number; // Add resumeId
  roleType?: string; // Add roleType
  llmProvider?: string;
}

interface PhaseSetupModalProps {
  isOpen: boolean;
  onClose: () => void;
  onStart: (config: PhaseConfig) => void;
  roleType: string;
  onRoleTypeChange?: (roleType: string) => void; // Add callback for role type changes
}

const INTERVIEWER_TYPES = [
  {
    value: 'ali-p8',
    label: '阿里P8后端面试',
    description: '后端技术、系统设计、高并发',
    icon: '🎯',
  },
  {
    value: 'byteance-algo',
    label: '字节算法工程师面试',
    description: '算法、数据结构、机器学习',
    icon: '🤖',
  },
  {
    value: 'tencent-backend',
    label: '腾讯后台开发面试',
    description: '后台开发、网络编程、分布式',
    icon: '💻',
  },
];

const LLM_PROVIDERS = [
  {
    value: 'dashscope',
    label: 'Aliyun (DashScope)',
    icon: '☁️',
  },
  {
    value: 'lmstudio',
    label: 'Local (LM Studio)',
    icon: '🏠',
  },
];

const PHASES = [
  {
    key: 'techEnabled' as const,
    label: '技术问题',
    description: '深入考察技术能力和编程基础',
    icon: '💻',
    estimatedMinutes: 15,
  },
  {
    key: 'projectEnabled' as const,
    label: '项目深挖',
    description: '探讨项目细节、难点和解决方案',
    icon: '🚀',
    estimatedMinutes: 15,
  },
  {
    key: 'hrEnabled' as const,
    label: 'HR问题',
    description: '职业规划、薪资期望、团队协作',
    icon: '🤝',
    estimatedMinutes: 5,
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
    roleType: roleType, // Initialize with prop value
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
      // Remove filter because the API might not return analyzeStatus yet or it's always considered ready if it has a latestScore
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

      // Auto-adjust planned duration based on selected phases
      const estimatedMinutes = PHASES.reduce((total, phase) => {
        return total + (newConfig[phase.key] ? phase.estimatedMinutes : 0);
      }, 0);

      // Round to nearest 5 minutes
      newConfig.plannedDuration = Math.max(15, Math.min(60, Math.round(estimatedMinutes / 5) * 5));

      return newConfig;
    });
  };

  const handleDurationChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = parseInt(e.target.value);
    setConfig((prev) => ({ ...prev, plannedDuration: value }));
  };

  const handleJDChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    setConfig((prev) => ({ ...prev, customJD: e.target.value }));
  };

  const handleResumeSelect = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const val = e.target.value;
    setConfig((prev) => ({
      ...prev,
      resumeId: val ? parseInt(val, 10) : undefined,
    }));
  };

  const handleRoleTypeChange = (roleType: string) => {
    setConfig((prev) => ({ ...prev, roleType }));
    // Notify parent component about role type change
    if (onRoleTypeChange) {
      onRoleTypeChange(roleType);
    }
  };

  const handleLlmProviderChange = (llmProvider: string) => {
    setConfig((prev) => ({ ...prev, llmProvider }));
  };

  const atLeastOneEnabled = PHASES.some((phase) => config[phase.key]);

  const handleStart = async () => {
    console.log('handleStart called, atLeastOneEnabled:', atLeastOneEnabled);
    console.log('Current config:', config);

    if (!atLeastOneEnabled) {
      console.warn('No phase selected, cannot start');
      alert('请至少选择一个面试阶段');
      return;
    }

    try {
      console.log('Calling onStart with config:', config);
      // onStart will handle closing the modal
      await onStart(config);
      console.log('onStart completed successfully');
    } catch (error) {
      console.error('Error in handleStart:', error);
      const message = error instanceof Error ? error.message : '启动面试失败';
      alert('启动面试失败：' + message);
    }
  };

  const estimatedTotalMinutes = PHASES.reduce((total, phase) => {
    return total + (config[phase.key] ? phase.estimatedMinutes : 0);
  }, 0);

  return (
    <AnimatePresence>
      {isOpen && (
        <>
          {/* Background overlay */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            onClick={onClose}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm z-50"
          />

          {/* Modal dialog */}
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
            <motion.div
              initial={{ opacity: 0, scale: 0.95, y: 20 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.95, y: 20 }}
              onClick={(e) => e.stopPropagation()}
              className="bg-white dark:bg-slate-800 rounded-2xl shadow-2xl max-w-2xl w-full max-h-[90vh] overflow-y-auto"
            >
              {/* Header */}
              <div className="px-6 py-4 border-b border-slate-200 dark:border-slate-700">
                <div className="flex items-center justify-between">
                  <div>
                    <h2 className="text-xl font-bold text-slate-900 dark:text-white">
                      配置面试参数
                    </h2>
                  </div>
                  <button
                    onClick={onClose}
                    className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                  >
                    <X className="w-6 h-6" />
                  </button>
                </div>
              </div>

              {/* Content */}
              <div className="px-6 py-4 space-y-6">

                {/* Interviewer Type Selection */}
                <div>
                  <div className="flex items-center gap-2 mb-3">
                    <Users className="w-5 h-5 text-primary-500" />
                    <p className="text-sm font-semibold text-slate-900 dark:text-white">
                      选择面试官类型
                    </p>
                  </div>
                  <div className="grid grid-cols-1 gap-2">
                    {INTERVIEWER_TYPES.map((type) => (
                      <button
                        key={type.value}
                        onClick={() => handleRoleTypeChange(type.value)}
                        className={`
                          w-full flex items-center gap-3 p-3 rounded-xl border-2
                          transition-all duration-200 text-left
                          ${config.roleType === type.value
                            ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                            : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                          }
                        `}
                      >
                        <div className="text-2xl flex-shrink-0">{type.icon}</div>
                        <div className="flex-1 min-w-0">
                          <p className="font-semibold text-slate-900 dark:text-white text-sm">
                            {type.label}
                          </p>
                          <p className="text-xs text-slate-600 dark:text-slate-400">
                            {type.description}
                          </p>
                        </div>
                        <div className={`
                          w-5 h-5 rounded-full border-2 flex items-center justify-center flex-shrink-0
                          ${config.roleType === type.value
                            ? 'border-primary-500 bg-primary-500'
                            : 'border-slate-300 dark:border-slate-600'
                          }
                        `}>
                          {config.roleType === type.value && (
                            <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                            </svg>
                          )}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* LLM Provider Selection */}
                <div>
                  <div className="flex items-center gap-2 mb-3">
                    <Bot className="w-5 h-5 text-primary-500" />
                    <p className="text-sm font-semibold text-slate-900 dark:text-white">
                      选择 LLM 模型供应商
                    </p>
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    {LLM_PROVIDERS.map((provider) => (
                      <button
                        key={provider.value}
                        onClick={() => handleLlmProviderChange(provider.value)}
                        className={`
                          flex items-center gap-3 p-3 rounded-xl border-2
                          transition-all duration-200 text-left
                          ${config.llmProvider === provider.value
                            ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                            : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                          }
                        `}
                      >
                        <div className="text-2xl flex-shrink-0">{provider.icon}</div>
                        <div className="flex-1 min-w-0">
                          <p className="font-semibold text-slate-900 dark:text-white text-sm">
                            {provider.label}
                          </p>
                        </div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Resume Selection */}
                <div className="bg-primary-50 dark:bg-primary-900/20 rounded-xl p-4 border border-primary-100 dark:border-primary-800/30">
                  <div className="flex items-center gap-3 mb-3">
                    <FileStack className="w-5 h-5 text-primary-500" />
                    <div>
                      <p className="font-semibold text-primary-900 dark:text-primary-100">
                        基于简历面试（推荐）
                      </p>
                      <p className="text-xs text-primary-600 dark:text-primary-400">
                        选择简历后，面试官将针对你的经历提问，并可跳过自我介绍环节。
                      </p>
                    </div>
                  </div>
                  <select
                    value={config.resumeId || ''}
                    onChange={handleResumeSelect}
                    className="w-full px-4 py-2.5 rounded-lg border border-primary-200 dark:border-primary-700/50
                             bg-white dark:bg-slate-800 text-slate-900 dark:text-white
                             focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent"
                  >
                    <option value="">不使用简历（通用提问）</option>
                    {loadingResumes ? (
                      <option disabled>加载简历中...</option>
                    ) : (
                      resumes.map(r => (
                        <option key={r.id} value={r.id}>
                          {r.filename} 
                        </option>
                      ))
                    )}
                  </select>
                </div>

                {/* Phase selection */}
                <div>
                  <p className="text-sm text-slate-600 dark:text-slate-400 mb-4">
                    选择要进行的面试阶段（至少选择一个）
                  </p>
                  <div className="grid grid-cols-1 gap-3">
                    {PHASES.map((phase) => (
                      <button
                        key={phase.key}
                        onClick={() => togglePhase(phase.key)}
                        className={`
                          w-full flex items-center gap-4 p-4 rounded-xl border-2
                          transition-all duration-200 text-left
                          ${config[phase.key]
                            ? 'border-primary-500 bg-primary-50 dark:bg-primary-900/20'
                            : 'border-slate-200 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                          }
                        `}
                      >
                        {/* Icon */}
                        <div className={`
                          text-2xl flex-shrink-0
                          ${config[phase.key] ? 'opacity-100' : 'opacity-40'}
                        `}>
                          {phase.icon}
                        </div>

                        {/* Content */}
                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 mb-1">
                            <p className="font-semibold text-slate-900 dark:text-white">
                              {phase.label}
                            </p>
                            <span className={`
                              text-xs px-2 py-0.5 rounded-full
                              ${config[phase.key]
                                ? 'bg-primary-500 text-white'
                                : 'bg-slate-200 dark:bg-slate-700 text-slate-600 dark:text-slate-400'
                              }
                            `}>
                              约{phase.estimatedMinutes}分钟
                            </span>
                          </div>
                          <p className="text-sm text-slate-600 dark:text-slate-400">
                            {phase.description}
                          </p>
                        </div>

                        {/* Checkbox indicator */}
                        <div className={`
                          w-6 h-6 rounded-lg border-2 flex items-center justify-center flex-shrink-0
                          ${config[phase.key]
                            ? 'border-primary-500 bg-primary-500'
                            : 'border-slate-300 dark:border-slate-600'
                          }
                        `}>
                          {config[phase.key] && (
                            <svg className="w-4 h-4 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M5 13l4 4L19 7" />
                            </svg>
                          )}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>

                {/* Duration slider */}
                <div className="bg-slate-50 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
                  <div className="flex items-center gap-3 mb-3">
                    <Clock className="w-5 h-5 text-primary-500" />
                    <div className="flex-1">
                      <p className="font-semibold text-slate-900 dark:text-white">
                        计划面试时长
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        根据当前选择，预计约 {estimatedTotalMinutes} 分钟
                      </p>
                    </div>
                    <div className="text-2xl font-bold text-primary-600 dark:text-primary-400">
                      {config.plannedDuration}
                      <span className="text-sm text-slate-500 dark:text-slate-400 ml-1">分钟</span>
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
                             [&::-webkit-slider-thumb]:transition-transform [&::-webkit-slider-thumb]:hover:scale-110"
                  />
                  <div className="flex justify-between text-xs text-slate-500 dark:text-slate-400 mt-1">
                    <span>15分钟</span>
                    <span>60分钟</span>
                  </div>
                </div>

                {/* Custom JD input */}
                <div className="bg-slate-50 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
                  <div className="flex items-center gap-3 mb-3">
                    <FileText className="w-5 h-5 text-primary-500" />
                    <div>
                      <p className="font-semibold text-slate-900 dark:text-white">
                        自定义职位描述（可选）
                      </p>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        提供 JD 可帮助面试官更有针对性地提问
                      </p>
                    </div>
                  </div>
                  <textarea
                    value={config.customJD || ''}
                    onChange={handleJDChange}
                    placeholder="粘贴目标岗位的职位描述（JD），例如：&#10;&#10;岗位名称：高级 Java 工程师&#10;职责：...&#10;要求：..."
                    rows={4}
                    className="w-full px-4 py-3 rounded-lg border border-slate-200 dark:border-slate-700
                             bg-white dark:bg-slate-800 text-slate-900 dark:text-white
                             placeholder:text-slate-400 dark:placeholder:text-slate-500
                             focus:outline-none focus:ring-2 focus:ring-primary-500 focus:border-transparent
                             resize-none"
                  />
                </div>
              </div>

              {/* Footer */}
              <div className="px-6 py-4 bg-slate-50 dark:bg-slate-900/50 border-t border-slate-200 dark:border-slate-700">
                <div className="flex gap-3">
                  <motion.button
                    onClick={onClose}
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                    className="flex-1 px-5 py-3 border border-slate-200 dark:border-slate-700
                             text-slate-700 dark:text-slate-300 rounded-xl font-medium
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
                      flex-1 px-5 py-3 rounded-xl font-semibold transition-all
                      ${atLeastOneEnabled
                        ? 'bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700 text-white shadow-lg'
                        : 'bg-slate-300 dark:bg-slate-700 text-slate-500 dark:text-slate-400 cursor-not-allowed'
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
