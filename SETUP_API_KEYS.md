# 🔑 API 密钥配置指南

语音面试功能需要配置以下 API 密钥才能正常工作。

## 📋 必需的 API 服务

### 1. 阿里云百炼 AI (DashScope)

**用途**: 提供 LLM 对话能力，生成面试问题、评估回答

**获取步骤**:
1. 访问 [阿里云百炼平台](https://dashscope.aliyun.com/)
2. 登录/注册阿里云账号
3. 开通 DashScope 服务（有免费额度）
4. 创建 API Key
5. 复制 API Key

**配置变量**:
```bash
AI_BAILIAN_API_KEY=sk-xxxxxxxxxxxxxxxxxxxxxxxx
```

**费用**: 
- 新用户有免费额度
- 按调用次数计费（qwen-plus 模型约 ¥0.008/千 tokens）

---

### 2. 阿里云语音服务 (NLS)

**用途**: 
- STT (语音转文字): 将用户语音转换为文本
- TTS (文字转语音): 将 AI 回答转换为语音

**获取步骤**:
1. 访问 [阿里云语音服务](https://www.aliyun.com/product/nls)
2. 开通以下服务：
   - **实时语音识别** (STT)
   - **语音合成** (TTS)
3. 创建项目并获取 App Keys
4. 创建 AccessKey（建议使用 RAM 子账号）

**配置变量**:
```bash
ALIYUN_ACCESS_KEY=your_access_key_id
ALIYUN_STT_APP_KEY=your_stt_app_key
ALIYUN_TTS_APP_KEY=your_tts_app_key
```

**费用**:
- 实时语音识别: ¥2.4/小时
- 语音合成: ¥2/百万字符
- 新用户有免费额度

---

## ⚙️ 配置步骤

### 方式 1: 使用 .env 文件（推荐）

1. 复制示例配置文件：
```bash
cp .env.example .env
```

2. 编辑 `.env` 文件，填入您的实际密钥：
```bash
# 使用您自己的密钥替换以下占位符
AI_BAILIAN_API_KEY=sk-your-actual-key-here
ALIYUN_ACCESS_KEY=your-actual-access-key
ALIYUN_STT_APP_KEY=your-actual-stt-key
ALIYUN_TTS_APP_KEY=your-actual-tts-key
```

3. 启动应用时会自动读取 `.env` 文件

### 方式 2: 使用环境变量

```bash
# Linux/Mac
export AI_BAILIAN_API_KEY=sk-your-key
export ALIYUN_ACCESS_KEY=your-key
export ALIYUN_STT_APP_KEY=your-key
export ALIYUN_TTS_APP_KEY=your-key

# Windows PowerShell
$env:AI_BAILIAN_API_KEY="sk-your-key"
$env:ALIYUN_ACCESS_KEY="your-key"
$env:ALIYUN_STT_APP_KEY="your-key"
$env:ALIYUN_TTS_APP_KEY="your-key"
```

### 方式 3: 在 IDE 中配置

**IDEA**:
1. Run → Edit Configurations
2. 选择 Spring Boot 配置
3. Environment variables 中添加上述变量

**VS Code**:
1. 创建 `.vscode/launch.json`
2. 添加 env 配置

---

## 🔍 验证配置

启动应用后，检查日志：

```
✅ 成功日志示例:
Aliyun STT service initialized
Aliyun TTS service initialized with voice: xiaoyun
DashScope LLM service initialized

❌ 失败日志示例:
Failed to initialize Aliyun STT: app-key is empty
```

---

## 💡 成本优化建议

1. **使用免费额度**: 新用户都有免费试用额度
2. **限制并发**: 配置 `rate-limit` 参数控制并发数
3. **使用缓存**: TTS 可以缓存常用回复的音频
4. **选择合适模型**: 
   - 开发测试用 `qwen-turbo`（更便宜）
   - 生产环境用 `qwen-plus`（效果更好）

---

## 🆘 常见问题

**Q: 必须使用阿里云吗？**

A: 目前 LLM 支持多家提供商（DashScope/MiniMax/OpenAI/DeepSeek），但语音服务只支持阿里云。如需其他语音服务，需要自行扩展。

**Q: 如何降低成本？**

A: 1) 使用 `qwen-turbo` 模型；2) 限制面试时长；3) 添加用户配额限制

**Q: API 密钥会泄露吗？**

A: `.env` 文件已加入 `.gitignore`，不会提交到 Git。请妥善保管您的密钥。

**Q: 测试时需要付费吗？**

A: 阿里云新用户有免费额度，足够测试使用。正式上线后再考虑付费。

---

## 📞 获取帮助

- 阿里云文档: https://help.aliyun.com/
- DashScope 文档: https://help.aliyun.com/zh/dashscope/
- 语音服务文档: https://help.aliyun.com/zh/nls/
