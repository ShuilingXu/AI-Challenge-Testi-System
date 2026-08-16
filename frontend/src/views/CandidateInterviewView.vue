<template>
  <div class="interview-shell">
    <main class="interview-workspace">
      <header class="workspace-header">
        <div>
          <p class="page-eyebrow">Candidate interview</p>
          <h1>候选人面试</h1>
        </div>
        <div class="header-actions">
          <span v-if="processSummary" class="stage-badge">{{ processSummary.processStatusView }}</span>
          <RouterLink class="link-chip" to="/user">返回报名记录</RouterLink>
        </div>
      </header>

      <template v-if="processSummary">
        <section class="status-rail" aria-label="面试进度">
          <div><span>当前阶段</span><strong>{{ stageLabel }}</strong></div>
          <div><span>答题进度</span><strong>{{ answeredAiRecords.length }} / {{ processSummary.aiMaxQuestionRounds || '-' }}</strong></div>
          <div><span>当前均分</span><strong>{{ processSummary.aiAverageScore ?? '-' }}</strong></div>
          <div><span>监考状态</span><strong>{{ proctoringStatus }}</strong></div>
        </section>

        <div v-if="processSummary.currentStage === 'AI'" class="ai-workspace">
          <aside class="exam-sidebar">
            <section class="sidebar-section">
              <div class="section-heading">
                <span>考试录像</span>
                <i class="recording-dot" :class="{ active: aiExamRecording.active }"></i>
              </div>
              <video ref="aiExamVideo" class="proctor-video" autoplay muted playsinline></video>
              <strong class="recording-label">{{ aiExamRecordingStatusText }}</strong>
              <div v-if="aiExamRecording.pending || aiExamRecording.limitReached" class="recording-recovery">
                <small>{{ aiExamRecording.error || aiExamRecording.notice || `有 ${formatRecordingSize(aiExamRecording.byteSize)} 录像等待上传` }}</small>
                <el-button v-if="aiExamRecording.pending" size="small" plain :loading="aiExamRecording.uploading" @click="retryAiExamRecordingUpload">重试上传</el-button>
              </div>
            </section>

            <section class="sidebar-section compact-section">
              <div class="section-heading"><span>轮次</span><strong>{{ currentRoundLabel }}</strong></div>
              <div class="round-track">
                <span
                  v-for="round in processSummary.aiMaxQuestionRounds || 1"
                  :key="round"
                  :class="{ done: round <= answeredAiRecords.length, current: round === currentQuestion?.sequenceNo }"
                >{{ round }}</span>
              </div>
              <dl class="exam-facts">
                <div><dt>全屏</dt><dd>{{ antiCheat.fullscreen ? '已进入' : '未进入' }}</dd></div>
                <div><dt>监考</dt><dd>已启用</dd></div>
              </dl>
            </section>

            <el-button v-if="isAiExamInProgress()" type="primary" class="wide-action" @click="enterAiExamMode">
              {{ aiExamRecording.active ? '返回全屏答题' : '开始全屏答题' }}
            </el-button>
            <el-button class="wide-action" @click="loadProcessRecords">刷新状态</el-button>
          </aside>

          <section class="answer-workspace">
            <div v-if="aiStatusText" class="ai-status-card" :class="{ busy: aiSubmitState.submitting, terminal: isAiTerminal }">
              <span class="status-dot"></span>
              <div><strong>{{ aiStatusText }}</strong><small>{{ aiStatusHint }}</small></div>
            </div>

            <div v-if="currentQuestion" class="current-question">
              <div class="question-meta"><span>第 {{ currentQuestion.sequenceNo }} 题</span><em>{{ currentQuestion.knowledgePoint }}</em></div>
              <h2>{{ currentQuestion.questionContent }}</h2>
              <div class="answer-composer">
                <el-input v-model="aiAnswer.answerContent" type="textarea" :rows="7" placeholder="在这里输入你的回答" :disabled="aiSubmitState.submitting || aiAnswerDisabled" @copy.prevent @cut.prevent @paste.prevent @drop.prevent />
                <div class="composer-footer">
                  <span>{{ aiAnswer.answerContent.length }} 字</span>
                  <el-button type="primary" size="large" :loading="aiSubmitState.submitting" :disabled="aiSubmitState.submitting || aiAnswerDisabled" @click="submitAiAnswer">
                    {{ aiSubmitState.submitting ? '正在评分' : '提交本题' }}
                  </el-button>
                </div>
              </div>
            </div>
            <div v-else class="terminal-state" :class="{ rejected: processSummary.overallStatus === 'REJECTED' }">
              <strong>{{ terminalStateTitle }}</strong>
              <p>{{ terminalStateDescription }}</p>
            </div>

            <div v-if="aiSubmitState.submitting" class="processing-state">
              <span class="processing-spinner"></span>
              <div><strong>{{ aiSubmitState.message }}</strong><p>{{ aiSubmitOverlayHint }}</p></div>
              <div v-if="isStreamMode && aiStreamText" class="ai-stream-log">{{ aiStreamText }}</div>
            </div>

            <section v-if="answeredAiRecords.length" class="answer-history">
              <div class="history-heading"><h2>已完成题目</h2><span>{{ answeredAiRecords.length }} 题</span></div>
              <details v-for="item in answeredAiRecords" :key="item.id" class="history-item">
                <summary>
                  <span>Q{{ item.sequenceNo }}</span>
                  <strong>{{ item.questionContent }}</strong>
                  <em>{{ item.averageScore ?? '-' }} 分</em>
                </summary>
                <div class="history-content">
                  <div><span>你的回答</span><p>{{ item.answerContent }}</p></div>
                  <div v-if="item.interviewerComment" class="feedback"><span>面试官反馈</span><p>{{ item.interviewerComment }}</p></div>
                </div>
              </details>
            </section>
          </section>
        </div>

        <section v-else-if="processSummary.currentStage === 'VIDEO'" class="video-workspace">
          <div class="video-workspace-head">
            <div><p class="page-eyebrow">Live interview</p><h2>{{ stageLabel }}</h2></div>
            <div class="header-actions">
              <el-button type="primary" :loading="joiningVideo" :disabled="joiningVideo" @click="joinVideo">加入视频面试</el-button>
              <el-button @click="stopRecording">结束并上传录制</el-button>
            </div>
          </div>
          <div class="video-grid">
            <div class="video-box"><span>我的画面</span><video ref="localVideo" autoplay muted playsinline></video></div>
            <div class="video-box"><span>面试官画面</span><video ref="remoteVideo" autoplay playsinline></video></div>
          </div>
          <div v-if="videoRecording.pending || videoRecording.uploading || videoRecording.error || videoRecording.limitReached" class="recording-recovery video-recording-recovery">
            <small>{{ videoRecording.uploading ? '面试录像上传中' : (videoRecording.error || videoRecording.notice || `有 ${formatRecordingSize(videoRecording.byteSize)} 录像等待上传`) }}</small>
            <el-button v-if="videoRecording.pending" size="small" plain :loading="videoRecording.uploading" @click="retryVideoRecordingUpload">重试上传</el-button>
            <el-button v-else-if="videoRecording.error" size="small" plain @click="retryVideoRecordingStart">重试录制</el-button>
          </div>
        </section>

        <section v-else class="terminal-state"><strong>{{ processSummary.processStatusView }}</strong><p>当前面试流程已更新。</p></section>
      </template>

      <div v-else class="loading-state">正在加载面试流程...</div>
      <p v-if="refreshState.retryCount > 0" class="retry-message">状态同步失败，正在进行第 {{ refreshState.retryCount }} 次重试：{{ refreshState.lastError }}</p>
      <p v-else-if="heartbeatState.failed" class="retry-message">实时状态同步暂时中断，系统将继续自动重试：{{ heartbeatState.message }}</p>
    </main>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { interviewApi } from '../services/api'
import { attachRemoteTrack, buildMediaErrorMessage, createPeerConnection, defaultIceServers, playVideo, requestCameraAndMicrophone } from '../utils/media'
import { appendRecordingChunk, beginRecordingSession, buildRecordingFile, deleteRecordingSession, formatRecordingSize, getRecordingSession, MAX_RECORDING_UPLOAD_BYTES, RECORDING_STOP_THRESHOLD_BYTES, RECORDING_WRITE_HIGH_WATER_BYTES, RECORDING_WRITE_LOW_WATER_BYTES, updateRecordingSession } from '../utils/recordingStore'

const route = useRoute()
const router = useRouter()
const sessionForm = reactive({ processId: route.params.processId ? Number(route.params.processId) : route.query.processId ? Number(route.query.processId) : null })
const aiAnswer = reactive({ answerContent: '' })
const aiRecords = ref([])
const processSummary = ref(null)
const currentQuestion = ref(null)
const refreshState = reactive({ loading: false, retryCount: 0, lastError: '' })
const heartbeatState = reactive({ failed: false, message: '' })
const aiSubmitState = reactive({ submitting: false, message: '' })
const aiStreamText = ref('')
const aiPendingRefresh = reactive({ active: false, attempts: 0, questionId: null })
const runtimeConfig = reactive({ disableDevtoolsShortcuts: true })
const antiCheat = reactive({ fullscreen: false, switchCount: 0, hasEnteredFullscreen: false, aiEndNotified: false })
const aiExamRecording = reactive({ active: false, starting: false, uploading: false, uploaded: false, pending: false, limitReached: false, byteSize: 0, notice: '', error: '' })
const videoRecording = reactive({ starting: false, uploading: false, pending: false, limitReached: false, byteSize: 0, notice: '', error: '' })
const localVideo = ref(null)
const remoteVideo = ref(null)
const aiExamVideo = ref(null)
const joiningVideo = ref(false)
let localStream = null
let aiExamStream = null
let componentDisposed = false
let peer = null
let pollTimer = null
let aiRefreshTimer = null
let heartbeatTimer = null
let heartbeatInFlight = false
let recorder = null
let recorderStopPromise = null
let recordingChunkWrites = Promise.resolve()
let recordingChunkWriteError = null
let recordingPendingWriteBytes = 0
let addedHrIce = new Set()
let remoteStream = null
let pendingHrIce = []
let recordingUploadPromise = null
let handledRecordingEndSignal = ''
let recordingEndTimer = null
let videoPollInProgress = false
let videoProcessStageId = null
let lastSwitchReportAt = 0
let antiCheatQueue = []
let antiCheatSending = false
let antiCheatRetryTimer = null
let aiExamRecorder = null
let aiExamRecorderStopPromise = null
let aiExamChunkWrites = Promise.resolve()
let aiExamChunkWriteError = null
let aiExamPendingWriteBytes = 0
let aiExamRecordingUploadPromise = null
let aiAnswerAbortController = null
let processStateRevision = 0
let processLoadGeneration = 0
let heartbeatDataRefreshTimer = null
let heartbeatDataRefreshInFlight = false
let heartbeatDataRefreshQueued = false

const currentAiRecords = computed(() => processSummary.value?.processStageId
  ? aiRecords.value.filter((item) => item.processStageId === processSummary.value.processStageId)
  : aiRecords.value)
const answeredAiRecords = computed(() => currentAiRecords.value.filter((item) => item.answerStatus === 'COMPLETED'))
const pendingQuestionRecord = computed(() => currentAiRecords.value.find((item) =>
  ['PENDING', 'PROCESSING', 'FAILED'].includes(item.questionStatus) && !item.answerContent,
))
const processingAnswerRecord = computed(() => currentAiRecords.value.find((item) => item.answerStatus === 'PROCESSING'))
const isAiTerminal = computed(() => processSummary.value?.currentStage === 'AI' && (
  processSummary.value?.stageStatus === 'WAITING_APPROVAL'
  || processSummary.value?.stageStatus === 'REJECTED'
  || processSummary.value?.overallStatus !== 'IN_PROGRESS'
))
const stageLabel = computed(() => processSummary.value?.stageName || ({ AI: 'AI 面试', VIDEO: '视频面试', ONSITE: '线下面试' }[processSummary.value?.currentStage] || processSummary.value?.currentStage || '-'))
const proctoringStatus = computed(() => {
  if (processSummary.value?.currentStage !== 'AI') return '不适用'
  if (aiExamRecording.uploading) return '录像上传中'
  if (aiExamRecording.uploaded) return '录像已保存'
  if (isAiTerminal.value) return '考试已结束'
  return aiExamRecording.active ? '录像中' : '待开始'
})
const currentRoundLabel = computed(() => {
  const maximum = processSummary.value?.aiMaxQuestionRounds || '-'
  return currentQuestion.value ? `${currentQuestion.value.sequenceNo} / ${maximum}` : `${answeredAiRecords.value.length} / ${maximum}`
})
const terminalStateTitle = computed(() => processSummary.value?.overallStatus === 'REJECTED' ? '本轮面试已结束' : '答题已完成')
const terminalStateDescription = computed(() => {
  if (processSummary.value?.overallStatus === 'REJECTED') return processSummary.value?.processStatusView || '本次 AI 面试未达到进入下一阶段的要求。'
  if (processSummary.value?.stageStatus === 'WAITING_APPROVAL') return '回答和录像正在等待 HR 审核，请留意报名记录中的状态更新。'
  return '系统正在同步最终结果。'
})

const aiStatusText = computed(() => {
  if (aiSubmitState.submitting) return aiSubmitState.message || 'AI正在处理你的回答'
  if (aiPendingRefresh.active) return 'AI仍在后台处理中'
  if (processSummary.value?.currentStage !== 'AI') return ''
  if (processSummary.value?.stageStatus === 'WAITING_APPROVAL') return `${stageLabel.value}已完成，等待HR审批`
  if (processSummary.value?.stageStatus === 'REJECTED' || processSummary.value?.overallStatus === 'REJECTED') return 'AI面试已结束'
  if (refreshState.loading) return '正在同步面试状态'
  if (!currentQuestion.value) return '正在生成下一道题'
  return '请阅读当前问题并作答'
})

const aiStatusHint = computed(() => {
  if (aiSubmitState.submitting) return '评分、评价和下一题生成可能需要几十秒'
  if (aiPendingRefresh.active) return '请求已超时但不代表失败，系统会自动刷新最新面试状态'
  if (processSummary.value?.overallStatus === 'REJECTED') return '你的回答与录像已保存，可返回报名记录查看流程状态'
  if (processSummary.value?.stageStatus === 'WAITING_APPROVAL') return '请保持关注流程状态，HR审批后会进入下一阶段'
  if (processingAnswerRecord.value) return '正在处理已提交的相同回答，请保持当前页面，系统会自动刷新'
  if (pendingQuestionRecord.value?.questionStatus === 'FAILED') return '题目生成暂时失败，系统正在自动重试'
  if (!currentQuestion.value && processSummary.value?.currentStage === 'AI') return '系统会自动刷新题目，请不要重复提交'
  return '提交后按钮会锁定，避免重复提交'
})

const isStreamMode = computed(() => processSummary.value?.aiOutputMode === 'STREAM')

const aiSubmitOverlayHint = computed(() => isStreamMode.value
  ? 'AI输出会实时显示在这里，请勿刷新页面。'
  : '请勿重复点击或刷新页面，AI 正在评分并生成后续安排。')

const aiAnswerDisabled = computed(() => isAiExamInProgress() && !aiExamRecording.active)

const aiExamRecordingStatusText = computed(() => {
  if (aiExamRecording.uploading) return '录像上传中'
  if (aiExamRecording.active) return '摄像头和麦克风录像中'
  if (aiExamRecording.starting) return '正在请求摄像头和麦克风权限'
  if (aiExamRecording.uploaded) return '录像已上传'
  if (aiExamRecording.error) return aiExamRecording.error
  return '未开始录像'
})

function fail(error) { ElMessage.error(error.message || '操作失败') }
async function loadProcessRecords(options = {}) {
  if (refreshState.loading) {
    return
  }
  refreshState.loading = true
  const generation = ++processLoadGeneration
  const requestRevision = processStateRevision
  try {
    if (!sessionForm.processId) {
      ElMessage.warning('请从面试者首页的报名记录进入面试')
      router.push('/user')
      return
    }
    const [processResponse, questionResponse, recordsResponse] = await Promise.all([
      interviewApi.getIntervieweeProcess(sessionForm.processId),
      interviewApi.getNextAiQuestion(sessionForm.processId),
      interviewApi.listIntervieweeAiRecords({ processId: sessionForm.processId }),
    ])
    if (componentDisposed || generation !== processLoadGeneration || requestRevision !== processStateRevision) {
      scheduleHeartbeatDataRefresh()
      return
    }
    processSummary.value = processResponse.data
    currentQuestion.value = questionResponse.data
    aiRecords.value = recordsResponse.data
    processStateRevision += 1
    if (currentQuestion.value?.answerStatus === 'FAILED' && !aiAnswer.answerContent) {
      aiAnswer.answerContent = currentQuestion.value.answerContent || ''
    }
    refreshState.retryCount = 0
    refreshState.lastError = ''
    notifyAiFinishedIfNeeded()
    syncAiAutoRefresh()
    syncHeartbeat()
  } catch (error) {
    if (componentDisposed || generation !== processLoadGeneration || requestRevision !== processStateRevision) {
      scheduleHeartbeatDataRefresh()
      return
    }
    refreshState.retryCount += 1
    refreshState.lastError = error.message || '刷新失败'
    if (!options.silent) {
      fail(error)
    }
    scheduleAiRefresh(nextRefreshDelay())
  } finally {
    refreshState.loading = false
    if (heartbeatDataRefreshQueued) scheduleHeartbeatDataRefresh()
  }
}

function syncAiAutoRefresh() {
  if (processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'IN_PROGRESS' && !currentQuestion.value
    && !heartbeatDataRefreshQueued && !heartbeatDataRefreshInFlight) {
    scheduleAiRefresh(3000)
  } else {
    clearAiRefresh()
  }
}

function scheduleAiRefresh(delay) {
  if (componentDisposed) return
  clearAiRefresh()
  aiRefreshTimer = setTimeout(() => loadProcessRecords({ silent: true }), delay)
}

function clearAiRefresh() {
  clearTimeout(aiRefreshTimer)
  aiRefreshTimer = null
}

function isTimeoutError(error) {
  return error?.code === 'ECONNABORTED' || /timeout|exceeded/i.test(error?.message || '')
}

function nextRefreshDelay() {
  return Math.min(3000 + refreshState.retryCount * 1000, 10000)
}
async function submitAiAnswer() {
  if (aiSubmitState.submitting) {
    ElMessage.info('AI正在处理上一轮回答，请稍候')
    return
  }
  try {
    if (!currentQuestion.value) {
      ElMessage.warning('当前没有可提交的问题，请等待题目生成')
      return
    }
    if (!aiAnswer.answerContent.trim()) {
      ElMessage.warning('请先填写回答内容')
      return
    }
    if (!aiExamRecording.active || !antiCheat.fullscreen) {
      ElMessage.warning('请先开启摄像头/麦克风录像并进入全屏答题模式')
      await enterAiExamMode()
      return
    }
    aiSubmitState.submitting = true
    aiSubmitState.message = 'AI正在评分并生成下一步'
    aiPendingRefresh.questionId = currentQuestion.value.id
    clearAiRefresh()
    if (isStreamMode.value) {
      await submitAiAnswerStream()
    } else {
      const response = await interviewApi.submitAiAnswer({ processId: sessionForm.processId, questionId: currentQuestion.value.id, answerContent: aiAnswer.answerContent })
      if (response.data?.answerStatus === 'PROCESSING') {
        aiPendingRefresh.active = true
        aiPendingRefresh.attempts = 0
        schedulePendingAiRefresh()
        return
      }
    }
    aiSubmitState.message = '正在同步最新面试状态'
    aiPendingRefresh.active = false
    aiPendingRefresh.attempts = 0
    aiPendingRefresh.questionId = null
    aiAnswer.answerContent = ''
    ElMessage.success('AI 回答已提交')
    await loadProcessRecords()
  } catch (error) {
    if (componentDisposed || error?.name === 'AbortError') return
    if (isTimeoutError(error)) {
      aiSubmitState.message = 'AI仍在后台处理，正在自动刷新状态'
      aiPendingRefresh.active = true
      aiPendingRefresh.attempts = 0
      ElMessage.info('AI处理时间较长，系统将自动刷新状态，请不要重复提交')
      schedulePendingAiRefresh()
      return
    }
    fail(error)
    scheduleAiRefresh(nextRefreshDelay())
  }
  finally {
    if (!aiPendingRefresh.active) {
      aiSubmitState.submitting = false
      aiSubmitState.message = ''
    }
  }
}

function schedulePendingAiRefresh() {
  if (componentDisposed) return
  clearAiRefresh()
  aiRefreshTimer = setTimeout(refreshPendingAiState, Math.min(3000 + aiPendingRefresh.attempts * 1000, 10000))
}

async function refreshPendingAiState() {
  aiPendingRefresh.attempts += 1
  aiSubmitState.message = `AI仍在后台处理，正在第 ${aiPendingRefresh.attempts} 次同步状态`
  try {
    await loadProcessRecords({ silent: true })
    const pendingRecord = aiRecords.value.find((item) => item.id === aiPendingRefresh.questionId)
    if (pendingRecord?.answerStatus === 'FAILED') {
      aiPendingRefresh.active = false
      aiPendingRefresh.attempts = 0
      aiPendingRefresh.questionId = null
      aiSubmitState.submitting = false
      aiSubmitState.message = ''
      aiAnswer.answerContent = pendingRecord.answerContent || aiAnswer.answerContent
      ElMessage.error('AI 评分失败，请使用相同回答重新提交')
      return
    }
    if (!isPendingAiResolved()) {
      schedulePendingAiRefresh()
      return
    }
    aiPendingRefresh.active = false
    aiPendingRefresh.attempts = 0
    aiPendingRefresh.questionId = null
    aiSubmitState.submitting = false
    aiSubmitState.message = ''
    aiAnswer.answerContent = ''
    ElMessage.success('AI处理已完成，状态已更新')
  } catch {
    if (componentDisposed) return
    schedulePendingAiRefresh()
  }
}

function syncHeartbeat() {
  const active = processSummary.value?.overallStatus === 'IN_PROGRESS'
  if (!active || !sessionForm.processId || componentDisposed) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
    return
  }
  if (heartbeatTimer) return
  heartbeatTimer = setInterval(async () => {
    if (componentDisposed || heartbeatInFlight || processSummary.value?.overallStatus !== 'IN_PROGRESS') return
    heartbeatInFlight = true
    try {
      const response = await interviewApi.heartbeat(sessionForm.processId)
      if (!componentDisposed && response.data) {
        const previousSummary = processSummary.value
        if (isOlderProcessSummary(response.data, previousSummary)) return
        const refreshInterviewData = shouldRefreshAfterHeartbeat(previousSummary, response.data)
        processSummary.value = response.data
        processStateRevision += 1
        heartbeatState.failed = false
        heartbeatState.message = ''
        notifyAiFinishedIfNeeded()
        uploadVideoRecordingAfterTerminalHeartbeat(previousSummary, response.data)
        if (refreshInterviewData) scheduleHeartbeatDataRefresh()
        syncAiAutoRefresh()
        syncHeartbeat()
      }
    } catch (error) {
      if (!componentDisposed) {
        heartbeatState.failed = true
        heartbeatState.message = error.message || '请检查网络连接'
      }
    } finally {
      heartbeatInFlight = false
    }
  }, 30000)
}

function isOlderProcessSummary(candidate, current) {
  const candidateTime = Date.parse(candidate?.updatedAt || '')
  const currentTime = Date.parse(current?.updatedAt || '')
  return Number.isFinite(candidateTime) && Number.isFinite(currentTime) && candidateTime < currentTime
}

function shouldRefreshAfterHeartbeat(previousSummary, nextSummary) {
  if (!previousSummary) return true
  return previousSummary.currentStage !== nextSummary.currentStage
    || previousSummary.processStageId !== nextSummary.processStageId
    || previousSummary.stageStatus !== nextSummary.stageStatus
    || previousSummary.aiAverageScore !== nextSummary.aiAverageScore
    || previousSummary.updatedAt !== nextSummary.updatedAt
    || (nextSummary.currentStage === 'AI' && nextSummary.stageStatus === 'IN_PROGRESS' && !currentQuestion.value)
}

function scheduleHeartbeatDataRefresh() {
  if (componentDisposed) return
  heartbeatDataRefreshQueued = true
  if (heartbeatDataRefreshTimer || heartbeatDataRefreshInFlight || refreshState.loading) return
  heartbeatDataRefreshTimer = setTimeout(() => {
    heartbeatDataRefreshTimer = null
    void refreshHeartbeatInterviewData()
  }, 150)
}

async function refreshHeartbeatInterviewData() {
  if (componentDisposed || refreshState.loading || heartbeatDataRefreshInFlight) return
  heartbeatDataRefreshQueued = false
  heartbeatDataRefreshInFlight = true
  const revision = processStateRevision
  const aiQuestionActive = processSummary.value?.currentStage === 'AI'
    && processSummary.value?.stageStatus === 'IN_PROGRESS'
    && processSummary.value?.overallStatus === 'IN_PROGRESS'
  let refreshFailed = false
  try {
    const [questionResponse, recordsResponse] = await Promise.all([
      aiQuestionActive ? interviewApi.getNextAiQuestion(sessionForm.processId) : Promise.resolve({ data: null }),
      interviewApi.listIntervieweeAiRecords({ processId: sessionForm.processId }),
    ])
    if (componentDisposed) return
    if (revision !== processStateRevision) {
      heartbeatDataRefreshQueued = true
      return
    }
    currentQuestion.value = questionResponse.data
    aiRecords.value = recordsResponse.data
    if (currentQuestion.value?.answerStatus === 'FAILED' && !aiAnswer.answerContent) {
      aiAnswer.answerContent = currentQuestion.value.answerContent || ''
    }
  } catch {
    refreshFailed = true
  } finally {
    heartbeatDataRefreshInFlight = false
    if (heartbeatDataRefreshQueued) scheduleHeartbeatDataRefresh()
    else if (refreshFailed && !componentDisposed && aiQuestionActive) scheduleAiRefresh(nextRefreshDelay())
    else syncAiAutoRefresh()
  }
}

function isPendingAiResolved() {
  if (processSummary.value?.currentStage !== 'AI' || processSummary.value?.stageStatus === 'WAITING_APPROVAL' || processSummary.value?.overallStatus !== 'IN_PROGRESS') {
    return true
  }
  const pendingRecord = aiRecords.value.find((item) => item.id === aiPendingRefresh.questionId)
  if (pendingRecord?.answerStatus === 'COMPLETED') {
    return true
  }
  return currentQuestion.value && currentQuestion.value.id !== aiPendingRefresh.questionId
}

async function enterAiExamMode() {
  if (isAiExamInProgress()) {
    await ensureAiExamRecordingStarted()
  }
  if (document.fullscreenElement !== document.documentElement) {
    try {
      await document.documentElement.requestFullscreen()
      antiCheat.hasEnteredFullscreen = true
    } catch (error) {
      antiCheat.hasEnteredFullscreen = true
      await reportAntiCheat('FULLSCREEN_DENIED', error.message || '全屏授权失败')
      ElMessage.warning('浏览器未允许全屏，请允许后继续答题')
    }
  } else {
    antiCheat.hasEnteredFullscreen = true
  }
}

async function submitAiAnswerStream() {
  aiStreamText.value = ''
  aiAnswerAbortController = new AbortController()
  try {
    await interviewApi.submitAiAnswerStream({ processId: sessionForm.processId, questionId: currentQuestion.value.id, answerContent: aiAnswer.answerContent }, ({ event, data }) => {
      const text = parseStreamData(data)
      if (event === 'error') throw new Error(text || '流式提交失败')
      if (event === 'token' && text) {
        aiStreamText.value += text
        aiSubmitState.message = 'AI正在实时输出'
      }
      if (event === 'done') aiSubmitState.message = 'AI处理完成'
    }, { signal: aiAnswerAbortController.signal })
  } finally {
    aiAnswerAbortController = null
  }
}

function parseStreamData(data) {
  try {
    const parsed = JSON.parse(data)
    return parsed.message || parsed.processStatusView || parsed.questionContent || data
  } catch {
    return data
  }
}

async function ensureAiExamRecordingStarted() {
  if (aiExamRecording.active || aiExamRecording.starting) return
  if (aiExamRecording.limitReached) {
    ElMessage.warning(recordingLimitNotice(aiExamRecording.uploaded))
    return
  }
  if (!window.MediaRecorder) {
    aiExamRecording.error = '当前浏览器不支持录像，请使用最新版 Chrome/Edge'
    await reportAntiCheat('AI_RECORDING_UNSUPPORTED', aiExamRecording.error)
    throw new Error(aiExamRecording.error)
  }
  aiExamRecording.starting = true
  aiExamRecording.error = ''
  try {
    const key = aiExamRecordingKey()
    const session = await beginRecordingSession(key, {
      kind: 'candidate-ai',
      processId: sessionForm.processId,
      fileName: `ai-exam-${sessionForm.processId}.webm`,
      contentType: 'video/webm',
    })
    if (session.byteSize > 0) {
      setAiPendingRecording(session, '上次录像尚未上传，请先重试上传')
      throw new Error(aiExamRecording.error)
    }
    const stream = await requestCameraAndMicrophone()
    if (componentDisposed) {
      stream.getTracks().forEach((track) => track.stop())
      return
    }
    aiExamStream = stream
    if (aiExamVideo.value) {
      aiExamVideo.value.srcObject = aiExamStream
      playVideo(aiExamVideo.value)
    }
    aiExamRecorder = createMediaRecorder(aiExamStream)
    aiExamRecorderStopPromise = null
    aiExamChunkWrites = Promise.resolve()
    aiExamChunkWriteError = null
    aiExamPendingWriteBytes = 0
    const currentRecorder = aiExamRecorder
    aiExamRecorder.ondataavailable = (event) => queueAiExamRecordingChunk(event.data, currentRecorder)
    aiExamRecorder.onstop = () => {
      aiExamRecording.active = false
      if (aiExamRecording.limitReached) void stopAndUploadAiExamRecording().catch((error) => { if (!componentDisposed) fail(error) })
    }
    aiExamRecorder.start(1000)
    aiExamRecording.active = true
    aiExamRecording.uploaded = false
    aiExamRecording.pending = false
    aiExamRecording.limitReached = false
    aiExamRecording.byteSize = 0
    aiExamRecording.notice = ''
    await reportAntiCheat('AI_RECORDING_STARTED', 'AI答题摄像头和麦克风录像已开始')
    ElMessage.success('AI答题录像已开始')
  } catch (error) {
    stopAiExamStream()
    aiExamRecording.error = buildMediaErrorMessage(error)
    await reportAntiCheat('AI_RECORDING_DENIED', aiExamRecording.error)
    ElMessage.error(aiExamRecording.error)
    throw error
  } finally {
    aiExamRecording.starting = false
  }
}

function createMediaRecorder(stream) {
  const mimeType = ['video/webm;codecs=vp9,opus', 'video/webm;codecs=vp8,opus', 'video/webm'].find((type) => MediaRecorder.isTypeSupported(type))
  const options = { videoBitsPerSecond: 240_000, audioBitsPerSecond: 32_000 }
  if (mimeType) options.mimeType = mimeType
  try {
    return new MediaRecorder(stream, options)
  } catch {
    return mimeType ? new MediaRecorder(stream, { mimeType }) : new MediaRecorder(stream)
  }
}

async function stopAndUploadAiExamRecording() {
  if (aiExamRecordingUploadPromise) return aiExamRecordingUploadPromise
  aiExamRecording.uploading = true
  aiExamRecordingUploadPromise = (async () => {
    try {
      await stopAiExamRecorderToStore()
      await uploadStoredAiExamRecording()
    } catch (error) {
      await markAiRecordingUploadFailed(error)
      throw error
    }
  })()
  try {
    await aiExamRecordingUploadPromise
  } finally {
    aiExamRecordingUploadPromise = null
    aiExamRecording.uploading = false
    stopAiExamStream()
  }
}

function stopAiExamStream() {
  aiExamStream?.getTracks().forEach((track) => track.stop())
  aiExamStream = null
  aiExamRecording.active = false
  if (aiExamVideo.value) aiExamVideo.value.srcObject = null
}

function reportAntiCheat(eventType, detail) {
  if (!sessionForm.processId || componentDisposed) return Promise.resolve(null)
  const eventId = globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
  antiCheatQueue.push({
    processId: sessionForm.processId,
    eventType,
    detail,
    eventId,
    occurredAtEpochMillis: Date.now(),
  })
  void flushAntiCheatQueue()
  return Promise.resolve(null)
}

async function flushAntiCheatQueue() {
  if (antiCheatSending || componentDisposed || !antiCheatQueue.length) return
  antiCheatSending = true
  clearTimeout(antiCheatRetryTimer)
  antiCheatRetryTimer = null
  try {
    while (!componentDisposed && antiCheatQueue.length) {
      const event = antiCheatQueue[0]
      const response = await interviewApi.reportAntiCheatEvent(event)
      antiCheatQueue.shift()
      if (response.data) {
        processSummary.value = response.data
        notifyAiFinishedIfNeeded()
        if (response.data.stageStatus === 'WAITING_APPROVAL') {
          currentQuestion.value = null
          clearAiRefresh()
        }
      }
    }
  } catch (error) {
    if (!componentDisposed) {
      console.warn('防作弊事件上报失败，将自动重试', error)
      antiCheatRetryTimer = setTimeout(() => { void flushAntiCheatQueue() }, 2000)
    }
  } finally {
    antiCheatSending = false
  }
}

function aiExamRecordingKey() {
  return `candidate-ai:${sessionForm.processId}`
}

function candidateVideoRecordingKey() {
  return `candidate-video:${sessionForm.processId}`
}

function queueAiExamRecordingChunk(data, currentRecorder) {
  if (!data?.size) return
  aiExamPendingWriteBytes += data.size
  if (aiExamPendingWriteBytes >= RECORDING_WRITE_HIGH_WATER_BYTES && currentRecorder.state === 'recording') currentRecorder.pause()
  aiExamChunkWrites = aiExamChunkWrites.then(async () => {
    try {
      if (aiExamChunkWriteError) return
      const result = await appendRecordingChunk(aiExamRecordingKey(), data)
      aiExamRecording.byteSize = result.session?.byteSize || aiExamRecording.byteSize
      if (result.limitReached) {
        markAiRecordingLimitReached(result.session)
        if (currentRecorder.state !== 'inactive') currentRecorder.stop()
      }
    } catch (error) {
      aiExamChunkWriteError = error
      aiExamRecording.error = `录像暂存失败：${error.message}`
      if (currentRecorder.state !== 'inactive') currentRecorder.stop()
    } finally {
      aiExamPendingWriteBytes = Math.max(0, aiExamPendingWriteBytes - data.size)
      if (!aiExamChunkWriteError && !aiExamRecording.limitReached && aiExamPendingWriteBytes <= RECORDING_WRITE_LOW_WATER_BYTES && currentRecorder.state === 'paused') currentRecorder.resume()
    }
  })
}

async function stopAiExamRecorderToStore() {
  if (aiExamRecorder && aiExamRecorder.state !== 'inactive') {
    const currentRecorder = aiExamRecorder
    aiExamRecorderStopPromise = new Promise((resolve) => {
      const previousStop = currentRecorder.onstop
      currentRecorder.onstop = (event) => { previousStop?.(event); resolve() }
      currentRecorder.stop()
    })
  }
  if (aiExamRecorderStopPromise) await aiExamRecorderStopPromise
  aiExamRecorderStopPromise = null
  aiExamRecorder = null
  await aiExamChunkWrites
  if (aiExamChunkWriteError) throw aiExamChunkWriteError
  const session = await updateRecordingSession(aiExamRecordingKey(), { status: 'ready', error: '' })
  if (session?.byteSize > 0) setAiPendingRecording(session)
}

async function uploadStoredAiExamRecording() {
  const stored = await buildRecordingFile(aiExamRecordingKey())
  if (!stored?.file.size) return
  if (stored.file.size > MAX_RECORDING_UPLOAD_BYTES) {
    throw new Error(`录像大小为 ${formatRecordingSize(stored.file.size)}，超过服务器 100 MB 限制，已保留在本机浏览器中`)
  }
  aiExamRecording.pending = true
  aiExamRecording.byteSize = stored.file.size
  if (stored.session.limitReached) markAiRecordingLimitReached(stored.session)
  await updateRecordingSession(aiExamRecordingKey(), { status: 'uploading', error: '' })
  await interviewApi.uploadAiExamRecording(stored.session.processId || sessionForm.processId, stored.file)
  await deleteRecordingSession(aiExamRecordingKey())
  aiExamRecording.pending = false
  aiExamRecording.byteSize = 0
  aiExamRecording.error = ''
  aiExamRecording.uploaded = true
  if (aiExamRecording.limitReached) aiExamRecording.notice = recordingLimitNotice(true)
  await reportAntiCheat('AI_RECORDING_UPLOADED', 'AI答题录像已上传')
  if (!componentDisposed) ElMessage.success('AI答题录像已上传')
}

async function retryAiExamRecordingUpload() {
  if (aiExamRecording.uploading) return
  aiExamRecording.uploading = true
  try {
    await uploadStoredAiExamRecording()
  } catch (error) {
    await markAiRecordingUploadFailed(error)
    if (!componentDisposed) fail(error)
  } finally {
    aiExamRecording.uploading = false
  }
}

function setAiPendingRecording(session, error = session.error || '') {
  aiExamRecording.pending = Boolean(session?.byteSize)
  aiExamRecording.byteSize = session?.byteSize || 0
  aiExamRecording.error = error
  if (session?.limitReached) markAiRecordingLimitReached(session)
}

function markAiRecordingLimitReached(session) {
  aiExamRecording.limitReached = true
  aiExamRecording.pending = Boolean(session?.byteSize)
  aiExamRecording.byteSize = session?.byteSize || aiExamRecording.byteSize
  aiExamRecording.notice = recordingLimitNotice(false)
}

function recordingLimitNotice(uploaded) {
  const limit = formatRecordingSize(RECORDING_STOP_THRESHOLD_BYTES)
  return uploaded
    ? `录像达到 ${limit} 安全阈值后已自动停止并上传，停止后的内容未继续录制`
    : `录像已达到 ${limit} 安全阈值，可用部分已保存在本机；请完成上传，停止后的内容不会继续录制`
}

async function markAiRecordingUploadFailed(error) {
  const message = error?.message || '录像上传失败'
  const session = await updateRecordingSession(aiExamRecordingKey(), { status: 'failed', error: message }).catch(() => null)
  setAiPendingRecording(session || { byteSize: aiExamRecording.byteSize }, message)
}

function handleFullscreenChange() {
  antiCheat.fullscreen = document.fullscreenElement === document.documentElement
  if (antiCheat.fullscreen) {
    antiCheat.hasEnteredFullscreen = true
    return
  }
  if (shouldReportSwitch()) {
    reportSwitchEvent('FULLSCREEN_EXIT', `退出全屏，当前本地累计${antiCheat.switchCount + 1}次`)
  }
}

function handleVisibilityChange() {
  if (document.hidden && shouldReportSwitch()) {
    reportSwitchEvent('TAB_HIDDEN', `页面隐藏/切屏，当前本地累计${antiCheat.switchCount + 1}次`)
  }
}

function handleWindowBlur() {
  if (shouldReportSwitch()) {
    reportSwitchEvent('WINDOW_BLUR', `窗口失焦/切屏，当前本地累计${antiCheat.switchCount + 1}次`)
  }
}

function reportSwitchEvent(eventType, detail) {
  const now = Date.now()
  if (now - lastSwitchReportAt < 1000) return
  lastSwitchReportAt = now
  antiCheat.switchCount += 1
  reportAntiCheat(eventType, detail)
}

function handleRestrictedShortcut(event) {
  if (!runtimeConfig.disableDevtoolsShortcuts) return
  const key = event.key?.toLowerCase()
  const blocked = event.key === 'F12'
    || (event.ctrlKey && event.shiftKey && ['i', 'j', 'c'].includes(key))
    || (event.metaKey && event.altKey && ['i', 'j', 'c'].includes(key))
    || (event.ctrlKey && key === 'u')
    || (isAiExamInProgress() && (event.ctrlKey || event.metaKey) && ['c', 'v', 'x'].includes(key))
  if (!blocked) return
  event.preventDefault()
  event.stopPropagation()
  ElMessage.warning(isAiExamInProgress() && ['c', 'v', 'x'].includes(key) ? 'AI答题期间禁止复制、剪切和粘贴' : '面试期间已禁用开发者工具快捷键')
}

function handleContextMenu(event) {
  if (!runtimeConfig.disableDevtoolsShortcuts) return
  event.preventDefault()
}

function shouldReportSwitch() {
  return antiCheat.hasEnteredFullscreen && processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'IN_PROGRESS' && processSummary.value?.overallStatus === 'IN_PROGRESS'
}

function notifyAiFinishedIfNeeded() {
  if (isAiTerminal.value && !antiCheat.aiEndNotified) {
    antiCheat.aiEndNotified = true
    currentQuestion.value = null
    clearAiRefresh()
    stopAndUploadAiExamRecording().catch(fail)
    ElMessageBox.alert(processSummary.value.processStatusView || 'AI面试已结束，请留意报名记录中的状态。', '面试结束', { confirmButtonText: '知道了' })
  }
}

function isAiExamInProgress() {
  return processSummary.value?.currentStage === 'AI' && processSummary.value?.stageStatus === 'IN_PROGRESS' && processSummary.value?.overallStatus === 'IN_PROGRESS'
}

function handleClipboardBlocked(event) {
  if (!isAiExamInProgress()) return
  event.preventDefault()
  event.stopPropagation()
  reportAntiCheat('CLIPBOARD_BLOCKED', `AI答题期间阻止${event.type}操作`)
  ElMessage.warning('AI答题期间禁止复制、剪切、粘贴和拖拽')
}
async function joinVideo() {
  if (joiningVideo.value || componentDisposed) return
  await restoreCandidateVideoRecording()
  if (videoRecording.pending) {
    ElMessage.warning('检测到尚未上传的面试录像，请先重试上传后再加入视频面试')
    return
  }
  joiningVideo.value = true
  try {
    await disconnectVideo()
    if (componentDisposed) return
    const joinedSession = (await interviewApi.intervieweeJoin(sessionForm.processId)).data
    if (componentDisposed) return
    videoProcessStageId = joinedSession?.processStageId || processSummary.value?.processStageId || null
    const stream = await requestCameraAndMicrophone()
    if (componentDisposed) {
      stream.getTracks().forEach((track) => track.stop())
      return
    }
    localStream = stream
    localVideo.value.srcObject = localStream
    playVideo(localVideo.value)
    const iceServers = await loadIceServers()
    if (componentDisposed) {
      await disconnectVideo({ uploadRecording: false })
      return
    }
    peer = createPeerConnection(iceServers)
    addedHrIce = new Set()
    pendingHrIce = []
    localStream.getTracks().forEach((track) => peer.addTrack(track, localStream))
    remoteStream = null
    peer.ontrack = (event) => { remoteStream = attachRemoteTrack(remoteVideo.value, event, remoteStream) }
    peer.onconnectionstatechange = () => {
      if (['failed', 'disconnected'].includes(peer.connectionState)) {
        ElMessage.warning('远端视频连接不稳定，请双方保持页面打开，必要时重新加入视频面')
      }
    }
    peer.onicecandidate = async (event) => {
      if (event.candidate) {
        await interviewApi.addIntervieweeIce(sessionForm.processId, { iceCandidate: JSON.stringify(event.candidate) })
      }
    }
    if (componentDisposed) return
    pollTimer = setInterval(async () => {
      if (componentDisposed || !peer || videoPollInProgress) return
      videoPollInProgress = true
      try {
        const state = (await interviewApi.getVideoState(sessionForm.processId)).data
        if (componentDisposed || !peer) return
        if (state.offerSdp && !peer.currentRemoteDescription) {
          await peer.setRemoteDescription(JSON.parse(state.offerSdp))
          await flushPendingHrIce()
          const answer = await peer.createAnswer()
          await peer.setLocalDescription(answer)
          await interviewApi.submitVideoAnswer(sessionForm.processId, { answerSdp: JSON.stringify(answer) })
        }
        if (state.hrIceCandidates) {
          const candidates = state.hrIceCandidates.split('\n').filter(Boolean)
          for (const item of candidates) {
            if (!addedHrIce.has(item)) {
              addedHrIce.add(item)
              await addHrIceCandidate(item)
            }
          }
        }
        if (state.sessionStatus === 'RECORDING') {
          void startRecordingIfNeeded().catch((error) => { if (!componentDisposed) fail(error) })
        }
        if (shouldHandleRecordingEnd(state)) {
          handledRecordingEndSignal = recordingEndSignalKey(state)
          clearInterval(pollTimer)
          pollTimer = null
          scheduleRecordingStop(state.recordingEndRequestedAt)
        }
      } catch (error) {
        if (!componentDisposed) console.warn('同步视频状态失败', error)
      } finally {
        videoPollInProgress = false
      }
    }, 1000)
    if (!componentDisposed) ElMessage.success('已加入视频面，等待HR就绪后同步开始录制')
  } catch (error) {
    await disconnectVideo({ uploadRecording: false })
    if (!componentDisposed) ElMessage.error(buildMediaErrorMessage(error))
  } finally {
    joiningVideo.value = false
  }
}

function uploadVideoRecordingAfterTerminalHeartbeat(previousSummary, summary) {
  const videoStageEnded = previousSummary?.currentStage === 'VIDEO' && (
    summary.overallStatus !== 'IN_PROGRESS'
    || summary.currentStage !== 'VIDEO'
    || summary.stageStatus !== 'IN_PROGRESS'
  )
  if (!videoStageEnded || (!recorder && !videoRecording.pending)) return
  void stopAndUploadRecording().catch((error) => { if (!componentDisposed) fail(error) })
}
async function stopRecording() {
  try {
    clearInterval(pollTimer)
    pollTimer = null
    const response = await interviewApi.completeIntervieweeVideo(sessionForm.processId)
    handledRecordingEndSignal = recordingEndSignalKey(response.data || {})
    scheduleRecordingStop(response.data?.recordingEndRequestedAt)
  } catch (error) { fail(error) }
}

function recordingEndSignalKey(state) {
  return state.recordingEndRequestedAt || (state.sessionStatus === 'END_REQUESTED' ? 'END_REQUESTED' : '')
}

function shouldHandleRecordingEnd(state) {
  const signal = recordingEndSignalKey(state)
  return signal && signal !== handledRecordingEndSignal
}

function scheduleRecordingStop(endAt) {
  clearTimeout(recordingEndTimer)
  recordingEndTimer = null
  if (componentDisposed) return
  const delay = Math.max(new Date(endAt || Date.now()).getTime() - Date.now(), 0)
  recordingEndTimer = setTimeout(async () => {
    recordingEndTimer = null
    if (componentDisposed) return
    try {
      await stopAndUploadRecording()
      if (componentDisposed) return
      await disconnectVideo({ uploadRecording: false })
    } catch (error) {
      if (!componentDisposed) fail(error)
    }
  }, delay)
}

async function startRecordingIfNeeded() {
  if (componentDisposed || videoRecording.starting || videoRecording.pending || videoRecording.limitReached || videoRecording.error || !localStream || (recorder && recorder.state !== 'inactive')) return
  videoRecording.starting = true
  try {
    const session = await beginRecordingSession(candidateVideoRecordingKey(), {
      kind: 'candidate-video',
      processId: sessionForm.processId,
      processStageId: videoProcessStageId,
      fileName: `interviewee-${sessionForm.processId}.webm`,
      contentType: 'video/webm',
    })
    if (session.byteSize > 0) {
      setPendingVideoRecording(session, '上次录像尚未上传，请先重试上传')
      return
    }
    if (componentDisposed || !localStream) return
    recorder = createMediaRecorder(localStream)
    recorderStopPromise = null
    recordingChunkWrites = Promise.resolve()
    recordingChunkWriteError = null
    recordingPendingWriteBytes = 0
    const currentRecorder = recorder
    recorder.ondataavailable = (event) => queueVideoRecordingChunk(event.data, currentRecorder)
    recorder.onstop = () => {
      if (videoRecording.limitReached) void stopAndUploadRecording().catch((error) => { if (!componentDisposed) fail(error) })
    }
    recorder.start(1000)
    videoRecording.byteSize = 0
    videoRecording.limitReached = false
    videoRecording.notice = ''
    videoRecording.error = ''
    ElMessage.success('双方已进入视频面，录制已同步开始')
  } catch (error) {
    videoRecording.error = `无法启动可靠录像：${error.message}`
    throw error
  } finally {
    videoRecording.starting = false
  }
}

function retryVideoRecordingStart() {
  videoRecording.error = ''
  void startRecordingIfNeeded().catch((error) => { if (!componentDisposed) fail(error) })
}

async function stopAndUploadRecording() {
  if (recordingUploadPromise) return recordingUploadPromise
  videoRecording.uploading = true
  recordingUploadPromise = (async () => {
    try {
      await stopVideoRecorderToStore()
      await uploadStoredVideoRecording()
    } catch (error) {
      await markVideoRecordingUploadFailed(error)
      throw error
    }
  })()
  try {
    await recordingUploadPromise
  } finally {
    recordingUploadPromise = null
    videoRecording.uploading = false
  }
}

function queueVideoRecordingChunk(data, currentRecorder) {
  if (!data?.size) return
  recordingPendingWriteBytes += data.size
  if (recordingPendingWriteBytes >= RECORDING_WRITE_HIGH_WATER_BYTES && currentRecorder.state === 'recording') currentRecorder.pause()
  recordingChunkWrites = recordingChunkWrites.then(async () => {
    try {
      if (recordingChunkWriteError) return
      const result = await appendRecordingChunk(candidateVideoRecordingKey(), data)
      videoRecording.byteSize = result.session?.byteSize || videoRecording.byteSize
      if (result.limitReached) {
        markVideoRecordingLimitReached(result.session)
        if (currentRecorder.state !== 'inactive') currentRecorder.stop()
      }
    } catch (error) {
      recordingChunkWriteError = error
      videoRecording.error = `录像暂存失败：${error.message}`
      if (currentRecorder.state !== 'inactive') currentRecorder.stop()
    } finally {
      recordingPendingWriteBytes = Math.max(0, recordingPendingWriteBytes - data.size)
      if (!recordingChunkWriteError && !videoRecording.limitReached && recordingPendingWriteBytes <= RECORDING_WRITE_LOW_WATER_BYTES && currentRecorder.state === 'paused') currentRecorder.resume()
    }
  })
}

async function stopVideoRecorderToStore() {
  if (recorder && recorder.state !== 'inactive') {
    const currentRecorder = recorder
    recorderStopPromise = new Promise((resolve) => {
      const previousStop = currentRecorder.onstop
      currentRecorder.onstop = (event) => { previousStop?.(event); resolve() }
      currentRecorder.stop()
    })
  }
  if (recorderStopPromise) await recorderStopPromise
  recorderStopPromise = null
  recorder = null
  await recordingChunkWrites
  if (recordingChunkWriteError) throw recordingChunkWriteError
  const session = await updateRecordingSession(candidateVideoRecordingKey(), { status: 'ready', error: '' })
  if (session?.byteSize > 0) setPendingVideoRecording(session)
}

async function uploadStoredVideoRecording() {
  const stored = await buildRecordingFile(candidateVideoRecordingKey())
  if (!stored?.file.size) return
  if (stored.file.size > MAX_RECORDING_UPLOAD_BYTES) {
    throw new Error(`录像大小为 ${formatRecordingSize(stored.file.size)}，超过服务器 100 MB 限制，已保留在本机浏览器中`)
  }
  setPendingVideoRecording(stored.session)
  await updateRecordingSession(candidateVideoRecordingKey(), { status: 'uploading', error: '' })
  await interviewApi.uploadVideoRecording(
    stored.session.processId || sessionForm.processId,
    stored.file,
    stored.session.processStageId || videoProcessStageId,
  )
  await deleteRecordingSession(candidateVideoRecordingKey())
  videoRecording.pending = false
  videoRecording.byteSize = 0
  videoRecording.error = ''
  if (videoRecording.limitReached) videoRecording.notice = recordingLimitNotice(true)
  if (!componentDisposed) ElMessage.success('面试者录制已上传')
}

async function retryVideoRecordingUpload() {
  if (videoRecording.uploading) return
  videoRecording.uploading = true
  try {
    await uploadStoredVideoRecording()
  } catch (error) {
    await markVideoRecordingUploadFailed(error)
    if (!componentDisposed) fail(error)
  } finally {
    videoRecording.uploading = false
  }
}

function setPendingVideoRecording(session, error = session.error || '') {
  videoRecording.pending = Boolean(session?.byteSize)
  videoRecording.byteSize = session?.byteSize || 0
  videoRecording.error = error
  if (session?.limitReached) markVideoRecordingLimitReached(session)
  if (session?.processStageId) videoProcessStageId = session.processStageId
}

function markVideoRecordingLimitReached(session) {
  videoRecording.limitReached = true
  videoRecording.pending = Boolean(session?.byteSize)
  videoRecording.byteSize = session?.byteSize || videoRecording.byteSize
  videoRecording.notice = recordingLimitNotice(false)
}

async function markVideoRecordingUploadFailed(error) {
  const message = error?.message || '录像上传失败'
  const session = await updateRecordingSession(candidateVideoRecordingKey(), { status: 'failed', error: message }).catch(() => null)
  setPendingVideoRecording(session || { byteSize: videoRecording.byteSize }, message)
}

async function disconnectVideo({ uploadRecording = true } = {}) {
  try {
    if (uploadRecording) await stopAndUploadRecording()
  } finally {
    clearInterval(pollTimer)
    clearTimeout(recordingEndTimer)
    pollTimer = null
    recordingEndTimer = null
    peer?.getSenders?.().forEach((sender) => sender.track?.stop())
    peer?.close()
    peer = null
    recorder = null
    handledRecordingEndSignal = ''
    videoPollInProgress = false
    videoProcessStageId = null
    localStream?.getTracks().forEach((track) => track.stop())
    localStream = null
    remoteStream = null
    pendingHrIce = []
    if (localVideo.value) localVideo.value.srcObject = null
    if (remoteVideo.value) remoteVideo.value.srcObject = null
  }
}

async function addHrIceCandidate(item) {
  if (!peer?.remoteDescription) {
    pendingHrIce.push(item)
    return
  }
  try {
    await peer.addIceCandidate(JSON.parse(item))
  } catch (error) {
    console.warn('添加HR ICE失败', error)
  }
}

async function flushPendingHrIce() {
  const items = pendingHrIce
  pendingHrIce = []
  for (const item of items) {
    await addHrIceCandidate(item)
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

async function loadRuntimeConfig() {
  try {
    const response = await interviewApi.getRuntimeConfig()
    runtimeConfig.disableDevtoolsShortcuts = response.data?.disableDevtoolsShortcuts !== false
  } catch {
    runtimeConfig.disableDevtoolsShortcuts = true
  }
}

async function restorePendingRecordings() {
  await Promise.all([restoreAiExamRecording(), restoreCandidateVideoRecording()])
}

async function restoreAiExamRecording() {
  try {
    const session = await getRecordingSession(aiExamRecordingKey())
    if (session?.byteSize > 0) setAiPendingRecording(session)
  } catch (error) {
    aiExamRecording.error = error.message
  }
}

async function restoreCandidateVideoRecording() {
  try {
    const session = await getRecordingSession(candidateVideoRecordingKey())
    if (session?.byteSize > 0) setPendingVideoRecording(session)
  } catch (error) {
    videoRecording.error = error.message
  }
}

async function preserveAiExamRecording() {
  try {
    await stopAiExamRecorderToStore()
  } catch (error) {
    await markAiRecordingUploadFailed(error)
  }
}

async function preserveVideoRecording() {
  try {
    await stopVideoRecorderToStore()
  } catch (error) {
    await markVideoRecordingUploadFailed(error)
  }
}

function handlePageHide() {
  void preserveAiExamRecording()
  void preserveVideoRecording()
}

onBeforeUnmount(() => {
  componentDisposed = true
  clearTimeout(antiCheatRetryTimer)
  antiCheatRetryTimer = null
  clearInterval(heartbeatTimer)
  heartbeatTimer = null
  clearTimeout(heartbeatDataRefreshTimer)
  heartbeatDataRefreshTimer = null
  aiAnswerAbortController?.abort()
  clearAiRefresh()
  document.removeEventListener('keydown', handleRestrictedShortcut, true)
  document.removeEventListener('contextmenu', handleContextMenu, true)
  document.removeEventListener('fullscreenchange', handleFullscreenChange)
  document.removeEventListener('visibilitychange', handleVisibilityChange)
  window.removeEventListener('blur', handleWindowBlur)
  document.removeEventListener('copy', handleClipboardBlocked, true)
  document.removeEventListener('cut', handleClipboardBlocked, true)
  document.removeEventListener('paste', handleClipboardBlocked, true)
  document.removeEventListener('drop', handleClipboardBlocked, true)
  window.removeEventListener('pagehide', handlePageHide)
  void preserveAiExamRecording()
  void preserveVideoRecording()
  stopAiExamStream()
  void disconnectVideo({ uploadRecording: false }).catch(() => {})
})

onMounted(async () => {
  await loadRuntimeConfig()
  document.addEventListener('keydown', handleRestrictedShortcut, true)
  document.addEventListener('contextmenu', handleContextMenu, true)
  document.addEventListener('fullscreenchange', handleFullscreenChange)
  document.addEventListener('visibilitychange', handleVisibilityChange)
  window.addEventListener('blur', handleWindowBlur)
  document.addEventListener('copy', handleClipboardBlocked, true)
  document.addEventListener('cut', handleClipboardBlocked, true)
  document.addEventListener('paste', handleClipboardBlocked, true)
  document.addEventListener('drop', handleClipboardBlocked, true)
  window.addEventListener('pagehide', handlePageHide)
  if (!sessionForm.processId) {
    ElMessage.warning('请从面试者首页选择报名记录进入面试')
    router.push('/user')
    return
  }
  await restorePendingRecordings()
  await loadProcessRecords()
  syncAiAutoRefresh()
  if (processSummary.value?.currentStage === 'AI' && processSummary.value?.overallStatus === 'IN_PROGRESS') {
    ElMessage.info('AI面试需要全屏答题并开启摄像头/麦克风录像，切屏和复制粘贴会被记录')
  }
})
</script>

<style scoped>
.interview-shell { min-height: 100vh; padding: 28px; background: #eef2ef; }
.interview-workspace { width: min(1280px, 100%); margin: 0 auto; }
.workspace-header, .video-workspace-head { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.workspace-header { margin-bottom: 22px; }
.workspace-header h1, .video-workspace-head h2 { margin: 6px 0 0; font-size: 30px; line-height: 1.2; letter-spacing: 0; }
.header-actions { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 10px; }
.stage-badge { padding: 8px 12px; border-radius: 999px; color: var(--primary-dark); background: #dcece5; border: 1px solid #bad5c8; font-size: 13px; font-weight: 700; }
.status-rail { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin-bottom: 16px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); box-shadow: var(--shadow-card); }
.status-rail div { min-width: 0; padding: 15px 18px; border-right: 1px solid var(--border); }
.status-rail div:last-child { border-right: 0; }
.status-rail span, .exam-facts dt { display: block; color: var(--text-muted); font-size: 12px; }
.status-rail strong { display: block; margin-top: 5px; overflow-wrap: anywhere; font-size: 16px; }
.ai-workspace { display: grid; grid-template-columns: 260px minmax(0, 1fr); gap: 16px; align-items: start; min-width: 0; }
.exam-sidebar { position: sticky; top: 16px; display: grid; gap: 12px; }
.sidebar-section, .answer-workspace, .video-workspace { min-width: 0; border: 1px solid var(--border); border-radius: 8px; background: var(--surface); box-shadow: var(--shadow-card); }
.sidebar-section { padding: 14px; }
.section-heading, .question-meta, .history-heading, .composer-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-heading { margin-bottom: 12px; font-size: 13px; font-weight: 700; }
.recording-dot { width: 9px; height: 9px; border-radius: 999px; background: #a8b3ad; }
.recording-dot.active { background: var(--danger); box-shadow: 0 0 0 4px rgba(220, 38, 38, 0.12); }
.proctor-video { display: block; width: 100%; aspect-ratio: 4 / 3; object-fit: cover; border-radius: 6px; background: #111; }
.recording-label { display: block; margin-top: 10px; color: var(--ink-soft); font-size: 13px; }
.recording-recovery { display: flex; align-items: flex-start; justify-content: space-between; gap: 10px; margin-top: 10px; padding: 10px; border: 1px solid #e4c987; border-radius: 6px; background: #fff9eb; color: #855b09; }
.recording-recovery small { min-width: 0; overflow-wrap: anywhere; line-height: 1.5; }
.recording-recovery :deep(.el-button) { flex: 0 0 auto; margin-left: 0; }
.video-recording-recovery { margin-top: 14px; }
.compact-section { padding-bottom: 10px; }
.round-track { display: flex; flex-wrap: wrap; gap: 7px; padding: 4px 0 14px; border-bottom: 1px solid var(--border); }
.round-track span { display: grid; width: 30px; height: 30px; place-items: center; border: 1px solid var(--border-strong); border-radius: 999px; color: var(--text-muted); background: var(--surface); font-size: 12px; font-weight: 700; }
.round-track span.done { color: #fff; border-color: var(--primary); background: var(--primary); }
.round-track span.current { color: var(--primary-dark); border: 2px solid var(--primary); background: var(--primary-soft); }
.exam-facts { display: grid; gap: 0; margin: 8px 0 0; }
.exam-facts div { display: flex; justify-content: space-between; gap: 12px; padding: 7px 0; }
.exam-facts dd { margin: 0; color: var(--ink-soft); font-size: 13px; font-weight: 700; }
.wide-action { width: 100%; margin-left: 0 !important; }
.answer-workspace { position: relative; display: flex; min-width: 0; flex-direction: column; padding: 24px; overflow: hidden; }
.ai-status-card { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px; align-items: center; margin-bottom: 22px; padding: 13px 15px; border: 1px solid #cfe1d8; border-radius: 6px; background: #f2f8f5; }
.ai-status-card.busy { border-color: #d7c69b; background: #fff9eb; }
.ai-status-card.terminal { border-color: var(--border); background: var(--surface-soft); }
.ai-status-card strong, .ai-status-card small { display: block; }
.ai-status-card small { margin-top: 3px; color: var(--text-muted); line-height: 1.5; }
.status-dot { width: 9px; height: 9px; border-radius: 999px; background: var(--primary); box-shadow: 0 0 0 4px var(--primary-ring); }
.ai-status-card.busy .status-dot { background: var(--warning); box-shadow: 0 0 0 4px rgba(217, 119, 6, 0.12); }
.current-question { min-width: 0; padding: 4px 2px 8px; }
.question-meta { justify-content: flex-start; }
.question-meta span, .question-meta em { padding: 6px 9px; border-radius: 4px; font-size: 12px; font-style: normal; font-weight: 700; }
.question-meta span { color: #fff; background: var(--ink); }
.question-meta em { color: var(--primary-dark); background: var(--primary-soft); }
.current-question h2 { max-width: 920px; margin: 18px 0 24px; overflow-wrap: anywhere; font-size: 23px; line-height: 1.55; letter-spacing: 0; }
.answer-composer { display: flex; min-width: 0; flex-direction: column; overflow: hidden; border: 1px solid var(--border-strong); border-radius: 8px; background: var(--surface); }
.answer-composer :deep(.el-textarea__inner) { min-height: 176px !important; padding: 16px; border: 0; border-radius: 0; box-shadow: none; resize: vertical; line-height: 1.8; }
.composer-footer { flex: 0 0 auto; padding: 10px 12px; border-top: 1px solid var(--border); background: var(--surface-soft); }
.composer-footer > span { color: var(--text-muted); font-size: 12px; }
.processing-state { display: grid; grid-template-columns: auto minmax(0, 1fr); gap: 12px; align-items: center; margin-top: 16px; padding: 14px; border: 1px solid #ead9ad; border-radius: 6px; background: #fff9eb; }
.processing-state p { margin: 4px 0 0; color: var(--text-muted); }
.processing-spinner { width: 18px; height: 18px; border: 2px solid #ead9ad; border-top-color: var(--warning); border-radius: 999px; animation: spin 0.8s linear infinite; }
.ai-stream-log { grid-column: 1 / -1; max-height: 180px; overflow: auto; padding: 12px; border-radius: 6px; background: var(--surface); color: var(--ink-soft); line-height: 1.7; white-space: pre-wrap; }
.answer-history { margin-top: 30px; padding-top: 24px; border-top: 1px solid var(--border); }
.history-heading { margin-bottom: 10px; }
.history-heading h2 { margin: 0; font-size: 17px; }
.history-heading span { color: var(--text-muted); font-size: 13px; }
.history-item { border-bottom: 1px solid var(--border); }
.history-item:first-of-type { border-top: 1px solid var(--border); }
.history-item summary { display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; gap: 10px; align-items: center; padding: 15px 4px; cursor: pointer; list-style: none; }
.history-item summary::-webkit-details-marker { display: none; }
.history-item summary > span { color: var(--primary); font-size: 13px; font-weight: 800; }
.history-item summary > strong { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.history-item summary > em { color: var(--ink-soft); font-size: 13px; font-style: normal; font-weight: 700; }
.history-content { display: grid; gap: 12px; padding: 0 4px 18px 46px; }
.history-content > div { padding: 14px; border-left: 3px solid var(--border-strong); background: var(--surface-soft); }
.history-content > div.feedback { border-left-color: var(--primary); background: #f2f8f5; }
.history-content span { color: var(--text-muted); font-size: 12px; font-weight: 700; }
.history-content p { margin: 7px 0 0; line-height: 1.75; }
.terminal-state, .loading-state { padding: 42px 26px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface-soft); text-align: center; }
.terminal-state strong { font-size: 20px; }
.terminal-state p { margin: 8px 0 0; color: var(--text-muted); line-height: 1.7; }
.terminal-state.rejected { border-color: #edcaca; background: #fff7f7; }
.video-workspace { padding: 22px; }
.video-workspace-head { margin-bottom: 18px; }
.video-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.video-box { min-width: 0; padding: 10px; border: 1px solid var(--border); border-radius: 8px; background: var(--surface-soft); }
.video-box span { display: block; margin-bottom: 8px; color: var(--text-muted); font-size: 13px; }
.video-box video { display: block; width: 100%; aspect-ratio: 16 / 10; object-fit: cover; border-radius: 6px; background: #111; }
.retry-message { margin: 12px 0 0; color: var(--danger); font-size: 13px; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 900px) {
  .interview-shell { padding: 16px 12px 28px; }
  .workspace-header, .video-workspace-head { align-items: flex-start; flex-direction: column; }
  .header-actions { justify-content: flex-start; }
  .workspace-header h1 { font-size: 26px; }
  .status-rail { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .status-rail div:nth-child(2) { border-right: 0; }
  .status-rail div:nth-child(-n + 2) { border-bottom: 1px solid var(--border); }
  .ai-workspace { grid-template-columns: 1fr; }
  .exam-sidebar { position: static; grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr); }
  .exam-sidebar .wide-action { grid-column: 1 / -1; }
  .answer-workspace { padding: 18px; }
  .current-question h2 { font-size: 20px; }
  .video-grid { grid-template-columns: 1fr; }
}
@media (max-width: 560px) {
  .status-rail { grid-template-columns: 1fr; }
  .status-rail div, .status-rail div:nth-child(2) { border-right: 0; border-bottom: 1px solid var(--border); }
  .status-rail div:last-child { border-bottom: 0; }
  .exam-sidebar { grid-template-columns: 1fr; }
  .history-item summary { grid-template-columns: 36px minmax(0, 1fr); }
  .history-item summary > em { grid-column: 2; }
  .history-content { padding-left: 0; }
  .composer-footer { align-items: stretch; flex-direction: column; }
  .composer-footer :deep(.el-button) { width: 100%; margin-left: 0; }
}
</style>
