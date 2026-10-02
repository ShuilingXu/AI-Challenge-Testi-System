# AI 添加教学知识库

在 `/admin/knowledge` 新建或选择知识库，点击条目区的 **AI 添加**，上传实际使用的教学参考材料、教学大纲或课件。点击 **AI 生成知识点** 后，系统提取文件文字并调用大模型，生成知识点与教学内容。教师可在预览中修改、移除条目，再点击 **一键入库**。新条目立即可用于现有考试出题和评分流程。

支持 PDF、DOCX、PPTX、XLS、XLSX、TXT、MD、CSV。文本文件使用 UTF-8；旧版 DOC/PPT 请先转换为 DOCX/PPTX。仅处理可提取文字，扫描件、图片及课件中的图片不执行 OCR。

单次最多 5 份材料，每份不超过 10MB，PDF 不超过 200 页，提取后的总文字不超过 60000 字。系统按 12000 字分块处理全部文字，并在每个分块标注材料来源；相同名称的知识点合并。单次最多生成 100 条知识点，每条知识点最多 255 字、内容最多 5000 字。超过限制会明确报错，不静默截断。生成可能需要数分钟。

使用系统设置中的 `SCHOOL_LLM_BASE_URL`、`SCHOOL_LLM_API_KEY` 和 `SCHOOL_LLM_MODEL`，支持 OpenAI 兼容的 `/chat/completions` 接口及配置热更新。提取的教学文字会发送至已配置的模型服务；上传文件不另行保存。

教师权限及 CSRF 校验沿用现有知识库接口。生成预览不写数据库；入库以事务追加全部条目，并沿用现有知识点内容校验和审计日志。失败时整批回滚，保留预览供修改。生成与保存固定目标知识库，不随页面选择切换。已有条目不修改。

## API

- `POST /api/interview/hr/knowledge-items/ai/generate`：multipart 参数 `knowledgeBaseId` 和多个 `files`；返回 `data: [{knowledgePoint, knowledgeContent}]`。
- `POST /api/interview/hr/knowledge-items/ai/save`：JSON `{knowledgeBaseId, items: [{knowledgePoint, knowledgeContent}]}`；返回 `data: {imported}`。

## 验证

```powershell
cd backend
mvn '-Dtest=TeachingMaterialReaderTest,KnowledgeAiServiceTest,KnowledgeAiControllerSecurityTest' test
cd ../frontend
npm run build
```
