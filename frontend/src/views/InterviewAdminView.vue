<template>
  <div class="page-shell">
    <section class="page-card">
      <div class="topline">
        <div>
          <p class="page-eyebrow">Interview HR</p>
          <h1 class="page-title">面试系统 HR 入口</h1>
        </div>
        <RouterLink class="link-chip" to="/admin">返回管理后台</RouterLink>
      </div>

      <div class="sub-tabs">
        <RouterLink class="link-chip" :class="{ active: activeTab === 'kb' }" to="/interview/hr/knowledge-bases">知识库</RouterLink>
        <RouterLink class="link-chip" :class="{ active: activeTab === 'weights' }" to="/interview/hr/weights">岗位权重</RouterLink>
        <RouterLink class="link-chip" :class="{ active: activeTab === 'template' }" to="/interview/hr/templates">流程模板</RouterLink>
        <RouterLink v-if="isItAdmin" class="link-chip" :class="{ active: activeTab === 'system' }" to="/interview/hr/system">系统配置</RouterLink>
        <RouterLink class="link-chip" :class="{ active: activeTab === 'process' }" to="/interview/hr/processes">面试流程</RouterLink>
      </div>

      <section v-if="activeTab === 'kb'" class="surface">
        <h3>知识库与知识点</h3>
        <div class="page-grid">
          <el-form :model="kbForm" label-position="top" class="surface inner-surface">
            <el-form-item label="知识库名称"><el-input v-model="kbForm.knowledgeBaseName" /></el-form-item>
            <el-form-item label="技术方向"><el-input v-model="kbForm.techCategory" /></el-form-item>
            <el-form-item label="岗位方向"><el-input v-model="kbForm.jobCategory" /></el-form-item>
            <el-button type="primary" @click="saveKnowledgeBase">保存知识库</el-button>
          </el-form>
          <el-form :model="itemForm" label-position="top" class="surface inner-surface">
            <el-form-item label="所属知识库"><el-select v-model="itemForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
            <el-form-item label="知识点"><el-input v-model="itemForm.knowledgePoint" /></el-form-item>
            <el-form-item label="知识内容"><el-input v-model="itemForm.knowledgeContent" type="textarea" :rows="4" /></el-form-item>
            <div class="action-row"><el-button type="primary" @click="saveKnowledgeItem">{{ itemForm.id ? '保存修改' : '保存知识点' }}</el-button><el-button @click="resetKnowledgeItemForm">清空新增</el-button></div>
          </el-form>
          <div class="surface inner-surface csv-import-box">
            <h3>CSV批量添加知识点</h3>
            <el-form label-position="top" class="form-grid">
              <el-form-item label="目标知识库"><el-select v-model="itemForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
              <el-form-item label="CSV格式"><el-input model-value="knowledgePoint,knowledgeContent,status" disabled /></el-form-item>
            </el-form>
            <p class="serial-line">支持UTF-8或GBK CSV。第一列知识点，第二列知识内容，第三列状态可选，1启用、0停用。</p>
            <div class="action-row">
              <el-upload :auto-upload="false" :show-file-list="false" accept=".csv,text/csv" :on-change="importKnowledgeItemsCsv">
                <el-button type="primary">上传CSV批量添加</el-button>
              </el-upload>
              <a class="link-chip" href="/knowledge-items-template.csv" download>下载CSV模板</a>
            </div>
          </div>
        </div>
        <el-table :data="knowledgeBases" stripe class="data-table" @row-click="openKnowledgeBase">
          <el-table-column prop="knowledgeBaseName" label="知识库" />
          <el-table-column prop="techCategory" label="技术方向" />
          <el-table-column prop="jobCategory" label="岗位方向" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteKnowledgeBase(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
        <el-table :data="knowledgeItems" stripe class="data-table" @row-click="editKnowledgeItem">
          <el-table-column prop="knowledgePoint" label="知识点" />
          <el-table-column prop="knowledgeContent" label="知识内容" min-width="280" />
          <el-table-column prop="status" label="状态" width="90"><template #default="scope">{{ scope.row.status === 0 ? '停用' : '启用' }}</template></el-table-column>
          <el-table-column label="操作" width="160"><template #default="scope"><el-button text @click.stop="editKnowledgeItem(scope.row)">编辑</el-button><el-button text type="danger" @click.stop="deleteKnowledgeItem(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'weights'" class="surface">
        <h3>岗位知识权重</h3>
        <el-form :model="weightForm" label-position="top" class="form-grid">
          <el-form-item label="招聘岗位"><el-select v-model="weightForm.jobId"><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
          <el-form-item label="知识库"><el-select v-model="weightForm.knowledgeBaseId"><el-option v-for="item in knowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="权重"><el-input-number v-model="weightForm.weight" :min="1" /></el-form-item>
        </el-form>
        <el-button type="primary" @click="saveWeight">保存权重</el-button>
        <el-table :data="weights" stripe class="data-table" @row-click="openWeight">
          <el-table-column prop="jobId" label="岗位ID" />
          <el-table-column prop="knowledgeBaseId" label="知识库ID" />
          <el-table-column prop="weight" label="权重" />
          <el-table-column label="操作" width="100"><template #default="scope"><el-button text type="danger" @click.stop="deleteWeight(scope.row.id)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'system'" class="surface">
        <div class="config-heading"><div><h3>面试系统配置</h3><p class="serial-line">运行参数保存到服务器 .env，保存后部分参数需要重启服务生效。</p></div><span class="env-badge">.env</span></div>
        <div class="config-tabs" role="tablist"><button v-for="tab in configTabs" :key="tab.key" :class="{ active: configTab === tab.key }" @click="configTab = tab.key">{{ tab.label }}</button></div>

        <template v-if="configTab !== 'models'">
          <div class="config-panel-head"><div><h3>{{ currentConfigGroup.title }}</h3><p>{{ currentConfigGroup.description }}</p></div></div>
          <el-form label-position="top" class="config-form-grid">
            <el-form-item v-for="field in currentConfigFields" :key="field.key" :label="field.label">
              <el-select v-if="field.options" v-model="systemConfig[field.key]" :placeholder="field.placeholder || '请选择'">
                <el-option v-for="option in field.options" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
              <el-input v-else v-model="systemConfig[field.key]" :type="field.secret ? 'password' : 'text'" :show-password="field.secret" :placeholder="field.placeholder || ''" />
            </el-form-item>
          </el-form>
          <div class="action-row"><el-button type="primary" :loading="savingSystemConfig" @click="saveSystemConfig">保存当前参数</el-button></div>
        </template>

        <template v-else>
          <div class="model-tabs"><button v-for="model in modelTabs" :key="model.role" :class="{ active: modelTab === model.role }" @click="modelTab = model.role">{{ model.label }}</button></div>
          <div class="model-editor">
            <div class="config-panel-head"><div><h3>{{ currentModel.label }}</h3><p>{{ currentModel.description }}</p></div><span class="key-state">密钥：{{ currentModel.keyLabel() }}</span></div>
            <el-form :model="currentModel.form" label-position="top" class="config-form-grid">
              <el-form-item label="配置名称"><el-input v-model="currentModel.form.configName" /></el-form-item>
              <el-form-item label="接口地址"><el-input v-model="currentModel.form.baseUrl" /></el-form-item>
              <el-form-item label="API Key"><el-input v-model="currentModel.form.apiKey" type="password" show-password placeholder="编辑留空则保留原密钥" /></el-form-item>
              <el-form-item label="模型名称"><el-input v-model="currentModel.form.modelName" /></el-form-item>
              <el-form-item :label="currentModel.promptLabel" class="wide"><el-input v-model="currentModel.form[currentModel.promptField]" type="textarea" :rows="6" /></el-form-item>
            </el-form>
            <div class="action-row"><el-button type="primary" @click="saveRoleLlmConfig(currentModel.form, currentModel.role)">保存模型配置</el-button></div>
          </div>
        </template>
      </section>

      <section v-if="activeTab === 'template'" class="surface">
        <div class="template-heading">
          <div><h3>面试流程模板</h3><p class="serial-line">配置可重复使用的 AI 与视频面试顺序；候选人发起后会生成独立快照。</p></div>
          <el-button v-if="isTemplateManager" @click="resetTemplateForm">新建模板</el-button>
        </div>
        <div v-if="isTemplateManager" class="template-editor">
          <el-form :model="templateForm" label-position="top" class="form-grid">
            <el-form-item label="模板名称"><el-input v-model="templateForm.templateName" maxlength="128" show-word-limit placeholder="例如：研发岗位三轮面试" /></el-form-item>
            <el-form-item label="状态"><el-select v-model="templateForm.status"><el-option label="启用" :value="1" /><el-option label="停用" :value="0" /></el-select></el-form-item>
            <el-form-item label="模板说明" class="wide"><el-input v-model="templateForm.description" type="textarea" :rows="2" maxlength="1000" show-word-limit placeholder="说明该流程的适用岗位或使用场景" /></el-form-item>
          </el-form>
          <div class="stage-list-head"><div><h4>面试阶段</h4><span>按顺序执行，可重复添加 AI 或视频面试。</span></div><el-button type="primary" @click="addTemplateStage">添加阶段</el-button></div>
          <div v-if="templateForm.stages.length" class="template-stage-list">
            <div v-for="(stage, index) in templateForm.stages" :key="stage.key" class="template-stage-row">
              <span class="stage-order">{{ index + 1 }}</span>
              <el-input v-model="stage.stageName" maxlength="128" placeholder="自定义阶段名称" />
              <el-radio-group v-model="stage.stageType" class="stage-type-switch"><el-radio-button value="AI">AI 面试</el-radio-button><el-radio-button value="VIDEO">视频面试</el-radio-button></el-radio-group>
              <el-select v-if="stage.stageType === 'AI'" v-model="stage.knowledgeBaseId" placeholder="选择题库"><el-option v-for="item in enabledKnowledgeBases" :key="item.id" :label="item.knowledgeBaseName" :value="item.id" /></el-select>
              <span v-else class="human-stage-note">由 HR 发起并完成视频面试</span>
              <div class="stage-row-actions">
                <el-button text :disabled="index === 0" @click="moveTemplateStage(index, -1)">上移</el-button>
                <el-button text :disabled="index === templateForm.stages.length - 1" @click="moveTemplateStage(index, 1)">下移</el-button>
                <el-button text type="danger" @click="removeTemplateStage(index)">删除</el-button>
              </div>
            </div>
          </div>
          <div v-else class="empty-box">请添加至少一个 AI 面试或视频面试阶段。</div>
          <div class="action-row"><el-button type="primary" :loading="savingTemplate" @click="saveProcessTemplate">保存模板</el-button><el-button @click="resetTemplateForm">清空</el-button></div>
        </div>
        <el-table :data="processTemplates" stripe class="data-table" @row-click="handleTemplateRowClick">
          <el-table-column prop="templateName" label="模板名称" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="220" />
          <el-table-column label="流程阶段" min-width="260"><template #default="scope"><span class="template-stage-summary">{{ templateStageSummary(scope.row) }}</span></template></el-table-column>
          <el-table-column label="状态" width="90"><template #default="scope"><el-tag :type="scope.row.status === 1 ? 'success' : 'info'">{{ scope.row.status === 1 ? '启用' : '停用' }}</el-tag></template></el-table-column>
          <el-table-column v-if="isTemplateManager" label="操作" width="160"><template #default="scope"><el-button text @click.stop="editProcessTemplate(scope.row)">编辑</el-button><el-button text type="danger" @click.stop="deleteProcessTemplate(scope.row)">删除</el-button></template></el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'process' && !isProcessDetail" class="surface">
        <h3>候选人面试流程</h3>
        <el-form :model="processSearch" label-position="top" class="form-grid">
          <el-form-item label="搜索候选人"><el-input v-model="processSearch.keyword" placeholder="候选人ID / 姓名 / 手机 / 邮箱 / 专业 / 学校" /></el-form-item>
        </el-form>
        <el-form :model="processForm" label-position="top" class="form-grid">
          <el-form-item label="候选人投递记录"><el-select v-model="processForm.recruitmentCandidateId" filterable clearable @change="syncIntervieweeByCandidate"><el-option v-for="item in filteredProcessCandidates" :key="item.id" :label="`ID ${item.id} / ${item.fullName} / ${item.jobTitle || '未绑定岗位'}`" :value="item.id" /></el-select></el-form-item>
          <el-form-item label="投递岗位"><el-select v-model="processForm.jobId" disabled><el-option v-for="job in jobs" :key="job.id" :label="job.jobTitle" :value="job.id" /></el-select></el-form-item>
          <el-form-item label="候选人唯一ID"><el-input :model-value="processCandidatePreview?.id || '-'" disabled /></el-form-item>
          <el-form-item label="流程模板"><el-select v-model="processForm.templateId" clearable placeholder="选择模板（不选则沿用旧流程）"><el-option v-for="item in enabledProcessTemplates" :key="item.id" :label="item.templateName" :value="item.id"><span>{{ item.templateName }}</span><small class="template-option-detail">{{ templateStageSummary(item) }}</small></el-option></el-select></el-form-item>
          <el-form-item label="AI通过阈值"><el-input-number v-model="processForm.aiThresholdScore" :min="0" :max="100" /></el-form-item>
          <el-form-item label="低分追问阈值"><el-input-number v-model="processForm.aiFollowUpThreshold" :min="0" :max="100" /></el-form-item>
          <el-form-item label="AI最少问答轮数"><el-input-number v-model="processForm.aiMinQuestionRounds" :min="1" /></el-form-item>
          <el-form-item label="AI最多问答轮数"><el-input-number v-model="processForm.aiMaxQuestionRounds" :min="1" /></el-form-item>
          <el-form-item label="切屏终止阈值"><el-input-number v-model="processForm.antiCheatSwitchLimit" :min="1" /></el-form-item>
          <el-form-item label="AI输出模式"><el-select v-model="processForm.aiOutputMode"><el-option label="普通输出" value="NORMAL" /><el-option label="流式输出" value="STREAM" /></el-select></el-form-item>
        </el-form>
        <div v-if="processCandidatePreview" class="candidate-preview">
          <h4>候选人投递预览</h4>
          <div class="preview-grid">
            <div><span>候选人ID</span><strong>{{ processCandidatePreview.id }}</strong></div>
            <div><span>候选人姓名</span><strong>{{ processCandidatePreview.fullName }}</strong></div>
            <div><span>投递岗位</span><strong>{{ processCandidatePreview.jobTitle || '-' }}</strong></div>
            <div><span>联系电话</span><strong>{{ processCandidatePreview.mobilePhone || '-' }}</strong></div>
            <div><span>邮箱</span><strong>{{ processCandidatePreview.email || '-' }}</strong></div>
            <div><span>专业</span><strong>{{ processCandidatePreview.major || '-' }}</strong></div>
            <div><span>毕业院校</span><strong>{{ processCandidatePreview.graduationSchool || '-' }}</strong></div>
            <div><span>状态</span><strong>{{ processCandidatePreview.interviewStageStatus || processCandidatePreview.applicationStatus || '-' }}</strong></div>
          </div>
        </div>
        <div class="link-row"><el-button type="primary" :loading="startingProcess" @click="startProcess">发起面试流程</el-button></div>
        <el-table :data="processes" stripe class="data-table" @row-click="openProcess">
          <el-table-column prop="id" label="流程流水号" width="110" />
          <el-table-column prop="recruitmentCandidateId" label="候选人ID" />
          <el-table-column prop="candidateName" label="候选人姓名" />
          <el-table-column prop="questionTitle" label="投递岗位" />
          <el-table-column prop="templateName" label="流程模板" min-width="140"><template #default="scope">{{ scope.row.templateName || '旧版固定流程' }}</template></el-table-column>
          <el-table-column prop="currentStage" label="当前轮次" />
          <el-table-column prop="processStatusView" label="状态展示" />
          <el-table-column prop="aiAverageScore" label="AI均分" />
          <el-table-column prop="overallStatus" label="总状态" />
          <el-table-column label="操作" min-width="250">
            <template #default="scope">
              <el-button text @click.stop="openProcess(scope.row)">进入面试界面</el-button>
              <el-button v-if="isAiApprovalPending(scope.row)" text type="primary" :loading="isProcessActionLoading(scope.row.id, 'approve-ai-1')" :disabled="processActionLoading" @click.stop="approveAi(scope.row, 1)">AI审批通过</el-button>
              <el-button v-if="isAiApprovalPending(scope.row)" text type="danger" :loading="isProcessActionLoading(scope.row.id, 'approve-ai-0')" :disabled="processActionLoading" @click.stop="approveAi(scope.row, 0)">AI审批不通过</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="activeTab === 'process' && isProcessDetail" class="surface">
        <div class="detail-headline">
          <div>
            <p class="page-eyebrow">Interview Process</p>
            <h3>{{ selectedProcess?.candidateName || '候选人' }} 的面试操作台</h3>
          </div>
          <RouterLink class="link-chip" to="/interview/hr/processes">返回流程列表</RouterLink>
        </div>
        <div v-if="selectedProcess" class="process-workbench">
          <section class="workbench-panel candidate-panel">
            <h3>面试者个人信息</h3>
            <div class="candidate-info-grid">
              <div><span>姓名</span><strong>{{ selectedCandidate?.fullName || selectedProcess.candidateName || '-' }}</strong></div>
              <div><span>手机号</span><strong>{{ selectedCandidate?.mobilePhone || '-' }}</strong></div>
              <div><span>邮箱</span><strong>{{ selectedCandidate?.email || '-' }}</strong></div>
              <div><span>岗位</span><strong>{{ selectedCandidate?.jobTitle || selectedProcess.questionTitle || '-' }}</strong></div>
              <div><span>专业</span><strong>{{ selectedCandidate?.major || '-' }}</strong></div>
              <div><span>学历</span><strong>{{ selectedCandidate?.educationLevel || '-' }}</strong></div>
              <div><span>毕业院校</span><strong>{{ selectedCandidate?.graduationSchool || '-' }}</strong></div>
              <div><span>工作年限</span><strong>{{ selectedCandidate?.yearsOfExperience ?? '-' }}</strong></div>
            </div>
            <div class="resume-ai-box">
              <span>简历AI评价</span>
              <strong>{{ selectedCandidate?.resumeLlmScore ?? resumeLlmStatusLabel(selectedCandidate?.resumeLlmStatus) }}</strong>
              <p>{{ selectedCandidate?.resumeLlmComment || '暂无简历AI评价' }}</p>
            </div>
          </section>
          <section v-if="selectedProcess.stages?.length" class="workbench-panel stage-overview-panel">
            <div class="panel-title-row"><h3>流程阶段</h3><span>{{ selectedProcess.templateName || '流程模板' }}</span></div>
            <ol class="process-stage-timeline">
              <li v-for="stage in selectedProcess.stages" :key="stage.processStageId || stage.id" :class="{ active: stage.processStageId === selectedProcess.processStageId, complete: ['PASSED', 'REJECTED'].includes(stage.stageStatus) }">
                <span class="timeline-index">{{ stage.sequenceNo }}</span>
                <div><strong>{{ stage.stageName }}</strong><small>{{ stage.stageType === 'AI' ? `AI 面试${stage.knowledgeBaseName ? ` · ${stage.knowledgeBaseName}` : ''}` : '视频面试 · HR 主持' }}</small></div>
                <el-tag size="small" :type="stageStatusTagType(stage.stageStatus)">{{ stageStatusLabel(stage.stageStatus) }}</el-tag>
              </li>
            </ol>
          </section>
          <section class="workbench-panel video-panel">
            <div class="panel-title-row"><h3>视频面试</h3><span>{{ selectedProcess.sessionStatus || '未开始' }}</span></div>
            <div v-if="selectedProcess.videoJoinLink || selectedProcess.videoSerialNo" class="serial-line">
              <span v-if="selectedProcess.videoSerialNo">视频流水号：{{ selectedProcess.videoSerialNo }}</span>
              <el-button v-if="selectedProcess.videoJoinLink" text class="video-link" @click="copyVideoJoinLink">复制候选人视频链接</el-button>
              <el-button v-if="selectedProcess.recordingPath || selectedProcess.recordingFileName" text class="video-link" @click="openRecording">查看合并录制文件</el-button>
            </div>
            <div class="video-grid">
              <div class="video-box"><span>HR本地视频</span><video ref="hrLocalVideo" autoplay muted playsinline></video></div>
              <div class="video-box"><span>面试者远端视频</span><video ref="hrRemoteVideo" autoplay playsinline></video></div>
            </div>
            <div v-if="hrRecording.pending || hrRecording.uploading || hrRecording.error || hrRecording.limitReached" class="recording-recovery">
              <small>{{ hrRecording.uploading ? 'HR 录像上传中' : (hrRecording.error || hrRecording.notice || `有 ${formatRecordingSize(hrRecording.byteSize)} HR 录像等待上传`) }}</small>
              <el-button v-if="hrRecording.pending" size="small" plain :loading="hrRecording.uploading" @click="retryHrRecordingUpload">重试上传</el-button>
              <el-button v-else-if="hrRecording.error" size="small" plain @click="retryHrRecordingStart">重试录制</el-button>
            </div>
            <div class="video-summary-box">
              <div class="summary-status-row">
                <span>音频转写/会议概要状态：{{ summaryStatusLabel(selectedProcess.summaryStatus) }}</span>
                <el-button
                  v-if="(selectedProcess.recordingPath || selectedProcess.recordingFileName) && selectedProcess.summaryStatus !== 'MISSING_RECORDING'"
                  size="small"
                  :loading="retryingVideoSummary"
                  @click="retryVideoSummary"
                >重新生成</el-button>
              </div>
              <div>
                <strong>会议概要</strong>
                <div v-if="selectedProcess.summaryText" class="summary-document">
                  <template v-for="(line, index) in summaryLines(selectedProcess.summaryText)" :key="index">
                    <h4 v-if="line.kind === 'heading'">{{ line.text }}</h4>
                    <p v-else-if="line.kind === 'list'" class="summary-list-item">{{ line.text }}</p>
                    <p v-else>{{ line.text }}</p>
                  </template>
                </div>
                <p v-else>暂无概要，双侧录制完成后自动生成。</p>
              </div>
              <p class="transcript-text"><strong>转写文本</strong>{{ selectedProcess.transcriptText || '暂无转写文本' }}</p>
            </div>
          </section>
          <section class="workbench-panel ai-question-panel">
            <div class="panel-title-row">
              <h3>AI问答与面试官评价</h3>
              <el-button v-if="selectedProcess.aiRecordingPath || selectedProcess.aiRecordingFileName" text class="video-link" @click="openAiRecording">查看AI问答视频</el-button>
            </div>
            <div v-if="aiRecordGroups.length" class="ai-review-content">
              <div class="ai-stage-review-list">
                <section v-for="group in aiRecordGroups" :key="group.key" class="ai-stage-review">
                  <div class="ai-stage-heading">
                    <div>
                      <span class="ai-stage-kicker">{{ group.sequenceNo ? `第 ${group.sequenceNo} 轮 AI 面试` : 'AI 面试' }}</span>
                      <h4>{{ group.stageName }}</h4>
                    </div>
                    <span class="ai-stage-count">{{ group.items.length }} 题</span>
                  </div>
                  <ol v-if="group.items.length" class="ai-question-list">
                    <li v-for="item in group.items" :key="item.id" class="ai-question-review">
                      <div class="ai-question-meta">
                        <span class="question-number" :class="{ answered: item.answerContent }">Q{{ item.sequenceNo }}</span>
                        <div>
                          <span>知识点</span>
                          <strong>{{ item.knowledgePoint || '未标注' }}</strong>
                        </div>
                        <div class="ai-question-score">
                          <span>评分</span>
                          <strong>{{ item.averageScore ?? '-' }}</strong>
                        </div>
                      </div>
                      <div class="ai-question-copy">
                        <span>AI 提问</span>
                        <p>{{ item.questionContent || '暂无提问内容' }}</p>
                      </div>
                      <div class="ai-response-grid">
                        <div>
                          <span>候选人回答</span>
                          <p>{{ item.answerContent || '暂无回答' }}</p>
                        </div>
                        <div>
                          <span>AI 面试官评价</span>
                          <p class="ai-interviewer-feedback">{{ item.interviewerComment || '暂无评价' }}</p>
                        </div>
                      </div>
                    </li>
                  </ol>
                  <p v-else class="empty-stage-review">本轮尚未生成题目。</p>
                </section>
              </div>
            </div>
            <p v-else class="empty-stage-review">暂无 AI 面试题目记录。</p>
          </section>
          <section class="workbench-panel action-panel">
            <h3>操作区</h3>
            <div class="process-stats">
              <p>流程流水号：{{ selectedProcess.id }}</p>
              <p>当前状态：{{ selectedProcess.processStatusView || '-' }}</p>
              <p>AI均分：{{ selectedProcess.aiAverageScore ?? '-' }}</p>
              <p>低分追问阈值：{{ selectedProcess.aiFollowUpThreshold ?? 70 }}</p>
              <p>AI轮数：{{ selectedProcess.aiMinQuestionRounds || '-' }} - {{ selectedProcess.aiMaxQuestionRounds || '-' }}</p>
              <p>切屏次数：{{ selectedProcess.antiCheatSwitchCount || 0 }} / {{ selectedProcess.antiCheatSwitchLimit || 5 }}</p>
            </div>
            <el-form v-if="canApproveAi || canApproveVideo || canApproveOnsite" label-position="top" class="onboarding-department-form">
              <el-form-item v-if="isFinalApproval(selectedProcess)" label="入职岗位">
                <el-select v-model="onboardingJobId" filterable placeholder="选择入职岗位">
                  <el-option v-for="item in jobs" :key="item.id" :label="`${item.jobCode} · ${item.jobTitle}`" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="入职部门">
                <el-select v-model="onboardingDepartmentId" filterable :disabled="Boolean(selectedProcess.jobDepartmentId)" placeholder="选择入职部门">
                  <el-option v-for="item in departments" :key="item.id" :label="item.departmentName" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item v-if="isFinalApproval(selectedProcess)" label="基本薪资（元/月）">
                <el-input-number v-model="onboardingBaseSalary" :min="0.01" :precision="2" :step="500" />
              </el-form-item>
            </el-form>
            <div class="action-button-grid">
              <el-button v-if="canApproveAi" type="primary" :loading="isProcessActionLoading(selectedProcess.id, 'approve-ai-1')" :disabled="processActionLoading" @click="approveAi(selectedProcess, 1)">AI审批通过</el-button>
              <el-button v-if="canApproveAi" type="danger" plain :loading="isProcessActionLoading(selectedProcess.id, 'approve-ai-0')" :disabled="processActionLoading" @click="approveAi(selectedProcess, 0)">AI审批不通过</el-button>
              <el-button v-if="canStartVideo" :loading="startingHrVideo" :disabled="startingHrVideo" @click="startHrVideoCall">开始视频面</el-button>
              <el-button v-if="canStopVideo" @click="stopHrRecording">结束并上传录制</el-button>
              <el-button v-if="canApproveVideo" :loading="isProcessActionLoading(selectedProcess.id, 'approve-video-1')" :disabled="processActionLoading" @click="approveVideo(1)">{{ selectedProcess.stageName || '视频面试' }}通过</el-button>
              <el-button v-if="canApproveVideo" :loading="isProcessActionLoading(selectedProcess.id, 'approve-video-0')" :disabled="processActionLoading" @click="approveVideo(0)">{{ selectedProcess.stageName || '视频面试' }}不通过</el-button>
              <el-button v-if="canApproveOnsite" :loading="isProcessActionLoading(selectedProcess.id, 'approve-onsite-1')" :disabled="processActionLoading" @click="approveOnsite(1)">线下面通过</el-button>
              <el-button v-if="canApproveOnsite" :loading="isProcessActionLoading(selectedProcess.id, 'approve-onsite-0')" :disabled="processActionLoading" @click="approveOnsite(0)">线下面不通过</el-button>
              <el-button v-if="canTerminate" type="danger" :loading="isProcessActionLoading(selectedProcess.id, 'terminate')" :disabled="processActionLoading" @click="terminateProcess">终止流程</el-button>
            </div>
            <div class="remark-box">
              <span>面试备注</span>
              <el-input v-model="processRemark" type="textarea" :rows="5" maxlength="2000" show-word-limit placeholder="记录面试补充说明、风险点、沟通结论" />
              <el-button type="primary" :loading="savingRemark" @click="saveProcessRemark">保存备注</el-button>
            </div>
          </section>
        </div>
        <div v-else class="empty-box">正在加载候选人面试流程...</div>
      </section>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { authApi, hrApi, interviewApi, recruitmentApi, systemApi } from '../services/api'
import { attachRemoteTrack, buildMediaErrorMessage, createPeerConnection, defaultIceServers, playVideo, requestCameraAndMicrophone } from '../utils/media'
import { appendRecordingChunk, beginRecordingSession, buildRecordingFile, deleteRecordingSession, formatRecordingSize, getRecordingSession, MAX_RECORDING_UPLOAD_BYTES, RECORDING_STOP_THRESHOLD_BYTES, RECORDING_WRITE_HIGH_WATER_BYTES, RECORDING_WRITE_LOW_WATER_BYTES, updateRecordingSession } from '../utils/recordingStore'
import { buildInterviewDecision } from '../utils/interviewDecision'
import { readSessionUser } from '../utils/session'

const sessionUser = ref(readSessionUser())
const route = useRoute()
const router = useRouter()
const isItAdmin = computed(() => sessionUser.value?.roleCode === 'IT_ADMIN')
const isTemplateManager = computed(() => ['IT_ADMIN', 'HR_ADMIN'].includes(sessionUser.value?.roleCode))
const activeTab = computed(() => route.meta.interviewTab || 'process')
const isProcessDetail = computed(() => route.name === 'interview-process-detail')
const knowledgeBases = ref([])
const knowledgeItems = ref([])
const weights = ref([])
const llmConfigs = ref([])
const jobs = ref([])
const departments = ref([])
const recruitmentCandidates = ref([])
const processes = ref([])
const processTemplates = ref([])
const aiRecords = ref([])
const selectedProcess = ref(null)
const selectedCandidate = ref(null)
const videoActive = ref(false)
const processRemark = ref('')
const onboardingDepartmentId = ref(null)
const onboardingJobId = ref(null)
const onboardingBaseSalary = ref(null)
const savingRemark = ref(false)
const startingProcess = ref(false)
const processAction = reactive({ processId: null, type: '' })
const processActionLoading = computed(() => processAction.processId !== null)
const savingSystemConfig = ref(false)
const configTab = ref('notifications')
const modelTab = ref('INTERVIEWER')
const retainOnBlankConfigKeys = new Set([
  'ALIYUN_SMS_ACCESS_KEY_SECRET', 'SMTP_PASSWORD', 'S3_SECRET_ACCESS_KEY',
  'ALIYUN_STT_ACCESS_KEY_SECRET', 'DB_PASSWORD', 'JWT_SECRET', 'INTERVIEW_TURN_SHARED_SECRET',
])

const hrLocalVideo = ref(null)
const hrRemoteVideo = ref(null)
const startingHrVideo = ref(false)
const hrRecording = reactive({ starting: false, uploading: false, pending: false, limitReached: false, byteSize: 0, notice: '', error: '' })
let hrLocalStream = null
let componentDisposed = false
let hrPeer = null
let hrPollTimer = null
let hrRecorder = null
let hrRecorderStopPromise = null
let hrRecordingSessionKey = null
let hrRecordingChunkWrites = Promise.resolve()
let hrRecordingChunkWriteError = null
let hrRecordingPendingWriteBytes = 0
let addedIntervieweeIce = new Set()
let hrRemoteStream = null
let pendingIntervieweeIce = []
let hrRecordingUploadPromise = null
let handledHrRecordingEndSignal = ''
let hrRecordingEndTimer = null
let hrVideoPollInProgress = false
let hrVideoProcessStageId = null
let processDetailLoadGeneration = 0
let adminLoadGeneration = 0
let videoSummaryRetryGeneration = 0
let videoSummaryRetryDelay = null

const kbForm = reactive({ id: null, knowledgeBaseName: '', techCategory: '', jobCategory: '', status: 1 })
const itemForm = reactive({ id: null, knowledgeBaseId: null, knowledgePoint: '', knowledgeContent: '', status: 1 })
const weightForm = reactive({ id: null, jobId: null, knowledgeBaseId: null, weight: 1 })
const interviewerLlmForm = reactive(createLlmForm('INTERVIEWER'))
const scorerLlmForm = reactive(createLlmForm('SCORER'))
const resumeReviewLlmForm = reactive(createLlmForm('RESUME_REVIEW'))
const videoSummaryLlmForm = reactive(createLlmForm('VIDEO_SUMMARY'))
const processForm = reactive({ recruitmentCandidateId: null, intervieweeUserId: '', jobId: null, templateId: null, aiThresholdScore: 70, aiFollowUpThreshold: 70, aiMinQuestionRounds: 5, aiMaxQuestionRounds: 10, antiCheatSwitchLimit: 5, aiOutputMode: 'NORMAL' })
const processSearch = reactive({ keyword: '' })
const templateForm = reactive(createTemplateForm())
const savingTemplate = ref(false)
const retryingVideoSummary = ref(false)
const systemConfig = reactive({
  ALIYUN_SMS_ACCESS_KEY_ID: '',
  ALIYUN_SMS_ACCESS_KEY_SECRET: '',
  ALIYUN_SMS_ENDPOINT: '',
  ALIYUN_SMS_SIGN_NAME: '',
  ALIYUN_SMS_TEMPLATE_CODE: '',
  SMTP_HOST: '',
  SMTP_PORT: '',
  SMTP_USERNAME: '',
  SMTP_PASSWORD: '',
  SMTP_FROM: '',
  SMTP_SSL_ENABLED: '',
  SMTP_STARTTLS_ENABLED: '',
  ALIYUN_STT_ACCESS_KEY_ID: '', ALIYUN_STT_ACCESS_KEY_SECRET: '', ALIYUN_STT_APP_KEY: '', ALIYUN_STT_ENDPOINT: '',
  S3_ENABLED: '', S3_ENDPOINT: '', S3_INTERNAL_ENDPOINT_ENABLED: '', S3_INTERNAL_ENDPOINT: '', S3_REGION: '', S3_BUCKET: '', S3_ACCESS_KEY_ID: '', S3_SECRET_ACCESS_KEY: '', S3_SESSION_TOKEN: '', S3_PREFIX: '', S3_PATH_STYLE_ACCESS: '', S3_ALLOW_HTTP_ENDPOINTS: '', S3_ALLOW_PRIVATE_ENDPOINTS: '',
  DB_TYPE: '', DB_URL: '', DB_USERNAME: '', DB_PASSWORD: '', JWT_SECRET: '',
  INTERVIEW_VIDEO_FFMPEG_PATH: '', INTERVIEW_VIDEO_VIDEO_CODEC: '', INTERVIEW_VIDEO_AUDIO_CODEC: '',
  INTERVIEW_STUN_URLS: '', INTERVIEW_TURN_URLS: '', INTERVIEW_TURN_SHARED_SECRET: '', INTERVIEW_TURN_CREDENTIAL_TTL_SECONDS: '',
  TURN_HOST: '', TURN_EXTERNAL_IP: '', TURN_PRIVATE_IP: '', TURN_REALM: '', TURN_MIN_PORT: '', TURN_MAX_PORT: '',
  RESUME_OCR_ENABLED: '', RESUME_OCR_TESSERACT_PATH: '', RESUME_OCR_LANGUAGE: '', RESUME_OCR_DPI: '', RESUME_OCR_MAX_PAGES: '',
})

const configTabs = [
  { key: 'notifications', label: '通知服务' },
  { key: 'storage', label: '对象存储' },
  { key: 'media', label: '面试媒体' },
  { key: 'database', label: '数据库与安全' },
  { key: 'resume', label: '简历识别' },
  { key: 'models', label: 'AI 模型' },
]
const booleanOptions = [
  { label: '启用', value: 'true' },
  { label: '关闭', value: 'false' },
]
const configGroups = {
  notifications: { title: '通知服务', description: '短信验证码和注册邮件使用的独立服务凭据。', fields: [
    { key: 'ALIYUN_SMS_ACCESS_KEY_ID', label: '阿里云短信 AccessKey ID' }, { key: 'ALIYUN_SMS_ACCESS_KEY_SECRET', label: '阿里云短信 AccessKey Secret', secret: true, placeholder: '留空则不覆盖' }, { key: 'ALIYUN_SMS_ENDPOINT', label: '阿里云短信 Endpoint', placeholder: 'dysmsapi.aliyuncs.com' }, { key: 'ALIYUN_SMS_SIGN_NAME', label: '短信签名' }, { key: 'ALIYUN_SMS_TEMPLATE_CODE', label: '短信模板 Code' }, { key: 'SMTP_HOST', label: 'SMTP 服务器', placeholder: 'smtp.example.com' }, { key: 'SMTP_PORT', label: 'SMTP 端口', placeholder: '587' }, { key: 'SMTP_USERNAME', label: 'SMTP 用户名' }, { key: 'SMTP_PASSWORD', label: 'SMTP 密码', secret: true, placeholder: '留空则不覆盖' }, { key: 'SMTP_FROM', label: '发件人' }, { key: 'SMTP_SSL_ENABLED', label: 'SSL 启用', options: booleanOptions }, { key: 'SMTP_STARTTLS_ENABLED', label: 'STARTTLS 启用', options: booleanOptions },
  ] },
  storage: { title: 'S3 兼容对象存储', description: '外网 Endpoint 供浏览器访问，内网 Endpoint 仅供服务端上传。默认只允许公网 HTTPS；可信 MinIO 或 VPC 地址需显式开启对应兼容开关。归档失败会回退到本地文件。', fields: [
    { key: 'S3_ENABLED', label: '启用归档', options: booleanOptions }, { key: 'S3_ENDPOINT', label: '外网 S3 Endpoint', placeholder: 'https://s3.example.com' }, { key: 'S3_INTERNAL_ENDPOINT_ENABLED', label: '使用内网上传 Endpoint', options: booleanOptions }, { key: 'S3_INTERNAL_ENDPOINT', label: '内网上传 Endpoint', placeholder: 'https://s3.internal.example.com' }, { key: 'S3_ALLOW_HTTP_ENDPOINTS', label: '允许 HTTP Endpoint（不安全）', options: booleanOptions }, { key: 'S3_ALLOW_PRIVATE_ENDPOINTS', label: '允许私网/环回 Endpoint', options: booleanOptions }, { key: 'S3_REGION', label: 'Region', placeholder: 'us-east-1' }, { key: 'S3_BUCKET', label: 'Bucket' }, { key: 'S3_ACCESS_KEY_ID', label: 'Access Key ID' }, { key: 'S3_SECRET_ACCESS_KEY', label: 'Secret Access Key', secret: true, placeholder: '留空则不覆盖' }, { key: 'S3_SESSION_TOKEN', label: 'Session Token（可选）', secret: true, placeholder: '临时凭据时填写' }, { key: 'S3_PREFIX', label: '对象前缀', placeholder: 'autohr' }, { key: 'S3_PATH_STYLE_ACCESS', label: 'Path Style Access', options: booleanOptions },
  ] },
  media: { title: '面试媒体', description: '视频编码、WebRTC 网络和阿里云语音转文字使用的独立参数。', fields: [
    { key: 'INTERVIEW_VIDEO_FFMPEG_PATH', label: 'FFmpeg 路径', placeholder: 'ffmpeg' }, { key: 'INTERVIEW_VIDEO_VIDEO_CODEC', label: '视频编码器' }, { key: 'INTERVIEW_VIDEO_AUDIO_CODEC', label: '音频编码器' }, { key: 'INTERVIEW_STUN_URLS', label: 'STUN 地址' }, { key: 'INTERVIEW_TURN_URLS', label: 'TURN 地址' }, { key: 'INTERVIEW_TURN_SHARED_SECRET', label: 'TURN 共享密钥', secret: true, placeholder: '留空则不覆盖' }, { key: 'INTERVIEW_TURN_CREDENTIAL_TTL_SECONDS', label: 'TURN 临时凭据有效期（秒）', placeholder: '3600' }, { key: 'ALIYUN_STT_ACCESS_KEY_ID', label: '阿里云语音 AccessKey ID' }, { key: 'ALIYUN_STT_ACCESS_KEY_SECRET', label: '阿里云语音 AccessKey Secret', secret: true, placeholder: '留空则不覆盖' }, { key: 'ALIYUN_STT_APP_KEY', label: '阿里云语音 AppKey' }, { key: 'ALIYUN_STT_ENDPOINT', label: '阿里云语音 Endpoint', placeholder: 'wss://nls-gateway-cn-shanghai.aliyuncs.com/ws/v1' }, { key: 'TURN_HOST', label: 'TURN 主机' }, { key: 'TURN_EXTERNAL_IP', label: 'TURN 外部 IP' }, { key: 'TURN_PRIVATE_IP', label: 'TURN 内部 IP' }, { key: 'TURN_REALM', label: 'TURN Realm' }, { key: 'TURN_MIN_PORT', label: 'TURN 最小端口' }, { key: 'TURN_MAX_PORT', label: 'TURN 最大端口' },
  ] },
  database: { title: '数据库与安全', description: '数据库连接和 JWT 签名参数。敏感值以掩码显示。', fields: [
    { key: 'DB_TYPE', label: '数据库类型', placeholder: 'sqlite / mysql / postgresql' }, { key: 'DB_URL', label: '数据库 URL' }, { key: 'DB_USERNAME', label: '数据库用户名' }, { key: 'DB_PASSWORD', label: '数据库密码', secret: true, placeholder: '留空则不覆盖' }, { key: 'JWT_SECRET', label: 'JWT Secret', secret: true, placeholder: '至少 32 位，留空则不覆盖' },
  ] },
  resume: { title: '简历识别', description: '简历 OCR 的开关、语言和处理限制。', fields: [
    { key: 'RESUME_OCR_ENABLED', label: 'OCR 启用', placeholder: 'true / false' }, { key: 'RESUME_OCR_TESSERACT_PATH', label: 'Tesseract 路径' }, { key: 'RESUME_OCR_LANGUAGE', label: '识别语言', placeholder: 'chi_sim+eng' }, { key: 'RESUME_OCR_DPI', label: '识别 DPI' }, { key: 'RESUME_OCR_MAX_PAGES', label: '最大页数' },
  ] },
}
const currentConfigGroup = computed(() => configGroups[configTab.value] || configGroups.notifications)
const currentConfigFields = computed(() => currentConfigGroup.value.fields.filter((field) => (
  field.key !== 'S3_INTERNAL_ENDPOINT' || systemConfig.S3_INTERNAL_ENDPOINT_ENABLED === 'true'
)))
const interviewerKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'INTERVIEWER')?.apiKeyMasked || '未配置')
const scorerKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'SCORER')?.apiKeyMasked || '未配置')
const resumeReviewKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'RESUME_REVIEW')?.apiKeyMasked || '未配置，默认回退评分模型')
const videoSummaryKeyLabel = computed(() => llmConfigs.value.find((item) => item.modelRole === 'VIDEO_SUMMARY')?.apiKeyMasked || '未配置')
const modelTabs = [
  { role: 'INTERVIEWER', label: '面试官', description: '生成面试问题和追问。', keyLabel: () => interviewerKeyLabel.value, form: interviewerLlmForm, promptField: 'promptTemplate', promptLabel: '提问提示词模板' },
  { role: 'SCORER', label: '评分器', description: '根据回答和岗位要求生成评分。', keyLabel: () => scorerKeyLabel.value, form: scorerLlmForm, promptField: 'scoringRulePrompt', promptLabel: '评分提示词' },
  { role: 'RESUME_REVIEW', label: '简历初筛', description: '对候选人简历进行初步匹配。', keyLabel: () => resumeReviewKeyLabel.value, form: resumeReviewLlmForm, promptField: 'scoringRulePrompt', promptLabel: '筛选提示词' },
  { role: 'VIDEO_SUMMARY', label: '会议概要', description: '总结视频面试表现和建议。', keyLabel: () => videoSummaryKeyLabel.value, form: videoSummaryLlmForm, promptField: 'scoringRulePrompt', promptLabel: '概要提示词' },
]
const currentModel = computed(() => modelTabs.find((item) => item.role === modelTab.value) || modelTabs[0])

const canTerminate = computed(() => selectedProcess.value?.overallStatus === 'IN_PROGRESS')
const canApproveAi = computed(() => isAiApprovalPending(selectedProcess.value))
const canStartVideo = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'VIDEO' && selectedProcess.value?.videoJoinLink && !videoActive.value)
const canStopVideo = computed(() => videoActive.value)
const canApproveVideo = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'VIDEO' && ['WAITING_APPROVAL', 'RECORDED'].includes(selectedProcess.value?.sessionStatus))
const canApproveOnsite = computed(() => canTerminate.value && selectedProcess.value?.currentStage === 'ONSITE')
const enabledKnowledgeBases = computed(() => knowledgeBases.value.filter((item) => item.status === 1))
const enabledProcessTemplates = computed(() => processTemplates.value.filter((item) => item.status === 1))
const filteredProcessCandidates = computed(() => {
  const keyword = processSearch.keyword.trim().toLowerCase()
  const candidates = recruitmentCandidates.value.filter((item) => !item.interviewProcessId && item.applicationStatus !== 'REJECTED')
  if (!keyword) return candidates
  return candidates.filter((item) => [item.id, item.fullName, item.mobilePhone, item.email, item.major, item.graduationSchool]
    .some((value) => String(value || '').toLowerCase().includes(keyword)))
})
const processCandidatePreview = computed(() => recruitmentCandidates.value.find((item) => item.id === processForm.recruitmentCandidateId) || null)
const aiRecordGroups = computed(() => {
  const records = [...aiRecords.value].sort(sortAiRecords)
  const aiStages = (selectedProcess.value?.stages || []).filter((stage) => stage.stageType === 'AI')
  if (!aiStages.length) {
    return records.length ? [{ key: 'legacy-ai', stageName: 'AI 面试', stageType: 'AI', sequenceNo: null, items: records }] : []
  }
  const groups = aiStages.map((stage) => {
    const stageId = stage.processStageId || stage.id
    return {
      key: String(stageId),
      stageName: stage.stageName || 'AI 面试',
      stageType: stage.stageType,
      sequenceNo: stage.sequenceNo,
      items: records.filter((record) => String(record.processStageId) === String(stageId)),
    }
  })
  const groupedIds = new Set(groups.flatMap((group) => group.items.map((item) => item.id)))
  const unassigned = records.filter((record) => !groupedIds.has(record.id))
  if (unassigned.length) groups.push({ key: 'unassigned-ai', stageName: '未分配 AI 题目', stageType: 'AI', sequenceNo: null, items: unassigned })
  return groups
})

function fail(error) { ElMessage.error(error.message || '操作失败') }
function summaryLines(markdown) {
  return String(markdown || '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line) => {
    if (/^#{1,6}\s+/.test(line)) return { kind: 'heading', text: line.replace(/^#{1,6}\s+/, '').replace(/\*\*/g, '') }
    if (/^(?:[-*]|\d+\.)\s+/.test(line)) return { kind: 'list', text: line.replace(/^([-*]|\d+\.)\s+/, '').replace(/\*\*/g, '') }
    return { kind: 'text', text: line.replace(/\*\*/g, '') }
  })
}
function resumeLlmStatusLabel(status) { return ({ PENDING: '评分中', COMPLETED: '已完成', FAILED: '评分失败' })[status] || '-' }
function sortAiRecords(left, right) {
  return (Number(left.sequenceNo ?? Number.MAX_SAFE_INTEGER) - Number(right.sequenceNo ?? Number.MAX_SAFE_INTEGER))
    || (Number(left.id ?? Number.MAX_SAFE_INTEGER) - Number(right.id ?? Number.MAX_SAFE_INTEGER))
}
function isAiApprovalPending(process) { return process?.overallStatus === 'IN_PROGRESS' && process?.currentStage === 'AI' && process?.stageStatus === 'WAITING_APPROVAL' }
async function loadAll() {
  const generation = ++adminLoadGeneration
  try {
    const sessionResponse = await authApi.getSession()
    if (generation !== adminLoadGeneration) return
    sessionUser.value = sessionResponse.data
    const knowledgeBasesResponse = await interviewApi.listKnowledgeBases()
    if (generation !== adminLoadGeneration) return
    knowledgeBases.value = knowledgeBasesResponse.data
    if (isItAdmin.value) {
      const [llmConfigsResponse, systemConfigResponse] = await Promise.all([
        interviewApi.listLlmConfigs(),
        systemApi.getConfig(),
      ])
      if (generation !== adminLoadGeneration) return
      llmConfigs.value = llmConfigsResponse.data
      Object.assign(systemConfig, systemConfigResponse.data)
      for (const key of ['S3_ALLOW_HTTP_ENDPOINTS', 'S3_ALLOW_PRIVATE_ENDPOINTS']) {
        if (!['true', 'false'].includes(systemConfig[key])) systemConfig[key] = 'false'
      }
      syncLlmForms()
    } else {
      llmConfigs.value = []
      if (activeTab.value === 'llm') router.replace('/interview/hr/knowledge-bases')
    }
    const [jobsResponse, departmentsResponse, candidatesResponse, templatesResponse, processesResponse] = await Promise.all([
      recruitmentApi.listAllAdminJobs(),
      hrApi.listAllDepartments({ status: 1 }),
      recruitmentApi.listCandidates(),
      interviewApi.listProcessTemplates(),
      interviewApi.listProcesses(),
    ])
    if (generation !== adminLoadGeneration) return
    jobs.value = jobsResponse.data
    departments.value = departmentsResponse.data
    recruitmentCandidates.value = candidatesResponse.data
    processTemplates.value = templatesResponse.data
    processes.value = processesResponse.data
    if (selectedProcess.value) {
      selectedProcess.value = processes.value.find((item) => item.id === selectedProcess.value.id) || selectedProcess.value
      if (isProcessDetail.value) {
        const currentProcess = selectedProcess.value
        const [recordsResponse, candidateResponse] = await Promise.all([
          interviewApi.listAiRecords({ processId: currentProcess.id }),
          currentProcess.recruitmentCandidateId ? recruitmentApi.getCandidate(currentProcess.recruitmentCandidateId) : Promise.resolve(null),
        ])
        if (generation !== adminLoadGeneration || selectedProcess.value?.id !== currentProcess.id) return
        aiRecords.value = recordsResponse.data
        selectedCandidate.value = candidateResponse?.data || null
        processRemark.value = selectedProcess.value.remark || ''
      }
    }
    if (generation !== adminLoadGeneration) return
    await syncRouteState()
  } catch (error) {
    if (generation === adminLoadGeneration) fail(error)
  }
}
async function selectKnowledgeBase(row) { itemForm.knowledgeBaseId = row.id; knowledgeItems.value = (await interviewApi.listKnowledgeItems({ knowledgeBaseId: row.id })).data }
async function openKnowledgeBase(row) { await router.push(`/interview/hr/knowledge-bases/${row.id}`) }
function openWeight(row) { Object.assign(weightForm, row); router.push(`/interview/hr/weights/${row.id}`) }
function openProcess(row) { router.push(`/interview/hr/processes/${row.id}`) }
async function saveKnowledgeBase() { try { await interviewApi.saveKnowledgeBase({ ...kbForm }); ElMessage.success('知识库已保存'); await loadAll() } catch (error) { fail(error) } }
async function confirmDelete(message) { await ElMessageBox.confirm(message, '确认删除', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }) }
async function deleteKnowledgeBase(id) { try { await confirmDelete('删除知识库会同时删除其中的知识点和岗位权重，且无法恢复。'); await interviewApi.deleteKnowledgeBase(id); ElMessage.success('知识库已删除'); await loadAll() } catch (error) { if (error !== 'cancel' && error !== 'close') fail(error) } }
async function saveKnowledgeItem() { try { await interviewApi.saveKnowledgeItem({ ...itemForm }); ElMessage.success('知识点已保存'); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { fail(error) } }
function editKnowledgeItem(row) { Object.assign(itemForm, { id: row.id, knowledgeBaseId: row.knowledgeBaseId, knowledgePoint: row.knowledgePoint, knowledgeContent: row.knowledgeContent, status: row.status ?? 1 }) }
function resetKnowledgeItemForm() { Object.assign(itemForm, { id: null, knowledgeBaseId: itemForm.knowledgeBaseId, knowledgePoint: '', knowledgeContent: '', status: 1 }) }
async function importKnowledgeItemsCsv(uploadFile) { try { if (!itemForm.knowledgeBaseId) { ElMessage.warning('请先选择目标知识库'); return } const response = await interviewApi.importKnowledgeItems(itemForm.knowledgeBaseId, uploadFile.raw); ElMessage.success(`已导入 ${response.data.imported} 条知识点`); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { fail(error) } }
async function deleteKnowledgeItem(id) { try { await confirmDelete('删除后无法恢复该知识点。'); await interviewApi.deleteKnowledgeItem(id); ElMessage.success('知识点已删除'); await selectKnowledgeBase({ id: itemForm.knowledgeBaseId }) } catch (error) { if (error !== 'cancel' && error !== 'close') fail(error) } }
async function saveWeight() { try { await interviewApi.saveJobKnowledgeWeight({ ...weightForm }); ElMessage.success('权重已保存'); weights.value = (await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })).data } catch (error) { fail(error) } }
async function deleteWeight(id) { try { await confirmDelete('删除后无法恢复该岗位知识库权重。'); await interviewApi.deleteJobKnowledgeWeight(id); ElMessage.success('权重已删除'); weights.value = (await interviewApi.listJobKnowledgeWeights({ jobId: weightForm.jobId })).data } catch (error) { if (error !== 'cancel' && error !== 'close') fail(error) } }
async function saveRoleLlmConfig(form, role) { try { await interviewApi.saveLlmConfig({ ...form, modelRole: role }); ElMessage.success('LLM配置已保存'); form.apiKey = ''; await loadAll() } catch (error) { fail(error) } }
function systemConfigPayload() {
  return Object.fromEntries(Object.entries(systemConfig).filter(([key, value]) => !retainOnBlankConfigKeys.has(key) || String(value || '').trim()))
}
async function saveSystemConfig() {
  if (configTab.value === 'storage' && systemConfig.S3_ENABLED === 'true') {
    const requiredFields = [
      ['S3_ENDPOINT', '请填写外网 S3 Endpoint'],
      ['S3_REGION', '请填写 S3 Region'],
      ['S3_BUCKET', '请填写 S3 Bucket'],
      ['S3_ACCESS_KEY_ID', '请填写 S3 Access Key ID'],
    ]
    const missing = requiredFields.find(([key]) => !String(systemConfig[key] || '').trim())
    if (missing) { ElMessage.warning(missing[1]); return }
    if (systemConfig.S3_INTERNAL_ENDPOINT_ENABLED === 'true' && !String(systemConfig.S3_INTERNAL_ENDPOINT || '').trim()) {
      ElMessage.warning('启用内网上传后必须填写内网上传 Endpoint')
      return
    }
  }
  savingSystemConfig.value = true
  try {
    await systemApi.saveConfig(systemConfigPayload())
    ElMessage.success('系统配置已保存')
    Object.assign(systemConfig, (await systemApi.getConfig()).data)
  } catch (error) {
    fail(error)
  } finally {
    savingSystemConfig.value = false
  }
}
function createTemplateForm() { return { id: null, version: null, templateName: '', description: '', status: 1, stages: [] } }
function createTemplateStage() { return { key: `${Date.now()}-${Math.random()}`, stageName: '', stageType: 'AI', knowledgeBaseId: null } }
function resetTemplateForm() { Object.assign(templateForm, createTemplateForm()) }
function addTemplateStage() { templateForm.stages.push(createTemplateStage()) }
function removeTemplateStage(index) { templateForm.stages.splice(index, 1) }
function moveTemplateStage(index, offset) { const target = index + offset; if (target < 0 || target >= templateForm.stages.length) return; const [stage] = templateForm.stages.splice(index, 1); templateForm.stages.splice(target, 0, stage) }
function templateStageSummary(template) { return (template?.stages || []).map((stage) => stage.stageName || (stage.stageType === 'AI' ? 'AI 面试' : '视频面试')).join(' -> ') || '暂无阶段' }
function stageStatusLabel(status) { return ({ PENDING: '待开始', READY: '待开始', IN_PROGRESS: '进行中', UPLOADING: '上传中', WAITING_APPROVAL: '待审批', PASSED: '已通过', REJECTED: '未通过' })[status] || status || '-' }
function stageStatusTagType(status) { return ({ PASSED: 'success', REJECTED: 'danger', WAITING_APPROVAL: 'warning', IN_PROGRESS: 'primary', UPLOADING: 'warning' })[status] || 'info' }
function summaryStatusLabel(status) { return ({ PENDING_MERGE: '等待合并', PENDING: '等待生成', PROCESSING: '生成中', COMPLETED: '已生成', FAILED_MERGE: '合并失败', FAILED: '生成失败', MISSING_RECORDING: '录像缺失（不阻止审批）' })[status] || status || '未生成' }
function handleTemplateRowClick(row) { if (isTemplateManager.value) void editProcessTemplate(row) }
async function editProcessTemplate(row) { try { const template = (await interviewApi.getProcessTemplate(row.id)).data; Object.assign(templateForm, { id: template.id, version: template.version, templateName: template.templateName, description: template.description || '', status: template.status ?? 1, stages: (template.stages || []).map((stage) => ({ key: `${stage.id}-${Date.now()}`, stageName: stage.stageName, stageType: stage.stageType, knowledgeBaseId: stage.knowledgeBaseId || null })) }); } catch (error) { fail(error) } }
async function saveProcessTemplate() { if (!templateForm.templateName.trim()) { ElMessage.warning('请填写模板名称'); return } if (!templateForm.stages.length) { ElMessage.warning('请至少添加一个面试阶段'); return } const invalidAiStage = templateForm.stages.find((stage) => stage.stageType === 'AI' && !stage.knowledgeBaseId); if (invalidAiStage) { ElMessage.warning(`请为“${invalidAiStage.stageName || 'AI 面试'}”选择题库`); return } const invalidName = templateForm.stages.find((stage) => !stage.stageName.trim()); if (invalidName) { ElMessage.warning('请填写每个阶段的展示名称'); return } savingTemplate.value = true; try { const saved = (await interviewApi.saveProcessTemplate({ id: templateForm.id, version: templateForm.version, templateName: templateForm.templateName.trim(), description: templateForm.description.trim(), status: templateForm.status, stages: templateForm.stages.map((stage, index) => ({ stageName: stage.stageName.trim(), stageType: stage.stageType, knowledgeBaseId: stage.stageType === 'AI' ? stage.knowledgeBaseId : null, sequenceNo: index + 1 })) })).data; ElMessage.success('流程模板已保存'); await loadAll(); await editProcessTemplate(saved) } catch (error) { fail(error) } finally { savingTemplate.value = false } }
async function deleteProcessTemplate(template) { try { await confirmDelete('删除后无法恢复该流程模板，已开始的面试流程不受影响。'); await interviewApi.deleteProcessTemplate(template.id, template.version); ElMessage.success('流程模板已删除'); if (templateForm.id === template.id) resetTemplateForm(); await loadAll() } catch (error) { if (error !== 'cancel' && error !== 'close') fail(error) } }
function editLlmConfig(row) { Object.assign(llmFormByRole(row.modelRole), { ...row, apiKey: '' }) }
function createLlmForm(role) { return { id: null, configName: role === 'SCORER' ? '评分模型' : role === 'RESUME_REVIEW' ? '简历初筛模型' : role === 'VIDEO_SUMMARY' ? '视频会议概要模型' : '面试官模型', modelRole: role, baseUrl: '', apiKey: '', modelName: '', promptTemplate: '', scoringRulePrompt: '', status: 1 } }
function llmFormByRole(role) { return role === 'SCORER' ? scorerLlmForm : role === 'RESUME_REVIEW' ? resumeReviewLlmForm : role === 'VIDEO_SUMMARY' ? videoSummaryLlmForm : interviewerLlmForm }
function syncLlmForms() {
  const interviewer = llmConfigs.value.find((item) => item.modelRole === 'INTERVIEWER')
  const scorer = llmConfigs.value.find((item) => item.modelRole === 'SCORER')
  const resumeReview = llmConfigs.value.find((item) => item.modelRole === 'RESUME_REVIEW')
  const videoSummary = llmConfigs.value.find((item) => item.modelRole === 'VIDEO_SUMMARY')
  Object.assign(interviewerLlmForm, interviewer ? { ...interviewer, apiKey: '' } : createLlmForm('INTERVIEWER'))
  Object.assign(scorerLlmForm, scorer ? { ...scorer, apiKey: '' } : createLlmForm('SCORER'))
  Object.assign(resumeReviewLlmForm, resumeReview ? { ...resumeReview, apiKey: '' } : createLlmForm('RESUME_REVIEW'))
  Object.assign(videoSummaryLlmForm, videoSummary ? { ...videoSummary, apiKey: '' } : createLlmForm('VIDEO_SUMMARY'))
}
async function syncIntervieweeByCandidate(candidateId) { const candidate = recruitmentCandidates.value.find((item) => item.id === candidateId); processForm.intervieweeUserId = candidate?.intervieweeUserId ? String(candidate.intervieweeUserId) : ''; processForm.jobId = candidate?.jobId || null }
async function startProcess() {
  if (startingProcess.value) return
  if (!processForm.recruitmentCandidateId) { ElMessage.warning('请先选择候选人投递记录'); return }
  if (!processForm.intervieweeUserId) { ElMessage.warning('未匹配到候选人账号'); return }
  if (!processForm.jobId) { ElMessage.warning('投递记录未绑定岗位'); return }
  if (processForm.aiFollowUpThreshold > processForm.aiThresholdScore) { ElMessage.warning('低分追问阈值不能高于 AI 通过阈值'); return }
  if (processForm.aiMaxQuestionRounds < processForm.aiMinQuestionRounds) { ElMessage.warning('AI最多问答轮数不能小于最少问答轮数'); return }
  startingProcess.value = true
  try {
    const response = await interviewApi.startProcess({ ...processForm, intervieweeUserId: Number(processForm.intervieweeUserId) })
    selectedProcess.value = response.data
    ElMessage.success('面试流程已发起')
    await loadAll()
  } catch (error) {
    fail(error)
  } finally {
    startingProcess.value = false
  }
}
async function loadProcessDetail(row) {
  const generation = ++processDetailLoadGeneration
  selectedProcess.value = row
  processRemark.value = row?.remark || ''
  const [recordsResponse, candidateResponse] = await Promise.all([
    interviewApi.listAiRecords({ processId: row.id }),
    row.recruitmentCandidateId ? recruitmentApi.getCandidate(row.recruitmentCandidateId) : Promise.resolve(null),
  ])
  if (generation !== processDetailLoadGeneration || selectedProcess.value?.id !== row.id) return
  aiRecords.value = recordsResponse.data
  selectedCandidate.value = candidateResponse?.data || null
}
function isFinalApproval(process) {
  if (!process?.templateId) return process?.currentStage === 'ONSITE'
  const stages = [...(process.stages || [])].sort((left, right) => Number(left.sequenceNo) - Number(right.sequenceNo))
  return stages.length > 0 && String(stages.at(-1).processStageId || stages.at(-1).id) === String(process.processStageId)
}
async function decisionPayload(process, approved) {
  const decision = buildInterviewDecision({
    process,
    approved,
    finalApproval: isFinalApproval(process),
    detailProcessId: isProcessDetail.value ? selectedProcess.value?.id : null,
    draft: {
      departmentId: onboardingDepartmentId.value,
      jobId: onboardingJobId.value,
      baseSalary: onboardingBaseSalary.value,
    },
  })
  if (decision.missing === 'department') {
    await promptForFinalApprovalDetails(process, '最终审批通过前请选择入职部门')
    return null
  }
  if (decision.missing === 'jobAndSalary') {
    await promptForFinalApprovalDetails(process, '最终审批通过前请选择入职岗位并填写正数基本薪资')
    return null
  }
  return decision.payload
}
async function promptForFinalApprovalDetails(process, message) {
  ElMessage.warning(message)
  if (!isProcessDetail.value) await router.push(`/interview/hr/processes/${process.id}`)
}
function isProcessActionLoading(processId, type) {
  return processAction.processId === processId && processAction.type === type
}
async function runProcessAction(processId, type, action) {
  if (processActionLoading.value) return
  processAction.processId = processId
  processAction.type = type
  try {
    await action()
  } catch (error) {
    fail(error)
  } finally {
    processAction.processId = null
    processAction.type = ''
  }
}
async function approveAi(process, approved) {
  await runProcessAction(process.id, `approve-ai-${approved}`, async () => {
    const payload = await decisionPayload(process, approved)
    if (!payload) return
    await interviewApi.approveAi(process.id, payload)
    ElMessage.success(`${process.stageName || 'AI面试'}审批完成`)
    await loadAll()
  })
}
async function approveVideo(approved) {
  const process = selectedProcess.value
  if (!process) return
  await runProcessAction(process.id, `approve-video-${approved}`, async () => {
    const payload = await decisionPayload(process, approved)
    if (!payload) return
    await interviewApi.approveVideo(process.id, payload)
    ElMessage.success(`${process.stageName || '视频面试'}审批完成`)
    await loadAll()
  })
}
async function approveOnsite(approved) {
  const process = selectedProcess.value
  if (!process) return
  await runProcessAction(process.id, `approve-onsite-${approved}`, async () => {
    const payload = await decisionPayload(process, approved)
    if (!payload) return
    await interviewApi.approveOnsite(process.id, payload)
    ElMessage.success('线下面审批完成')
    await loadAll()
  })
}
async function terminateProcess() {
  const process = selectedProcess.value
  if (!process) return
  await runProcessAction(process.id, 'terminate', async () => {
    await interviewApi.terminateProcess(process.id, { approved: 0 })
    ElMessage.success('流程已终止')
    await loadAll()
  })
}
async function saveProcessRemark() {
  if (!selectedProcess.value) return
  savingRemark.value = true
  try {
    selectedProcess.value = (await interviewApi.updateProcessRemark(selectedProcess.value.id, { comment: processRemark.value })).data
    processRemark.value = selectedProcess.value.remark || ''
    ElMessage.success('备注已保存')
    await loadAll()
  } catch (error) { fail(error) } finally { savingRemark.value = false }
}

function cancelVideoSummaryRetry() {
  videoSummaryRetryGeneration += 1
  if (videoSummaryRetryDelay) {
    clearTimeout(videoSummaryRetryDelay.timer)
    const resolve = videoSummaryRetryDelay.resolve
    videoSummaryRetryDelay = null
    resolve(false)
  }
  retryingVideoSummary.value = false
}

function waitForVideoSummaryRetry(generation, delay) {
  if (componentDisposed || generation !== videoSummaryRetryGeneration) return Promise.resolve(false)
  return new Promise((resolve) => {
    const timer = setTimeout(() => {
      if (videoSummaryRetryDelay?.timer === timer) videoSummaryRetryDelay = null
      resolve(!componentDisposed && generation === videoSummaryRetryGeneration)
    }, delay)
    videoSummaryRetryDelay = { timer, resolve }
  })
}

async function retryVideoSummary() {
  if (!selectedProcess.value || retryingVideoSummary.value || componentDisposed) return
  const processId = selectedProcess.value.id
  const generation = ++videoSummaryRetryGeneration
  retryingVideoSummary.value = true
  try {
    const retryResponse = await interviewApi.retryVideoSummary(processId)
    if (componentDisposed || generation !== videoSummaryRetryGeneration || selectedProcess.value?.id !== processId) return
    selectedProcess.value = retryResponse.data
    ElMessage.success(selectedProcess.value.summaryStatus === 'PENDING_MERGE' ? '已开始重新合并录像并生成概要' : '已开始重新生成转写与会议概要')
    for (let attempt = 0; attempt < 90; attempt += 1) {
      if (!await waitForVideoSummaryRetry(generation, 2000)) return
      const latest = (await interviewApi.getProcess(processId)).data
      if (componentDisposed || generation !== videoSummaryRetryGeneration || selectedProcess.value?.id !== processId) return
      if (latest) selectedProcess.value = latest
      if (!['PENDING_MERGE', 'PENDING', 'PROCESSING'].includes(latest?.summaryStatus)) break
    }
    if (componentDisposed || generation !== videoSummaryRetryGeneration || selectedProcess.value?.id !== processId) return
    if (selectedProcess.value.summaryStatus === 'COMPLETED') ElMessage.success('转写与会议概要已生成')
    else if (selectedProcess.value.summaryStatus?.startsWith('FAILED')) ElMessage.error(selectedProcess.value.summaryStatus)
  } catch (error) {
    if (!componentDisposed && generation === videoSummaryRetryGeneration) fail(error)
  } finally {
    if (generation === videoSummaryRetryGeneration) retryingVideoSummary.value = false
  }
}

async function copyVideoJoinLink() {
  if (!selectedProcess.value?.videoJoinLink) return
  const url = new URL(selectedProcess.value.videoJoinLink, window.location.origin).toString()
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('候选人视频链接已复制')
  } catch {
    window.prompt('复制候选人视频链接', url)
  }
}

async function startHrVideoCall() {
  if (!selectedProcess.value || startingHrVideo.value || componentDisposed) return
  const processId = selectedProcess.value.id
  startingHrVideo.value = true
  try {
    await disconnectHrVideo()
    if (componentDisposed) return
    const sessionResponse = await interviewApi.createVideoSession(processId)
    if (componentDisposed || selectedProcess.value?.id !== processId) return
    selectedProcess.value.videoJoinLink = sessionResponse.data?.videoJoinLink || selectedProcess.value.videoJoinLink
    hrVideoProcessStageId = sessionResponse.data?.processStageId || selectedProcess.value.processStageId || null
    const stream = await requestCameraAndMicrophone()
    if (componentDisposed) {
      stream.getTracks().forEach((track) => track.stop())
      return
    }
    hrLocalStream = stream
    hrLocalVideo.value.srcObject = hrLocalStream
    playVideo(hrLocalVideo.value)
    const iceServers = await loadIceServers()
    if (componentDisposed || selectedProcess.value?.id !== processId) {
      await disconnectHrVideo({ uploadRecording: false })
      return
    }
    hrPeer = createPeerConnection(iceServers)
    addedIntervieweeIce = new Set()
    pendingIntervieweeIce = []
    hrLocalStream.getTracks().forEach((track) => hrPeer.addTrack(track, hrLocalStream))
    hrRemoteStream = null
    hrPeer.ontrack = (event) => { hrRemoteStream = attachRemoteTrack(hrRemoteVideo.value, event, hrRemoteStream) }
    hrPeer.onconnectionstatechange = () => {
      if (['failed', 'disconnected'].includes(hrPeer.connectionState)) {
        ElMessage.warning('远端视频连接不稳定，请双方保持页面打开，必要时重新开始视频面')
      }
    }
    hrPeer.onicecandidate = async (event) => {
      if (event.candidate) {
        await interviewApi.addHrIce(processId, { iceCandidate: JSON.stringify(event.candidate) })
      }
    }
    const offer = await hrPeer.createOffer()
    await hrPeer.setLocalDescription(offer)
    await interviewApi.publishVideoOffer(processId, { offerSdp: JSON.stringify(offer) })
    await interviewApi.hrJoin(processId)
    if (componentDisposed || selectedProcess.value?.id !== processId) return
    videoActive.value = true
    hrPollTimer = setInterval(async () => {
      if (componentDisposed || !hrPeer || hrVideoPollInProgress) return
      hrVideoPollInProgress = true
      try {
        const state = (await interviewApi.getHrVideoState(processId)).data
        if (componentDisposed || !hrPeer || selectedProcess.value?.id !== processId) return
        if (state.answerSdp && !hrPeer.currentRemoteDescription) {
          await hrPeer.setRemoteDescription(JSON.parse(state.answerSdp))
          await flushPendingIntervieweeIce()
        }
        if (state.intervieweeIceCandidates) {
          const candidates = state.intervieweeIceCandidates.split('\n').filter(Boolean)
          for (const item of candidates) {
            if (!addedIntervieweeIce.has(item)) {
              addedIntervieweeIce.add(item)
              await addIntervieweeIceCandidate(item)
            }
          }
        }
        if (state.sessionStatus === 'RECORDING') {
          void startHrRecordingIfNeeded(processId).catch((error) => { if (!componentDisposed) fail(error) })
        }
        if (shouldHandleHrRecordingEnd(state)) {
          handledHrRecordingEndSignal = hrRecordingEndSignalKey(state)
          clearInterval(hrPollTimer)
          hrPollTimer = null
          scheduleHrRecordingStop(state.recordingEndRequestedAt)
        }
      } catch (error) {
        if (!componentDisposed) console.warn('同步HR视频状态失败', error)
      } finally {
        hrVideoPollInProgress = false
      }
    }, 1000)
    if (!componentDisposed) ElMessage.success('HR视频已就绪，等待面试者加入后同步开始录制')
  } catch (error) {
    await disconnectHrVideo({ uploadRecording: false })
    if (!componentDisposed) ElMessage.error(buildMediaErrorMessage(error))
  } finally {
    startingHrVideo.value = false
  }
}

async function openRecording() { if (!selectedProcess.value) return; try { await interviewApi.openRecording(selectedProcess.value.id, selectedProcess.value.processStageId) } catch (error) { fail(error) } }
async function openAiRecording() { if (!selectedProcess.value) return; try { await interviewApi.openAiRecording(selectedProcess.value.id, selectedProcess.value.processStageId) } catch (error) { fail(error) } }

async function stopHrRecording() {
  try {
    clearInterval(hrPollTimer)
    hrPollTimer = null
    const response = await interviewApi.completeVideo(selectedProcess.value.id)
    handledHrRecordingEndSignal = hrRecordingEndSignalKey(response.data || {})
    scheduleHrRecordingStop(response.data?.recordingEndRequestedAt)
  } catch (error) { fail(error) }
}

function hrRecordingEndSignalKey(state) {
  return state.recordingEndRequestedAt || (state.sessionStatus === 'END_REQUESTED' ? 'END_REQUESTED' : '')
}

function shouldHandleHrRecordingEnd(state) {
  const signal = hrRecordingEndSignalKey(state)
  return signal && signal !== handledHrRecordingEndSignal
}

function scheduleHrRecordingStop(endAt) {
  clearTimeout(hrRecordingEndTimer)
  hrRecordingEndTimer = null
  if (componentDisposed) return
  const delay = Math.max(new Date(endAt || Date.now()).getTime() - Date.now(), 0)
  hrRecordingEndTimer = setTimeout(async () => {
    hrRecordingEndTimer = null
    if (componentDisposed) return
    try {
      await stopAndUploadHrRecording()
      if (componentDisposed) return
      await disconnectHrVideo({ uploadRecording: false })
      if (componentDisposed) return
      await loadAll()
    } catch (error) {
      if (!componentDisposed) fail(error)
    }
  }, delay)
}

async function startHrRecordingIfNeeded(processId) {
  if (componentDisposed || hrRecording.starting || hrRecording.pending || hrRecording.limitReached || hrRecording.error || !hrLocalStream || (hrRecorder && hrRecorder.state !== 'inactive')) return
  hrRecording.starting = true
  try {
    const key = hrVideoRecordingKey(processId)
    const session = await beginRecordingSession(key, {
      kind: 'hr-video',
      processId,
      processStageId: hrVideoProcessStageId,
      fileName: `hr-${processId}.webm`,
      contentType: 'video/webm',
    })
    if (session.byteSize > 0) {
      setPendingHrRecording(session, '上次 HR 录像尚未上传，请先重试上传')
      return
    }
    if (componentDisposed || !hrLocalStream || selectedProcess.value?.id !== processId) return
    hrRecordingSessionKey = key
    hrRecorder = createHrMediaRecorder(hrLocalStream)
    hrRecorderStopPromise = null
    hrRecordingChunkWrites = Promise.resolve()
    hrRecordingChunkWriteError = null
    hrRecordingPendingWriteBytes = 0
    const currentRecorder = hrRecorder
    hrRecorder.ondataavailable = (event) => queueHrRecordingChunk(event.data, currentRecorder)
    hrRecorder.onstop = () => {
      if (hrRecording.limitReached) void stopAndUploadHrRecording().catch((error) => { if (!componentDisposed) fail(error) })
    }
    hrRecorder.start(1000)
    hrRecording.byteSize = 0
    hrRecording.limitReached = false
    hrRecording.notice = ''
    hrRecording.error = ''
    ElMessage.success('双方已进入视频面，录制已同步开始')
  } catch (error) {
    hrRecording.error = `无法启动可靠录像：${error.message}`
    throw error
  } finally {
    hrRecording.starting = false
  }
}

function createHrMediaRecorder(stream) {
  const mimeType = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find((type) => MediaRecorder.isTypeSupported(type))
  const options = { videoBitsPerSecond: 240_000, audioBitsPerSecond: 32_000 }
  if (mimeType) options.mimeType = mimeType
  try {
    return new MediaRecorder(stream, options)
  } catch {
    return mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
  }
}

function retryHrRecordingStart() {
  hrRecording.error = ''
  const processId = selectedProcess.value?.id
  if (processId) void startHrRecordingIfNeeded(processId).catch((error) => { if (!componentDisposed) fail(error) })
}

async function stopAndUploadHrRecording() {
  if (hrRecordingUploadPromise) return hrRecordingUploadPromise
  hrRecording.uploading = true
  hrRecordingUploadPromise = (async () => {
    try {
      await stopHrRecorderToStore()
      await uploadStoredHrRecording()
    } catch (error) {
      await markHrRecordingUploadFailed(error)
      throw error
    }
  })()
  try {
    await hrRecordingUploadPromise
  } finally {
    hrRecordingUploadPromise = null
    hrRecording.uploading = false
  }
}

function hrVideoRecordingKey(processId = selectedProcess.value?.id) {
  return processId ? `hr-video:${processId}` : ''
}

function queueHrRecordingChunk(data, currentRecorder) {
  if (!data?.size || !hrRecordingSessionKey) return
  const key = hrRecordingSessionKey
  hrRecordingPendingWriteBytes += data.size
  if (hrRecordingPendingWriteBytes >= RECORDING_WRITE_HIGH_WATER_BYTES && currentRecorder.state === 'recording') currentRecorder.pause()
  hrRecordingChunkWrites = hrRecordingChunkWrites.then(async () => {
    try {
      if (hrRecordingChunkWriteError) return
      const result = await appendRecordingChunk(key, data)
      hrRecording.byteSize = result.session?.byteSize || hrRecording.byteSize
      if (result.limitReached) {
        markHrRecordingLimitReached(result.session)
        if (currentRecorder.state !== 'inactive') currentRecorder.stop()
      }
    } catch (error) {
      hrRecordingChunkWriteError = error
      hrRecording.error = `录像暂存失败：${error.message}`
      if (currentRecorder.state !== 'inactive') currentRecorder.stop()
    } finally {
      hrRecordingPendingWriteBytes = Math.max(0, hrRecordingPendingWriteBytes - data.size)
      if (!hrRecordingChunkWriteError && !hrRecording.limitReached && hrRecordingPendingWriteBytes <= RECORDING_WRITE_LOW_WATER_BYTES && currentRecorder.state === 'paused') currentRecorder.resume()
    }
  })
}

async function stopHrRecorderToStore() {
  const key = hrRecordingSessionKey || hrVideoRecordingKey()
  if (!key) return
  if (hrRecorder && hrRecorder.state !== 'inactive') {
    const currentRecorder = hrRecorder
    hrRecorderStopPromise = new Promise((resolve) => {
      const previousStop = currentRecorder.onstop
      currentRecorder.onstop = (event) => { previousStop?.(event); resolve() }
      currentRecorder.stop()
    })
  }
  if (hrRecorderStopPromise) await hrRecorderStopPromise
  hrRecorderStopPromise = null
  hrRecorder = null
  await hrRecordingChunkWrites
  if (hrRecordingChunkWriteError) throw hrRecordingChunkWriteError
  const session = await updateRecordingSession(key, { status: 'ready', error: '' })
  if (session?.byteSize > 0) setPendingHrRecording(session)
}

async function uploadStoredHrRecording() {
  const key = hrRecordingSessionKey || hrVideoRecordingKey()
  if (!key) return
  const stored = await buildRecordingFile(key)
  if (!stored?.file.size) return
  if (stored.file.size > MAX_RECORDING_UPLOAD_BYTES) {
    throw new Error(`录像大小为 ${formatRecordingSize(stored.file.size)}，超过服务器 100 MB 限制，已保留在本机浏览器中`)
  }
  setPendingHrRecording(stored.session)
  await updateRecordingSession(key, { status: 'uploading', error: '' })
  await interviewApi.uploadHrVideoRecording(
    stored.session.processId || selectedProcess.value?.id,
    stored.file,
    stored.session.processStageId || hrVideoProcessStageId,
  )
  await deleteRecordingSession(key)
  hrRecording.pending = false
  hrRecording.byteSize = 0
  hrRecording.error = ''
  if (hrRecording.limitReached) hrRecording.notice = hrRecordingLimitNotice(true)
  hrRecordingSessionKey = null
  if (!componentDisposed) ElMessage.success('HR录制已上传')
}

async function retryHrRecordingUpload() {
  if (hrRecording.uploading) return
  hrRecording.uploading = true
  try {
    await uploadStoredHrRecording()
  } catch (error) {
    await markHrRecordingUploadFailed(error)
    if (!componentDisposed) fail(error)
  } finally {
    hrRecording.uploading = false
  }
}

function setPendingHrRecording(session, error = session.error || '') {
  hrRecording.pending = Boolean(session?.byteSize)
  hrRecording.byteSize = session?.byteSize || 0
  hrRecording.error = error
  if (session?.limitReached) markHrRecordingLimitReached(session)
  if (session?.key) hrRecordingSessionKey = session.key
  if (session?.processStageId) hrVideoProcessStageId = session.processStageId
}

function markHrRecordingLimitReached(session) {
  hrRecording.limitReached = true
  hrRecording.pending = Boolean(session?.byteSize)
  hrRecording.byteSize = session?.byteSize || hrRecording.byteSize
  hrRecording.notice = hrRecordingLimitNotice(false)
}

function hrRecordingLimitNotice(uploaded) {
  const limit = formatRecordingSize(RECORDING_STOP_THRESHOLD_BYTES)
  return uploaded
    ? `HR 录像达到 ${limit} 安全阈值后已自动停止并上传，停止后的内容未继续录制`
    : `HR 录像已达到 ${limit} 安全阈值，可用部分已保存在本机；请完成上传，停止后的内容不会继续录制`
}

async function markHrRecordingUploadFailed(error) {
  const key = hrRecordingSessionKey || hrVideoRecordingKey()
  const message = error?.message || '录像上传失败'
  const session = key ? await updateRecordingSession(key, { status: 'failed', error: message }).catch(() => null) : null
  setPendingHrRecording(session || { key, byteSize: hrRecording.byteSize }, message)
}

async function disconnectHrVideo({ uploadRecording = true } = {}) {
  try {
    if (uploadRecording) await stopAndUploadHrRecording()
  } finally {
    clearInterval(hrPollTimer)
    clearTimeout(hrRecordingEndTimer)
    hrPollTimer = null
    hrRecordingEndTimer = null
    hrPeer?.getSenders?.().forEach((sender) => sender.track?.stop())
    hrPeer?.close()
    hrPeer = null
    hrRecorder = null
    handledHrRecordingEndSignal = ''
    hrVideoPollInProgress = false
    hrVideoProcessStageId = null
    hrLocalStream?.getTracks().forEach((track) => track.stop())
    hrLocalStream = null
    hrRemoteStream = null
    pendingIntervieweeIce = []
    if (hrLocalVideo.value) hrLocalVideo.value.srcObject = null
    if (hrRemoteVideo.value) hrRemoteVideo.value.srcObject = null
    videoActive.value = false
  }
}

async function addIntervieweeIceCandidate(item) {
  if (!hrPeer?.remoteDescription) {
    pendingIntervieweeIce.push(item)
    return
  }
  try {
    await hrPeer.addIceCandidate(JSON.parse(item))
  } catch (error) {
    console.warn('添加面试者 ICE失败', error)
  }
}

async function flushPendingIntervieweeIce() {
  const items = pendingIntervieweeIce
  pendingIntervieweeIce = []
  for (const item of items) {
    await addIntervieweeIceCandidate(item)
  }
}

async function loadIceServers() {
  try {
    const response = await interviewApi.getIceServers()
    return response.data?.length ? response.data : defaultIceServers()
  } catch {
    return defaultIceServers()
  }
}

async function restorePendingHrRecording(processId) {
  if (!processId || hrRecorder) return
  try {
    const session = await getRecordingSession(hrVideoRecordingKey(processId))
    if (componentDisposed || selectedProcess.value?.id !== processId) return
    hrRecording.limitReached = false
    hrRecording.notice = ''
    if (session?.byteSize > 0) {
      setPendingHrRecording(session)
    } else {
      hrRecording.pending = false
      hrRecording.byteSize = 0
      hrRecording.error = ''
      hrRecordingSessionKey = null
    }
  } catch (error) {
    if (!componentDisposed && selectedProcess.value?.id === processId) hrRecording.error = error.message
  }
}

async function preserveHrRecording() {
  try {
    await stopHrRecorderToStore()
  } catch (error) {
    await markHrRecordingUploadFailed(error)
  }
}

function handleHrPageHide() {
  void preserveHrRecording()
}

onBeforeUnmount(() => {
  componentDisposed = true
  cancelVideoSummaryRetry()
  window.removeEventListener('pagehide', handleHrPageHide)
  void preserveHrRecording()
  void disconnectHrVideo({ uploadRecording: false }).catch(() => {})
})

async function syncRouteState() {
  const id = Number(route.params.id)
  if (activeTab.value === 'weights') {
    const weightResponse = await interviewApi.listJobKnowledgeWeights()
    if (componentDisposed) return
    weights.value = weightResponse.data
    if (route.name === 'interview-weight-detail' && id) {
      const row = weights.value.find((item) => item.id === id)
      if (row) Object.assign(weightForm, row)
    }
    return
  }
  if (!id) return
  if (route.name === 'interview-knowledge-base-detail') {
    const row = knowledgeBases.value.find((item) => item.id === id)
    if (row) await selectKnowledgeBase(row)
  } else if (route.name === 'interview-llm-config-detail') {
    const row = llmConfigs.value.find((item) => item.id === id)
    if (row) editLlmConfig(row)
  } else if (route.name === 'interview-process-detail') {
    const row = processes.value.find((item) => item.id === id)
    if (row) await loadProcessDetail(row)
  }
}

watch(() => route.fullPath, () => {
  cancelVideoSummaryRetry()
  syncRouteState().catch((error) => { if (!componentDisposed) fail(error) })
})

watch([isProcessDetail, () => selectedProcess.value?.id], ([detail, processId]) => {
  if (!detail) {
    clearOnboardingDraft()
    return
  }
  onboardingDepartmentId.value = selectedProcess.value?.jobDepartmentId || null
  onboardingJobId.value = selectedProcess.value?.jobId || null
  onboardingBaseSalary.value = null
  if (processId) void restorePendingHrRecording(processId)
})

watch(() => selectedProcess.value?.jobDepartmentId, (departmentId) => {
  if (isProcessDetail.value && departmentId) onboardingDepartmentId.value = departmentId
})

function clearOnboardingDraft() {
  onboardingDepartmentId.value = null
  onboardingJobId.value = null
  onboardingBaseSalary.value = null
}

onMounted(() => {
  window.addEventListener('pagehide', handleHrPageHide)
  void loadAll()
})
</script>

<style scoped>
.topline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; }
.sub-tabs { margin: 22px 0; }
.form-grid { min-width: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 16px; }
.llm-config-grid { min-width: 0; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.wide { grid-column: 1 / -1; }
.section-title { margin-top: 28px; }
.inner-surface { min-width: 0; background: var(--surface); }
.detail-surface { margin-top: 18px; }
.detail-headline { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 16px; }
.detail-headline h3 { margin: 6px 0 0; }
.process-workbench { display: grid; grid-template-columns: minmax(0, 1.2fr) minmax(300px, .8fr); grid-template-rows: auto auto; gap: 16px; align-items: start; }
.workbench-panel { min-width: 0; border: 1px solid var(--border); border-radius: var(--radius-lg); padding: 16px; background: var(--surface); box-shadow: var(--shadow-card); }
.workbench-panel h3 { margin: 0 0 12px; }
.candidate-info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.candidate-info-grid div, .resume-ai-box, .process-stats { border-radius: var(--radius-sm); padding: 10px; background: var(--surface-soft); border: 1px solid var(--border); }
.candidate-info-grid span, .resume-ai-box span, .remark-box span { display: block; color: var(--text-muted); margin-bottom: 5px; }
.candidate-info-grid strong, .resume-ai-box strong { color: var(--ink); }
.resume-ai-box { margin-top: 12px; }
.resume-ai-box p { margin: 8px 0 0; line-height: 1.7; }
.panel-title-row { display: flex; justify-content: space-between; gap: 12px; align-items: center; }
.panel-title-row span { color: var(--primary); font-weight: 700; }
.question-number { display: grid; place-items: center; width: 42px; height: 42px; border: 1px solid var(--border-strong); background: var(--surface); color: var(--ink); border-radius: 50%; font-weight: 700; }
.question-number.answered { background: var(--primary); color: #ffffff; border-color: var(--primary); }
.ai-stage-review-list { display: grid; min-width: 0; max-width: 100%; gap: 24px; margin-top: 18px; }
.ai-stage-review { min-width: 0; max-width: 100%; padding-top: 18px; border-top: 1px solid var(--border); }
.ai-stage-review:first-child { padding-top: 0; border-top: 0; }
.ai-stage-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 16px; }
.ai-stage-heading h4 { margin: 4px 0 0; color: var(--ink); font-size: 17px; }
.ai-stage-kicker { color: var(--primary); font-size: 12px; font-weight: 800; letter-spacing: .04em; }
.ai-stage-count { color: var(--text-muted); font-size: 13px; }
.empty-stage-review { margin: 14px 0 0; color: var(--text-muted); }
.ai-question-panel { grid-column: 1; display: grid; min-width: 0; max-height: min(760px, calc(100dvh - 48px)); grid-template-rows: auto minmax(0, 1fr); gap: 16px; overflow: hidden; }
.action-panel { grid-column: 2; align-self: start; height: fit-content; }
.ai-review-content { min-width: 0; min-height: 0; width: 100%; max-width: 100%; padding-right: 8px; overflow-x: hidden; overflow-y: auto; overscroll-behavior: contain; scrollbar-gutter: stable; }
.ai-question-list { display: grid; min-width: 0; gap: 0; margin: 12px 0 0; padding: 0; list-style: none; }
.ai-question-review { min-width: 0; padding: 20px 0; border-top: 1px solid var(--border); }
.ai-question-review:first-child { border-top: 0; }
.ai-question-meta { display: grid; min-width: 0; grid-template-columns: 42px minmax(0, 1fr) auto; gap: 12px; align-items: center; }
.ai-question-meta span:not(.question-number), .ai-question-copy > span, .ai-response-grid span { display: block; margin-bottom: 4px; color: var(--text-muted); font-size: 12px; font-weight: 600; }
.ai-question-meta strong { display: block; min-width: 0; overflow-wrap: anywhere; color: var(--ink); }
.ai-question-score { min-width: 52px; text-align: right; }
.ai-question-copy { min-width: 0; margin: 16px 0; padding-left: 54px; }
.ai-question-copy p, .ai-response-grid p { min-width: 0; margin: 0; overflow-wrap: anywhere; white-space: pre-wrap; line-height: 1.7; }
.ai-response-grid { display: grid; min-width: 0; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; margin-left: 54px; padding-top: 16px; border-top: 1px dashed var(--border); }
.ai-response-grid > div { min-width: 0; }
.ai-interviewer-feedback { margin: 0; overflow-wrap: anywhere; white-space: pre-wrap; line-height: 1.6; color: var(--ink-soft); }
.process-stats { display: grid; gap: 4px; margin-bottom: 12px; }
.process-stats p { margin: 0; color: var(--ink-soft); }
.onboarding-department-form { max-width: 420px; margin-bottom: 12px; }
.onboarding-department-form :deep(.el-form-item) { margin-bottom: 0; }
.onboarding-department-form :deep(.el-select) { width: 100%; }
.action-button-grid { min-width: 0; display: flex; flex-wrap: wrap; gap: 10px; margin-bottom: 14px; }
.template-heading, .stage-list-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }.template-heading { margin-bottom: 18px; }.template-heading h3, .stage-list-head h4 { margin: 0; }.stage-list-head { margin: 18px 0 12px; }.stage-list-head span, .template-option-detail, .human-stage-note { color: var(--text-muted); font-size: 12px; }.template-stage-list { display: grid; gap: 10px; }.template-stage-row { display: grid; grid-template-columns: 32px minmax(150px, 1.2fr) minmax(180px, .9fr) minmax(180px, 1fr) auto; gap: 10px; align-items: center; padding: 12px; border: 1px solid var(--border); border-radius: var(--radius-sm); background: var(--surface-soft); }.stage-order { display: grid; place-items: center; width: 28px; height: 28px; border-radius: 50%; background: #dceee7; color: #175c50; font-weight: 800; font-size: 13px; }.stage-type-switch { white-space: nowrap; }.human-stage-note { min-width: 160px; }.stage-row-actions { display: flex; gap: 2px; white-space: nowrap; }.template-stage-summary { color: var(--text-muted); line-height: 1.6; }.template-option-detail { display: block; margin-top: 3px; }.stage-overview-panel { grid-column: 1 / -1; }.process-stage-timeline { display: grid; gap: 0; margin: 0; padding: 0; list-style: none; }.process-stage-timeline li { display: grid; grid-template-columns: 28px minmax(0, 1fr) auto; align-items: center; gap: 10px; padding: 10px 0; border-bottom: 1px solid var(--border); }.process-stage-timeline li:last-child { border-bottom: 0; }.process-stage-timeline li.active { color: var(--primary); }.process-stage-timeline li.complete { opacity: .72; }.timeline-index { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 50%; background: #e9edf0; color: #66737c; font-size: 12px; font-weight: 800; }.process-stage-timeline li.active .timeline-index { background: #dceee7; color: #175c50; }.process-stage-timeline strong, .process-stage-timeline small { display: block; }.process-stage-timeline small { margin-top: 3px; color: var(--text-muted); font-size: 12px; }
.remark-box { display: grid; gap: 10px; }
.csv-import-box { grid-column: 1 / -1; }
.serial-line { min-width: 0; margin: 8px 0 14px; color: var(--ink-soft); overflow-wrap: anywhere; }
.video-link { display: inline-flex; max-width: 100%; margin-left: 12px; color: var(--primary); font-weight: 700; text-decoration: none; overflow-wrap: anywhere; }
.candidate-preview { margin: 10px 0 16px; padding: 16px; border: 1px solid var(--border); border-radius: var(--radius-lg); background: var(--surface); }
.candidate-preview h4 { margin: 0 0 12px; }
.preview-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.preview-grid span { display: block; color: var(--text-muted); font-size: 12px; margin-bottom: 4px; }
.preview-grid strong { color: var(--ink); }
.video-grid { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 12px; margin: 12px 0 18px; }
.video-box { background: var(--surface); padding: 12px; border-radius: var(--radius-md); border: 1px solid var(--border); }
.video-box span { display: block; margin-bottom: 8px; color: var(--text-muted); }
.video-box video { width: 100%; min-height: 220px; background: #111; border-radius: var(--radius-md); }
.recording-recovery { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin: -4px 0 18px; padding: 10px 12px; border: 1px solid #e4c987; border-radius: 6px; background: #fff9eb; color: #855b09; }
.recording-recovery small { min-width: 0; overflow-wrap: anywhere; line-height: 1.5; }
.recording-recovery :deep(.el-button) { flex: 0 0 auto; margin-left: 0; }
.video-summary-box { display: grid; gap: 10px; padding: 14px; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface-soft); }
.video-summary-box span { color: var(--primary); font-weight: 800; }
.summary-status-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.video-summary-box p { margin: 0; color: var(--ink-soft); line-height: 1.7; }
.video-summary-box strong { display: block; margin-bottom: 4px; color: var(--ink); }
.summary-document { display: grid; gap: 7px; max-height: 460px; padding-right: 8px; overflow-y: auto; }
.summary-document h4 { margin: 12px 0 2px; color: var(--ink); font-size: 15px; }
.summary-document .summary-list-item { position: relative; padding-left: 16px; }
.summary-document .summary-list-item::before { position: absolute; left: 2px; color: var(--primary); content: '•'; }
.transcript-text { white-space: pre-wrap; }
.data-table { margin-top: 18px; }
.config-heading { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; }.config-heading h3, .config-panel-head h3 { margin: 0; }.config-heading .serial-line { margin: 8px 0 0; }.env-badge { padding: 5px 9px; border: 1px solid #bfd3c9; border-radius: 5px; background: #edf6f1; color: #175c50; font-size: 12px; font-weight: 700; }
.config-tabs, .model-tabs { display: flex; flex-wrap: wrap; gap: 8px; margin: 24px 0; padding-bottom: 12px; border-bottom: 1px solid var(--border); }.config-tabs button, .model-tabs button { padding: 10px 14px; border: 1px solid var(--border); border-radius: 6px; background: var(--surface); color: var(--ink-soft); font: inherit; font-size: 13px; font-weight: 650; cursor: pointer; }.config-tabs button:hover, .model-tabs button:hover { color: var(--primary); border-color: var(--primary); }.config-tabs button.active, .model-tabs button.active { color: #fff; background: var(--primary); border-color: var(--primary); }
.config-panel-head { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; margin: 6px 0 18px; }.config-panel-head p { margin: 7px 0 0; color: var(--text-muted); line-height: 1.6; }.config-form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 18px; padding: 20px; border: 1px solid var(--border); border-radius: var(--radius-md); background: var(--surface-soft); }.model-editor { max-width: 880px; }.key-state { color: var(--text-muted); font-size: 13px; }.model-editor .action-row { margin-top: 18px; }
@media (max-width: 900px) { .topline, .detail-headline { flex-direction: column; } .form-grid, .video-grid, .llm-config-grid, .preview-grid, .process-workbench, .candidate-info-grid { grid-template-columns: 1fr; } .ai-question-panel, .action-panel { grid-column: 1 / -1; } .ai-question-panel { height: auto; max-height: none; grid-template-rows: auto; overflow: visible; } .ai-review-content { padding-right: 0; overflow: visible; } .action-panel { position: static; } }
@media (max-width: 900px) { .template-stage-row { grid-template-columns: 32px 1fr; }.stage-type-switch, .template-stage-row > .el-select, .human-stage-note, .stage-row-actions { grid-column: 2; }.stage-row-actions { flex-wrap: wrap; } }
@media (max-width: 700px) { .ai-response-grid { grid-template-columns: 1fr; } .ai-question-copy { padding-left: 0; } .ai-response-grid { margin-left: 0; } }
@media (max-width: 700px) { .config-form-grid { grid-template-columns: 1fr; } .config-heading, .config-panel-head { flex-direction: column; } }
</style>
