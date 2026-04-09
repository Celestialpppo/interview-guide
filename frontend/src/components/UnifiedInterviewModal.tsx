import { useState, useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import {
  X, Sparkles, FileText, Mic,
  FileStack, ChevronDown, ChevronUp, Loader2
} from 'lucide-react';
import { skillApi, type SkillDTO, type CategoryDTO } from '../api/skill';
import { historyApi, type ResumeListItem } from '../api/history';

export type InterviewMode = 'text' | 'voice';
export type Difficulty = 'junior' | 'mid' | 'senior';

export interface UnifiedInterviewConfig {
  mode: InterviewMode;
  skillId: string;
  skillName: string;
  difficulty: Difficulty;
  resumeId?: number;
  resumeText?: string;
  llmProvider: string;
  // 文字面试
  questionCount: number;
  // 语音面试
  techEnabled: boolean;
  projectEnabled: boolean;
  hrEnabled: boolean;
  plannedDuration: number;
  // 自定义 Skill
  customJdText?: string;
  customCategories?: CategoryDTO[];
}

interface UnifiedInterviewModalProps {
  isOpen: boolean;
  onClose: () => void;
  onStart: (config: UnifiedInterviewConfig) => void;
  defaultMode?: InterviewMode;
  hideModeSwitch?: boolean;
  title?: string;
  subtitle?: string;
  startButtonText?: string;
}

const DIFFICULTY_OPTIONS: { value: Difficulty; label: string; desc: string }[] = [
  { value: 'junior', label: '校招', desc: '0-1 年' },
  { value: 'mid', label: '中级', desc: '1-3 年' },
  { value: 'senior', label: '高级', desc: '3 年+' },
];

export default function UnifiedInterviewModal({
  isOpen,
  onClose,
  onStart,
  defaultMode = 'text',
  hideModeSwitch = false,
  title = '开始模拟面试',
  subtitle = '选择面试模式和主题，快速开始',
  startButtonText = '开始面试',
}: UnifiedInterviewModalProps) {
  const [mode, setMode] = useState<InterviewMode>(defaultMode);
  const [skillId, setSkillId] = useState('java-backend');
  const [difficulty, setDifficulty] = useState<Difficulty>('mid');
  const [skills, setSkills] = useState<SkillDTO[]>([]);
  const [loadingSkills, setLoadingSkills] = useState(false);

  // 更多选项
  const [showMore, setShowMore] = useState(false);
  const [resumeId, setResumeId] = useState<number | undefined>(undefined);
  const [resumes, setResumes] = useState<ResumeListItem[]>([]);
  const [questionCount, setQuestionCount] = useState(6);

  // 语音面试选项
  const [plannedDuration, setPlannedDuration] = useState(30);

  // 自定义 Skill
  const [customJdText, setCustomJdText] = useState('');
  const [customCategories, setCustomCategories] = useState<CategoryDTO[]>([]);
  const [parsingJd, setParsingJd] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setMode(defaultMode);
      Promise.all([loadSkills(), loadResumes()]);
    }
  }, [isOpen, defaultMode]);

  const loadSkills = async () => {
    setLoadingSkills(true);
    try {
      const data = await skillApi.listSkills();
      setSkills(data);
    } catch (err) {
      console.error('Failed to load skills:', err);
    } finally {
      setLoadingSkills(false);
    }
  };

  const loadResumes = async () => {
    try {
      const data = await historyApi.getResumes();
      setResumes(data);
    } catch (err) {
      console.error('Failed to load resumes:', err);
    }
  };

  const handleParseJd = async () => {
    if (!customJdText || customJdText.length < 50) {
      alert('JD 内容太少（至少 50 字），请补充后重试');
      return;
    }
    setParsingJd(true);
    try {
      const categories = await skillApi.parseJd(customJdText);
      setCustomCategories(categories);
    } catch (err) {
      alert('JD 解析失败，请重试或选择预设主题');
    } finally {
      setParsingJd(false);
    }
  };

  const handleStart = () => {
    const selectedSkill = skills.find(s => s.id === skillId);
    const config: UnifiedInterviewConfig = {
      mode,
      skillId: skillId,
      skillName: selectedSkill?.name || '自定义',
      difficulty,
      resumeId,
      llmProvider: 'dashscope',
      questionCount,
      techEnabled: true,
      projectEnabled: true,
      hrEnabled: true,
      plannedDuration,
      customJdText: skillId === 'custom' ? customJdText : undefined,
      customCategories: skillId === 'custom' ? customCategories : undefined,
    };
    onStart(config);
  };

  if (!isOpen) return null;

  const isCustomSkill = skillId === 'custom';

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
              onClick={e => e.stopPropagation()}
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
                        {title}
                      </h2>
                      <p className="text-xs text-slate-500 dark:text-slate-400">
                        {subtitle}
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
                {!hideModeSwitch && (
                  <div>
                    <label className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                      面试模式
                    </label>
                    <div className="grid grid-cols-2 gap-2">
                      {([
                        { value: 'text' as InterviewMode, label: '文字面试', icon: FileText, desc: '逐题答题，AI 评估' },
                        { value: 'voice' as InterviewMode, label: '语音面试', icon: Mic, desc: '实时语音对话' },
                      ]).map(opt => {
                        const Icon = opt.icon;
                        const selected = mode === opt.value;
                        return (
                          <button
                            key={opt.value}
                            onClick={() => setMode(opt.value)}
                            className={`flex items-center gap-3 p-3 rounded-xl border-2 transition-all duration-200 text-left
                              ${selected
                                ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                                : 'border-slate-150 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                              }`}
                          >
                            <Icon className={`w-5 h-5 flex-shrink-0 ${selected ? 'text-primary-500' : 'text-slate-400'}`} />
                            <div className="min-w-0">
                              <p className={`font-semibold text-sm ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-900 dark:text-white'}`}>
                                {opt.label}
                              </p>
                              <p className="text-[11px] text-slate-500 dark:text-slate-400">{opt.desc}</p>
                            </div>
                          </button>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* 面试模板（统一选择器，文字和语音共用） */}
                <div>
                  <label className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                    面试方向
                  </label>
                  {loadingSkills ? (
                    <div className="flex items-center gap-2 py-4 text-slate-400">
                      <Loader2 className="w-4 h-4 animate-spin" />
                      <span className="text-sm">加载中...</span>
                    </div>
                  ) : (
                    <div className="grid grid-cols-2 gap-2">
                      {skills.map(skill => {
                        const selected = skillId === skill.id;
                        const icon = skill.display?.icon || '📋';
                        return (
                          <button
                            key={skill.id}
                            onClick={() => setSkillId(skill.id)}
                            className={`flex items-center gap-3 p-3 rounded-xl border-2 transition-all duration-200 text-left
                              ${selected
                                ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                                : 'border-slate-150 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                              }`}
                          >
                            <div className={`w-9 h-9 rounded-lg flex items-center justify-center text-base flex-shrink-0 ${
                              selected ? skill.display?.iconBg || 'bg-primary-100 dark:bg-primary-900/50' : 'bg-slate-100 dark:bg-slate-700'
                            }`}>
                              <span className={selected ? (skill.display?.iconColor || 'text-primary-600') : ''}>{icon}</span>
                            </div>
                            <div className="flex-1 min-w-0">
                              <span className={`text-xs font-medium block truncate ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-700 dark:text-slate-300'}`}>
                                {skill.name}
                              </span>
                              <span className="text-[10px] text-slate-400 truncate block">
                                {skill.description}
                              </span>
                            </div>
                          </button>
                        );
                      })}
                      {/* 自定义按钮 */}
                      <button
                        onClick={() => setSkillId('custom')}
                        className={`flex items-center gap-3 p-3 rounded-xl border-2 border-dashed transition-all duration-200 text-left
                          ${isCustomSkill
                            ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                            : 'border-slate-200 dark:border-slate-700 hover:border-primary-300 dark:hover:border-primary-600'
                          }`}
                      >
                        <div className="w-9 h-9 rounded-lg flex items-center justify-center bg-slate-100 dark:bg-slate-700 flex-shrink-0">
                          <span className="text-base">✨</span>
                        </div>
                        <div className="flex-1 min-w-0">
                          <span className={`text-xs font-medium block ${isCustomSkill ? 'text-primary-700 dark:text-primary-300' : 'text-slate-500 dark:text-slate-400'}`}>
                            自定义 JD
                          </span>
                        </div>
                      </button>
                    </div>
                  )}
                </div>

                {/* 自定义 JD 输入 */}
                <AnimatePresence>
                  {isCustomSkill && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="overflow-hidden"
                    >
                      <div className="space-y-3 bg-slate-50 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-200 dark:border-slate-700">
                        <textarea
                          value={customJdText}
                          onChange={e => setCustomJdText(e.target.value)}
                          placeholder="粘贴目标岗位的职位描述（JD），至少 50 字..."
                          rows={4}
                          className="w-full px-4 py-3 rounded-xl border border-slate-200 dark:border-slate-700
                            bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white
                            placeholder:text-slate-400 resize-none focus:outline-none focus:ring-2
                            focus:ring-primary-500/50 focus:border-primary-400 transition-shadow"
                        />
                        <button
                          onClick={handleParseJd}
                          disabled={parsingJd || !customJdText}
                          className="flex items-center gap-2 px-4 py-2 text-sm font-medium rounded-lg
                            bg-primary-500 text-white hover:bg-primary-600 disabled:opacity-50
                            disabled:cursor-not-allowed transition-colors"
                        >
                          {parsingJd ? <Loader2 className="w-4 h-4 animate-spin" /> : <Sparkles className="w-4 h-4" />}
                          解析面试方向
                        </button>
                        {customCategories.length > 0 && (
                          <div className="flex flex-wrap gap-2">
                            {customCategories.map((cat, i) => (
                              <span
                                key={i}
                                className="px-3 py-1 text-xs font-medium rounded-full bg-primary-100 dark:bg-primary-900/30 text-primary-700 dark:text-primary-300"
                              >
                                {cat.label}
                                <span className="ml-1 text-[10px] text-primary-500">({cat.priority})</span>
                              </span>
                            ))}
                          </div>
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* 难度 */}
                <div>
                  <label className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                    难度
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    {DIFFICULTY_OPTIONS.map(opt => {
                      const selected = difficulty === opt.value;
                      return (
                        <button
                          key={opt.value}
                          onClick={() => setDifficulty(opt.value)}
                          className={`py-2.5 px-3 rounded-xl border-2 transition-all duration-200 text-center
                            ${selected
                              ? 'border-primary-500 bg-primary-50/80 dark:bg-primary-900/20'
                              : 'border-slate-150 dark:border-slate-700 bg-white dark:bg-slate-800 hover:border-slate-300 dark:hover:border-slate-600'
                            }`}
                        >
                          <p className={`text-sm font-semibold ${selected ? 'text-primary-700 dark:text-primary-300' : 'text-slate-700 dark:text-slate-300'}`}>
                            {opt.label}
                          </p>
                          <p className="text-[11px] text-slate-400">{opt.desc}</p>
                        </button>
                      );
                    })}
                  </div>
                </div>

                {/* 更多选项 */}
                <button
                  onClick={() => setShowMore(!showMore)}
                  className="w-full flex items-center gap-2 py-2 text-sm text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-300 transition-colors"
                >
                  {showMore ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
                  <span>更多选项</span>
                  <div className="flex-1 border-t border-slate-200 dark:border-slate-700" />
                </button>

                <AnimatePresence>
                  {showMore && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="overflow-hidden space-y-4"
                    >
                      {/* 简历选择 */}
                      <div className="bg-gradient-to-br from-primary-50/80 to-blue-50/80 dark:from-primary-900/20 dark:to-blue-900/10 rounded-xl p-4 border border-primary-100 dark:border-primary-800/30">
                        <div className="flex items-center gap-3 mb-3">
                          <FileStack className="w-5 h-5 text-primary-500" />
                          <div>
                            <p className="font-semibold text-sm text-primary-900 dark:text-primary-100">
                              基于简历面试（可选）
                            </p>
                          </div>
                        </div>
                        <select
                          value={resumeId || ''}
                          onChange={e => setResumeId(e.target.value ? parseInt(e.target.value) : undefined)}
                          className="w-full px-4 py-2.5 rounded-lg border border-primary-200 dark:border-primary-700/50
                            bg-white dark:bg-slate-800 text-sm text-slate-900 dark:text-white
                            focus:outline-none focus:ring-2 focus:ring-primary-500/50 transition-shadow"
                        >
                          <option value="">不使用简历（通用提问）</option>
                          {resumes.map(r => (
                            <option key={r.id} value={r.id}>{r.filename}</option>
                          ))}
                        </select>
                      </div>

                      {/* 文字面试 - 题目数 */}
                      {mode === 'text' && (
                        <div>
                          <label className="flex items-center gap-2 mb-3 text-sm font-semibold text-slate-700 dark:text-slate-200">
                            题目数量
                          </label>
                          <div className="flex gap-2">
                            {[6, 8, 10, 12].map(n => (
                              <button
                                key={n}
                                onClick={() => setQuestionCount(n)}
                                className={`flex-1 py-2 rounded-lg text-sm font-medium transition-all
                                  ${questionCount === n
                                    ? 'bg-primary-500 text-white shadow-sm'
                                    : 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 hover:bg-slate-200 dark:hover:bg-slate-600'
                                  }`}
                              >
                                {n} 题
                              </button>
                            ))}
                          </div>
                        </div>
                      )}

                      {/* 语音面试 - 时长 */}
                      {mode === 'voice' && (
                        <div className="bg-slate-50/80 dark:bg-slate-900/50 rounded-xl p-4 border border-slate-150 dark:border-slate-700">
                          <div className="flex items-center justify-between mb-3">
                            <p className="font-semibold text-sm text-slate-900 dark:text-white">计划面试时长</p>
                            <div className="text-2xl font-bold tabular-nums text-primary-600 dark:text-primary-400">
                              {plannedDuration}
                              <span className="text-xs font-normal text-slate-400 ml-0.5">min</span>
                            </div>
                          </div>
                          <input
                            type="range"
                            min="15"
                            max="60"
                            step="5"
                            value={plannedDuration}
                            onChange={e => setPlannedDuration(parseInt(e.target.value))}
                            className="w-full h-2 bg-slate-200 dark:bg-slate-700 rounded-lg appearance-none cursor-pointer
                              [&::-webkit-slider-thumb]:appearance-none [&::-webkit-slider-thumb]:w-4
                              [&::-webkit-slider-thumb]:h-4 [&::-webkit-slider-thumb]:rounded-full
                              [&::-webkit-slider-thumb]:bg-primary-500 [&::-webkit-slider-thumb]:cursor-pointer
                              [&::-webkit-slider-thumb]:shadow-md [&::-webkit-slider-thumb]:shadow-primary-500/30"
                          />
                        </div>
                      )}
                    </motion.div>
                  )}
                </AnimatePresence>
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
                    whileHover={{ scale: 1.02 }}
                    whileTap={{ scale: 0.98 }}
                    className="flex-1 px-5 py-3 rounded-xl font-semibold text-sm transition-all
                      bg-gradient-to-r from-primary-500 to-primary-600 hover:from-primary-600 hover:to-primary-700 text-white shadow-lg shadow-primary-500/25"
                  >
                      {startButtonText}
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
