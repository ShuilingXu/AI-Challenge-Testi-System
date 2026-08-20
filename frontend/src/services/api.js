import axios from 'axios'
import { clearSession, readSessionToken } from '../utils/session'

const apiBaseUrl = import.meta.env.VITE_API_BASE_URL || '/api'
const request = axios.create({
  baseURL: apiBaseUrl,
  timeout: 10000,
  withCredentials: true,
  xsrfCookieName: 'AUTOHR_CSRF',
  xsrfHeaderName: 'X-CSRF-Token',
})

request.interceptors.request.use((config) => {
  const token = readSessionToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => response.data,
  (error) => {
    if (error.response?.status === 401) {
      clearSession()
      if (window.location.pathname !== '/login') {
        window.location.replace('/login')
      }
    }
    const message = error.response?.data?.message || error.message || '请求失败'
    const wrapped = new Error(message)
    wrapped.code = error.code
    wrapped.status = error.response?.status
    return Promise.reject(wrapped)
  },
)

async function openAuthorizedFile(path, params = {}) {
  const normalizedPath = path.startsWith('/api') ? path.slice(4) : path
  const popup = window.open('about:blank', '_blank')
  if (!popup) throw new Error('浏览器阻止了文件预览窗口，请允许弹窗后重试')
  try {
    popup.opener = null
    const downloadUrlResponse = await request.get(`${normalizedPath}/download-url`, { params })
    const externalUrl = downloadUrlResponse?.data?.url
    if (externalUrl) {
      popup.location.replace(externalUrl)
      return
    }
    const blob = await request.get(normalizedPath, { params, responseType: 'blob' })
    const objectUrl = URL.createObjectURL(blob)
    const releaseObjectUrl = releaseWhenPreviewCloses(popup, objectUrl)
    try {
      popup.location.replace(objectUrl)
    } catch (error) {
      releaseObjectUrl()
      throw error
    }
  } catch (error) {
    popup.close()
    throw error
  }
}

function releaseWhenPreviewCloses(popup, objectUrl) {
  let released = false
  let monitor = null
  const release = () => {
    if (released) return
    released = true
    if (monitor) window.clearInterval(monitor)
    URL.revokeObjectURL(objectUrl)
  }
  monitor = window.setInterval(() => {
    if (popup.closed) {
      release()
      return
    }
    try {
      const currentUrl = popup.location.href
      if (currentUrl !== 'about:blank' && currentUrl !== objectUrl) release()
    } catch {
      release()
    }
  }, 1000)
  return release
}

const defaultPageParams = { page: 1, pageSize: 200 }

async function requestPage(path, params) {
  const pageParams = { ...defaultPageParams, ...params }
  const response = await request.get(path, { params: pageParams })
  const pagination = response?.data
  if (Array.isArray(pagination)) return response
  const items = pagination?.items || []
  return { ...response, data: items, pagination: { ...pagination, loaded: items.length } }
}

export const authApi = {
  getCaptcha() { return request.get('/auth/captcha') },
  login(payload) { return request.post('/auth/login', payload) },
  register(payload) { return request.post('/auth/register', payload) },
  sendRegisterCode(payload) { return request.post('/auth/register/code', payload) },
  sendPasswordResetCode(payload) { return request.post('/auth/password-reset/code', payload) },
  resetPassword(payload) { return request.post('/auth/password-reset', payload) },
  getSession() { return request.get('/auth/me') },
  changePassword(payload) { return request.post('/auth/change-password', payload) },
  updateProfile(payload) { return request.post('/auth/profile', payload) },
  listUsers(params) { return requestPage('/auth/admin/users', params) },
  listAuditLogs(params) { return requestPage('/auth/admin/audit-logs', params) },
  updateUser(id, payload) { return request.post(`/auth/admin/users/${id}`, payload) },
  deleteUser(id) { return request.delete(`/auth/admin/users/${id}`) },
  logout() {
    return request.post('/auth/logout').finally(() => {
      clearSession()
    })
  },
}

export const schoolApi = {
  listPublicClasses() { return request.get('/exams/classes') },
  registerStudent(payload) { return request.post('/exams/student-registration', payload) },
  listClasses(params) { return request.get('/exams/admin/classes', { params }) },
  saveClass(payload) { return request.post('/exams/admin/classes', payload) },
  deleteClass(id) { return request.post(`/exams/admin/classes/${id}/delete`) },
  importClasses(file) { const form = new FormData(); form.append('file', file); return request.post('/exams/admin/classes/import', form, { headers: { 'Content-Type': 'multipart/form-data' } }) },
  listStudents(params) { return request.get('/exams/admin/students', { params }) },
  saveStudent(payload) { return request.post('/exams/admin/students', payload) },
  importStudents(file) { const form = new FormData(); form.append('file', file); return request.post('/exams/admin/students/import', form, { headers: { 'Content-Type': 'multipart/form-data' } }) },
  listAdminExams() { return request.get('/exams/admin/exams') },
  saveExam(payload) { return request.post('/exams/admin/exams', payload) },
  deleteExam(id) { return request.post(`/exams/admin/exams/${id}/delete`) },
  analytics(params) { return request.get('/exams/admin/analytics', { params }) },
  getAdminAttempt(processId) { return request.get(`/exams/admin/attempts/${processId}`) },
  listStudentExams() { return request.get('/exams/student/exams') },
  startExam(examId) { return request.post(`/exams/student/exams/${examId}/start`) },
  listStudentAttempts() { return request.get('/exams/student/attempts') },
  getAttemptAnalysis(processId) { return request.get(`/exams/student/attempts/${processId}/analysis`) },
}

export const systemApi = {
  getConfig() { return request.get('/system/config') },
  saveConfig(payload) { return request.post('/system/config', payload) },
}

export const siteContentApi = {
  listPublished(params) { return requestPage('/site-content', params) },
  listAdmin(params) { return requestPage('/site-content/admin', params) },
  save(payload) { return request.post('/site-content/admin', payload) },
  remove(id) { return request.delete(`/site-content/admin/${id}`) },
}

export const siteSettingsApi = {
  getPublic() { return request.get('/site-settings') },
  getAdmin() { return request.get('/site-settings/admin') },
  save(payload) { return request.post('/site-settings/admin', payload) },
}

export const interviewApi = {
  getRuntimeConfig() { return request.get('/interview/runtime-config') },
  getIceServers() { return request.get('/interview/ice-servers') },
  saveKnowledgeBase(payload) { return request.post('/interview/hr/knowledge-bases', payload) },
  listKnowledgeBases(params) { return requestPage('/interview/hr/knowledge-bases', params) },
  deleteKnowledgeBase(id) { return request.post(`/interview/hr/knowledge-bases/${id}/delete`) },
  saveKnowledgeItem(payload) { return request.post('/interview/hr/knowledge-items', payload) },
  importKnowledgeItems(knowledgeBaseId, file) {
    const formData = new FormData()
    formData.append('knowledgeBaseId', knowledgeBaseId)
    formData.append('file', file)
    return request.post('/interview/hr/knowledge-items/import-csv', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  listKnowledgeItems(params) { return requestPage('/interview/hr/knowledge-items', params) },
  deleteKnowledgeItem(id) { return request.post(`/interview/hr/knowledge-items/${id}/delete`) },
  saveJobKnowledgeWeight(payload) { return request.post('/interview/hr/job-knowledge-weights', payload) },
  listJobKnowledgeWeights(params) { return requestPage('/interview/hr/job-knowledge-weights', params) },
  deleteJobKnowledgeWeight(id) { return request.post(`/interview/hr/job-knowledge-weights/${id}/delete`) },
  saveProcessTemplate(payload) { return request.post('/interview/hr/process-templates', payload) },
  listProcessTemplates(params) { return requestPage('/interview/hr/process-templates', params) },
  getProcessTemplate(id) { return request.get(`/interview/hr/process-templates/${id}`) },
  deleteProcessTemplate(id, version) { return request.post(`/interview/hr/process-templates/${id}/delete`, null, { params: { version } }) },
  startProcess(payload) { return request.post('/interview/hr/processes', payload) },
  listProcesses(params) { return requestPage('/interview/hr/processes', params) },
  getProcess(id) { return request.get(`/interview/hr/processes/${id}`) },
  getIntervieweeProcess(processId) { return request.get(`/interview/interviewee/process/${processId}`) },
  heartbeat(processId) { return request.post(`/interview/interviewee/heartbeat/${processId}`) },
  getNextAiQuestion(processId) { return request.get(`/interview/interviewee/next-question/${processId}`) },
  listAiRecords(params) { return requestPage('/interview/hr/ai-records', params) },
  listIntervieweeAiRecords(params) { return requestPage('/interview/interviewee/ai-records', params) },
  createVideoSession(processId) { return request.post(`/interview/hr/video-session/${processId}`) },
  publishVideoOffer(processId, payload) { return request.post(`/interview/hr/video-offer/${processId}`, payload) },
  getVideoState(processId) { return request.get(`/interview/interviewee/video-state/${processId}`) },
  getHrVideoState(processId) { return request.get(`/interview/hr/video-state/${processId}`) },
  submitVideoAnswer(processId, payload) { return request.post(`/interview/interviewee/video-answer/${processId}`, payload) },
  addHrIce(processId, payload) { return request.post(`/interview/hr/video-ice/${processId}`, payload) },
  addIntervieweeIce(processId, payload) { return request.post(`/interview/interviewee/video-ice/${processId}`, payload) },
  uploadVideoRecording(processId, file, processStageId) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('originalFileName', file.name)
    formData.append('contentType', file.type || 'video/webm')
    if (processStageId) formData.append('processStageId', processStageId)
    return request.post(`/interview/interviewee/video-recording/${processId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  uploadAiExamRecording(processId, file) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('originalFileName', file.name)
    formData.append('contentType', file.type || 'video/webm')
    return request.post(`/interview/interviewee/ai-recording/${processId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  reportAntiCheatEvent(payload) { return request.post('/interview/interviewee/anti-cheat-event', payload) },
  uploadHrVideoRecording(processId, file, processStageId) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('originalFileName', file.name)
    formData.append('contentType', file.type || 'video/webm')
    if (processStageId) formData.append('processStageId', processStageId)
    return request.post(`/interview/hr/video-recording/${processId}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
  openRecording(processId, processStageId) { return openAuthorizedFile(`/api/interview/hr/video-recording/${processId}`, { processStageId }) },
  openAiRecording(processId, processStageId) { return openAuthorizedFile(`/api/interview/hr/ai-recording/${processId}`, { processStageId }) },
  retryVideoSummary(processId) { return request.post(`/interview/hr/video-summary/${processId}/retry`) },
  intervieweeJoin(processId) { return request.post(`/interview/interviewee/video-join/${processId}`) },
  hrJoin(processId) { return request.post(`/interview/hr/video-join/${processId}`) },
  completeVideo(processId) { return request.post(`/interview/hr/video-complete/${processId}`) },
  completeIntervieweeVideo(processId) { return request.post(`/interview/interviewee/video-complete/${processId}`) },
  approveAi(processId, payload) { return request.post(`/interview/hr/approve-ai/${processId}`, payload) },
  approveVideo(processId, payload) { return request.post(`/interview/hr/approve-video/${processId}`, payload) },
  approveOnsite(processId, payload) { return request.post(`/interview/hr/approve-onsite/${processId}`, payload) },
  terminateProcess(processId, payload) { return request.post(`/interview/hr/terminate/${processId}`, payload) },
  updateProcessRemark(processId, payload) { return request.post(`/interview/hr/processes/${processId}/remark`, payload) },
  submitAiAnswer(payload) { return request.post('/interview/interviewee/ai-answer', payload, { timeout: 120000 }) },
  async submitAiAnswerStream(payload, onEvent, options = {}) {
    const streamUrl = `${apiBaseUrl.replace(/\/$/, '')}/interview/interviewee/ai-answer/stream`
    const abortController = new AbortController()
    const abortFromCaller = () => abortController.abort()
    if (options.signal?.aborted) abortFromCaller()
    else options.signal?.addEventListener('abort', abortFromCaller, { once: true })
    let timedOut = false
    let reader = null
    const timeoutId = window.setTimeout(() => {
      timedOut = true
      abortController.abort()
    }, 185000)
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    const dispatchChunk = (chunk) => {
      if (!chunk.trim()) return
      const streamEvent = { event: 'message', data: '' }
      chunk.split('\n').forEach((line) => {
        if (line.startsWith('event:')) streamEvent.event = line.slice(6).trim()
        if (line.startsWith('data:')) streamEvent.data += line.slice(5).replace(/^ /, '')
      })
      if (streamEvent.data) onEvent?.(streamEvent)
    }
    const dispatchBufferedEvents = (flush = false) => {
      const chunks = buffer.replace(/\r\n/g, '\n').split('\n\n')
      const tail = chunks.pop() || ''
      buffer = flush ? '' : tail
      chunks.forEach(dispatchChunk)
      if (flush && tail.trim()) dispatchChunk(tail)
    }

    try {
      const response = await fetch(streamUrl, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...csrfHeaders(),
        },
        credentials: 'include',
        body: JSON.stringify(payload),
        signal: abortController.signal,
      })
      if (!response.ok || !response.body) {
        throw new Error(await response.text() || '流式提交失败')
      }
      reader = response.body.getReader()
      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        dispatchBufferedEvents()
      }
      buffer += decoder.decode()
      dispatchBufferedEvents(true)
    } catch (error) {
      abortController.abort()
      if (timedOut) {
        const timeoutError = new Error('AI stream request timed out')
        timeoutError.code = 'ECONNABORTED'
        throw timeoutError
      }
      throw error
    } finally {
      window.clearTimeout(timeoutId)
      options.signal?.removeEventListener('abort', abortFromCaller)
      reader?.releaseLock()
    }
  },
}

function csrfHeaders() {
  const cookie = typeof document === 'undefined'
    ? ''
    : document.cookie.split('; ').find((item) => item.startsWith('AUTOHR_CSRF='))
  const token = cookie ? decodeURIComponent(cookie.slice('AUTOHR_CSRF='.length)) : ''
  return token ? { 'X-CSRF-Token': token } : {}
}
