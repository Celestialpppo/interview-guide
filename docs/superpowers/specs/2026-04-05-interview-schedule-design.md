# 面试日程管理系统设计文档

**日期**: 2026-04-05
**功能**: 独立的面试日程管理模块，支持文本解析、日历视图、拖拽调整

---

## 1. 概述

### 1.1 功能定位

一个独立的面试日程管理系统，不与现有简历、模拟面试功能耦合，提供：

- **文本解析**：支持飞书、腾讯会议、Zoom 等格式的面试邀约文本自动识别
- **日历视图**：默认周视图，支持月视图和列表视图切换
- **拖拽调整**：类似微软 Outlook 的拖拽调整面试时间功能
- **状态管理**：自动更新面试状态，支持手动修改

### 1.2 技术栈选择

**方案**: React Big Calendar

**理由**:
- 成熟的日历组件库，支持周/月/日视图切换
- 内置拖拽功能，配置简单
- 社区活跃，文档完善
- TypeScript 支持良好
- 包体积可接受（~50KB gzipped）

---

## 2. 数据库设计

### 2.1 表结构

```sql
CREATE TABLE interview_schedule (
    id BIGSERIAL PRIMARY KEY,
    company_name VARCHAR(255) NOT NULL,           -- 公司名称
    position VARCHAR(255) NOT NULL,               -- 岗位
    interview_time TIMESTAMP NOT NULL,            -- 面试时间
    interview_type VARCHAR(50),                   -- 面试形式(ONSITE/VIDEO/PHONE)
    meeting_link TEXT,                            -- 会议链接
    round_number INTEGER DEFAULT 1,               -- 第几轮面试
    interviewer VARCHAR(255),                     -- 面试官
    notes TEXT,                                   -- 备注
    status VARCHAR(50) DEFAULT 'PENDING',         -- 状态
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引
CREATE INDEX idx_interview_time ON interview_schedule(interview_time);
CREATE INDEX idx_status ON interview_schedule(status);
CREATE INDEX idx_company ON interview_schedule(company_name);
```

### 2.2 状态枚举

| 状态 | 值 | 说明 |
|------|------|------|
| 待面试 | `PENDING` | 默认状态 |
| 已完成 | `COMPLETED` | 面试时间已过或手动标记 |
| 已取消 | `CANCELLED` | 用户手动取消 |
| 已改期 | `RESCHEDULED` | 拖拽调整时间后 |

### 2.3 自动状态更新

**定时任务**: 每小时执行一次，检查所有 `PENDING` 状态且面试时间已过的记录，自动更新为 `COMPLETED`。

---

## 3. 前端设计

### 3.1 路由与导航

**路由**: `/interview-schedule`

**导航入口**: 在 `Layout.tsx` 侧边栏，"问答助手"下方添加：
```typescript
{
  icon: Calendar,
  label: '面试日程',
  path: '/interview-schedule'
}
```

### 3.2 组件结构

```
InterviewSchedulePage.tsx (主页面)
├── ScheduleHeader.tsx (顶部操作栏)
│   ├── 视图切换 (周视图/月视图/列表)
│   ├── 日期导航 (上一周/下一周/今天)
│   └── 添加面试按钮
│
├── ScheduleCalendar.tsx (日历组件)
│   └── react-big-calendar (周/月视图)
│       └── InterviewEvent.tsx (自定义事件组件)
│
├── ScheduleList.tsx (列表视图)
│   └── InterviewListItem.tsx (列表项)
│
└── InterviewFormModal.tsx (添加/编辑弹窗)
    ├── TextInputStep.tsx (原始文本输入)
    ├── ParseResultStep.tsx (解析结果预览)
    └── ManualFormStep.tsx (通用表单模板)
```

### 3.3 日历事件展示

**事件卡片内容**:
- 公司名称
- 岗位名称
- 第几轮面试

**颜色编码**:
- `PENDING` - 蓝色（主题色）
- `COMPLETED` - 绿色
- `CANCELLED` - 灰色
- `RESCHEDULED` - 橙色

**点击事件**: 弹出详情卡片（Popover），显示完整信息和操作按钮（编辑、删除、改期）

### 3.4 添加/编辑弹窗流程

**步骤 1: 文本输入**
- 大文本框，用户粘贴飞书/腾讯会议/Zoom 邀约文本
- "解析"按钮触发解析

**步骤 2: 解析结果预览**
- 显示解析成功/失败状态
- 显示提取的字段（公司、岗位、时间等）
- 显示置信度和解析方法（规则/AI）
- 可展开查看详细日志
- 操作按钮："确认并编辑" / "重新输入"

**步骤 3: 手动表单**
- 无论解析成功或失败，都可进入此表单编辑
- 表单字段：
  - 公司名称（必填）
  - 岗位（必填）
  - 面试时间（必填）
  - 面试形式（现场/视频/电话）
  - 会议链接
  - 第几轮面试（默认 1）
  - 面试官
  - 备注

### 3.5 拖拽与确认机制

**流程**:
1. 用户拖拽事件到新时间位置
2. 系统临时存储变更，不立即保存
3. 显示确认对话框："确认调整面试时间吗？"
4. 用户点击"确认"：批量保存所有待确认的变更
5. 用户点击"取消"：恢复原状态

**技术实现**:
- 使用 `pendingChanges` Map 存储待确认的变更
- 确认后批量调用 API 更新
- 取消后清空 Map，不刷新数据

### 3.6 列表视图

**排序**: 按面试时间升序排列

**列表项内容**:
- 状态徽章 + 面试时间
- 公司名称
- 岗位名称
- 第几轮 + 面试形式
- 会议链接（如有）
- 操作按钮（编辑、删除）

### 3.7 UI 风格一致性

- 使用 Tailwind CSS
- 与现有卡片、按钮、表单样式保持一致
- 配色使用项目主题色（primary-500/600）
- 圆角、阴影、间距遵循现有设计规范

---

## 4. 后端设计

### 4.1 REST API

**基础路径**: `/api/interview-schedule`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/` | 获取所有面试（支持筛选） |
| GET | `/{id}` | 获取单个面试详情 |
| POST | `/` | 创建面试 |
| POST | `/parse` | 解析文本并返回结果 |
| PUT | `/{id}` | 更新面试信息 |
| DELETE | `/{id}` | 删除面试 |
| PATCH | `/{id}/status` | 更新面试状态 |

### 4.2 请求/响应示例

#### 解析接口

```json
// POST /api/interview-schedule/parse
// Request
{
  "rawText": "飞书会议邀约文本...",
  "source": "feishu"
}

// Response (成功)
{
  "success": true,
  "data": {
    "companyName": "字节跳动",
    "position": "前端工程师",
    "interviewTime": "2026-04-10T14:00:00",
    "interviewType": "VIDEO",
    "meetingLink": "https://meeting.feishu.cn/xxx",
    "roundNumber": 2,
    "interviewer": "张三"
  },
  "confidence": 0.95,
  "parseMethod": "rule",
  "log": "规则匹配成功：飞书格式，提取字段..."
}

// Response (失败)
{
  "success": false,
  "data": null,
  "confidence": 0,
  "parseMethod": "ai",
  "log": "规则解析失败：未识别格式。AI 解析失败：无法提取公司名称..."
}
```

#### 创建接口

```json
// POST /api/interview-schedule/
// Request
{
  "companyName": "字节跳动",
  "position": "前端工程师",
  "interviewTime": "2026-04-10T14:00:00",
  "interviewType": "VIDEO",
  "meetingLink": "https://meeting.feishu.cn/xxx",
  "roundNumber": 2,
  "interviewer": "张三",
  "notes": "技术面试"
}

// Response
{
  "id": 1,
  "companyName": "字节跳动",
  "position": "前端工程师",
  "interviewTime": "2026-04-10T14:00:00",
  "interviewType": "VIDEO",
  "meetingLink": "https://meeting.feishu.cn/xxx",
  "roundNumber": 2,
  "interviewer": "张三",
  "notes": "技术面试",
  "status": "PENDING",
  "createdAt": "2026-04-05T10:00:00",
  "updatedAt": "2026-04-05T10:00:00"
}
```

### 4.3 文本解析逻辑

#### 解析流程

```
原始文本
    ↓
【规则引擎】
├── 飞书格式识别（正则 + 关键词）
├── 腾讯会议格式识别
├── Zoom 格式识别
└── 通用格式识别
    ↓
成功? → 返回结果（parseMethod: "rule"）
    ↓ 否
【AI 解析】
├── 调用 Spring AI
├── 使用结构化输出（Structured Output）
└── 提取字段
    ↓
成功? → 返回结果（parseMethod: "ai"）
    ↓ 否
返回失败 + 详细日志
```

#### 规则引擎实现

**飞书格式**:
- 关键词："飞书会议"、"Feishu Meeting"
- 提取字段：
  - 时间：正则 `时间：(\d{4}-\d{2}-\d{2} \d{2}:\d{2})`
  - 会议链接：正则 `https://meeting\.feishu\.cn/[^\n]+`
  - 会议号、密码等

**腾讯会议格式**:
- 关键词："腾讯会议"、"Tencent Meeting"
- 提取字段：
  - 会议号：正则 `会议号：(\d{9,})`
  - 密码：正则 `密码：(\d{4,})`
  - 时间、链接等

**Zoom 格式**:
- 关键词："Zoom Meeting"、"Join Zoom Meeting"
- 提取字段：
  - Join URL：正则 `https://zoom\.us/j/[^\n]+`
  - Meeting ID：正则 `Meeting ID:\s*(\d+)`
  - Passcode、时间等

#### AI 解析实现

**Prompt 模板**:
```
你是一个面试邀约信息提取助手。从以下文本中提取面试信息：
- 公司名称（companyName）
- 岗位名称（position）
- 面试时间（interviewTime，转换为 ISO 8601 格式）
- 面试形式（interviewType: ONSITE/VIDEO/PHONE）
- 会议链接（meetingLink）
- 第几轮面试（roundNumber，数字）
- 面试官（interviewer，如有）
- 其他备注（notes）

文本：
{{rawText}}

请以 JSON 格式返回结果，字段名使用英文。如果某字段无法提取，返回 null。
```

**结构化输出配置**:
```java
@Bean
public ChatClient chatClient(ChatClient.Builder builder) {
    return builder
        .defaultSystem("你是面试邀约信息提取助手")
        .build();
}

// 使用 Spring AI 的 Structured Output
InterviewParseResult result = chatClient.prompt()
    .user(promptText)
    .call()
    .entity(InterviewParseResult.class);
```

### 4.4 后端模块划分

```
modules/interviewschedule/
├── InterviewScheduleController.java
├── model/
│   ├── InterviewScheduleEntity.java
│   ├── InterviewScheduleDTO.java
│   ├── CreateInterviewRequest.java
│   ├── ParseRequest.java
│   ├── ParseResponse.java
│   └── InterviewStatus.java (enum)
├── repository/
│   └── InterviewScheduleRepository.java
└── service/
    ├── InterviewScheduleService.java
    ├── InterviewParseService.java
    ├── RuleParseService.java      (规则解析)
    ├── AIParseService.java        (AI 解析)
    └── ScheduleStatusUpdater.java (定时任务)
```

---

## 5. 前端依赖与实现细节

### 5.1 新增依赖

```json
{
  "dependencies": {
    "react-big-calendar": "^1.13.0",
    "dayjs": "^1.11.13",
    "@dnd-kit/core": "^6.1.0",
    "@dnd-kit/modifiers": "^7.0.0"
  },
  "devDependencies": {
    "@types/react-big-calendar": "^1.8.6"
  }
}
```

### 5.2 状态管理

使用 React hooks（useState + useEffect）管理状态：

```typescript
// hooks/useInterviewSchedule.ts
export function useInterviewSchedule() {
  const [interviews, setInterviews] = useState<InterviewSchedule[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const fetchInterviews = async (filters?: Filters) => { /* ... */ };
  const createInterview = async (data: InterviewFormData) => { /* ... */ };
  const updateInterview = async (id: number, data: Partial<InterviewFormData>) => { /* ... */ };
  const deleteInterview = async (id: number) => { /* ... */ };
  const updateStatus = async (id: number, status: InterviewStatus) => { /* ... */ };

  return {
    interviews,
    loading,
    error,
    fetchInterviews,
    createInterview,
    updateInterview,
    deleteInterview,
    updateStatus
  };
}
```

### 5.3 日历配置

```typescript
import { Calendar, dayjsLocalizer, View } from 'react-big-calendar';
import 'react-big-calendar/lib/css/react-big-calendar.css';

const localizer = dayjsLocalizer(dayjs);

// 自定义样式
const eventStyleGetter = (event: InterviewEvent) => {
  const colors = {
    PENDING: { backgroundColor: '#3b82f6', borderColor: '#2563eb' },
    COMPLETED: { backgroundColor: '#10b981', borderColor: '#059669' },
    CANCELLED: { backgroundColor: '#6b7280', borderColor: '#4b5563' },
    RESCHEDULED: { backgroundColor: '#f59e0b', borderColor: '#d97706' },
  };
  return { style: colors[event.status] };
};

<Calendar
  localizer={localizer}
  events={events}
  view={view}              // 'week' | 'month'
  onView={setView}
  date={date}
  onNavigate={setDate}
  eventPropGetter={eventStyleGetter}
  selectable
  onSelectEvent={handleSelectEvent}
  onEventDrop={handleEventDrop}
  resizable
  onEventResize={handleEventResize}
/>
```

### 5.4 API 客户端

```typescript
// api/interviewSchedule.ts
import { request } from './request';

export const interviewScheduleApi = {
  getAll: (params?: { status?: string; start?: string; end?: string }) =>
    request.get('/interview-schedule', { params }),

  getById: (id: number) =>
    request.get(`/interview-schedule/${id}`),

  create: (data: InterviewFormData) =>
    request.post('/interview-schedule', data),

  parse: (rawText: string, source?: string) =>
    request.post('/interview-schedule/parse', { rawText, source }),

  update: (id: number, data: Partial<InterviewFormData>) =>
    request.put(`/interview-schedule/${id}`, data),

  delete: (id: number) =>
    request.delete(`/interview-schedule/${id}`),

  updateStatus: (id: number, status: InterviewStatus) =>
    request.patch(`/interview-schedule/${id}/status`, { status }),
};
```

---

## 6. 测试策略

### 6.1 单元测试

**后端**:
- `RuleParseService` 测试：针对飞书、腾讯会议、Zoom 格式的正则解析
- `AIParseService` 测试：模拟 AI 返回结果
- `InterviewScheduleService` 测试：CRUD 操作

**前端**:
- 文本解析组件：输入各种格式的文本，验证解析结果
- 表单验证：必填项、格式校验

### 6.2 集成测试

- 端到端流程：粘贴文本 → 解析 → 确认 → 保存 → 在日历中显示
- 拖拽流程：拖拽事件 → 确认 → API 调用 → 数据更新

### 6.3 手动测试场景

1. **文本解析**:
   - 飞书邀约文本
   - 腾讯会议邀约文本
   - Zoom 邀约文本
   - 无效文本（验证失败处理）

2. **日历操作**:
   - 周视图/月视图切换
   - 日期导航
   - 拖拽调整时间
   - 点击事件查看详情

3. **状态管理**:
   - 等待面试时间过期，验证自动标记为已完成
   - 手动修改状态

---

## 7. 部署注意事项

### 7.1 数据库迁移

首次部署需要创建 `interview_schedule` 表，建议：
- 使用 JPA `ddl-auto: update` 自动创建
- 生产环境使用 Flyway 或 Liquibase 管理迁移脚本

### 7.2 定时任务配置

确保 Spring Boot 应用启用了定时任务：
```java
@EnableScheduling
@SpringBootApplication
public class App {
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
```

### 7.3 前端构建

安装新依赖后需要重新构建：
```bash
cd frontend
pnpm install
pnpm build
```

---

## 8. 后续优化方向

### 8.1 短期优化
- 支持更多会议格式（Google Meet、Microsoft Teams）
- 导出日历（.ics 文件）
- 批量导入面试邀约

### 8.2 长期优化
- 面试准备资料关联（上传文档、笔记）
- 面试后复盘记录
- 数据统计（面试数量、通过率等）
