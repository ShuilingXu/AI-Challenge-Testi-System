import { reactive, ref } from 'vue'
import { schoolApi } from '../services/api'
import { deleteRecordingSession, loadExamRecordingSegments, saveExamRecordingSegment } from '../utils/recordingStore'

export function useExamMonitoring(processId) {
  const cameraPreview = ref(null)
  const ready = ref(false)
  const error = ref('')
  const recording = ref(false)
  const pendingCount = ref(0)
  const uploading = ref(false)
  let cameraStream = null
  let screenStream = null
  let outputStream = null
  let recorder = null
  let segmentTimer = null
  let drawTimer = null
  let screenVideo = null
  let canvas = null
  let nextSegmentNo = 0
  let uploadQueue = Promise.resolve()
  let uploadFailed = false
  const pendingUploads = []
  let stopping = false
  let captureGeneration = 0
  const policy = reactive({ cameraEnabled: false, screenRecordingEnabled: false })
  let prepared = false

  async function prepare() {
    const result = (await schoolApi.monitoringPolicy(processId)).data
    Object.assign(policy, { cameraEnabled: Number(result.cameraEnabled) === 1, screenRecordingEnabled: Number(result.screenRecordingEnabled) === 1 })
    nextSegmentNo = Number(result.nextSegmentNo) || 0
    const restored = await loadExamRecordingSegments(processId)
    pendingUploads.push(...restored)
    pendingCount.value = pendingUploads.length
    nextSegmentNo = Math.max(nextSegmentNo, ...restored.map(item => item.segmentNo + 1))
    prepared = true
    if (pendingUploads.length) void retryUploads()
  }

  function stopTracks() {
    cameraStream?.getTracks().forEach(track => track.stop())
    screenStream?.getTracks().forEach(track => track.stop())
    outputStream?.getTracks().forEach(track => track.stop())
    cameraStream = null
    screenStream = null
    outputStream = null
    if (cameraPreview.value) cameraPreview.value.srcObject = null
    if (screenVideo) screenVideo.srcObject = null
    screenVideo = null
  }

  function stop() {
    stopping = true
    captureGeneration += 1
    ready.value = false
    recording.value = false
    clearInterval(segmentTimer)
    clearInterval(drawTimer)
    const finished = recorder?.state === 'recording'
      ? new Promise(resolve => { recorder.addEventListener('stop', resolve, { once: true }); recorder.stop() })
      : Promise.resolve()
    recorder = null
    stopTracks()
    return finished.then(async () => {
      await uploadQueue
      await Promise.all(pendingUploads.map(item => item.saved))
    })
  }

  function lostDevice(name) {
    if (stopping) return
    error.value = `${name}已停止共享，请重新授权后继续考试。`
    stop()
  }

  function queueUpload(blob) {
    if (!blob.size) return
    const item = { segmentNo: nextSegmentNo++, blob }
    // Persist immediately, including the final segment created while an older
    // upload is failing. Upload retries must never delay local durability.
    item.saved = saveExamRecordingSegment(processId, item.segmentNo, blob)
      .then(key => { item.key = key; return true })
      .catch(cause => { item.storageError = cause; return false })
    pendingUploads.push(item)
    pendingCount.value = pendingUploads.length
    scheduleUploads()
  }

  function scheduleUploads() {
    uploadQueue = uploadQueue.then(async () => {
      uploading.value = true
      while (pendingUploads.length) {
        const item = pendingUploads[0]
        const { segmentNo, blob } = item
        if (item.saved && !await item.saved) {
          item.key = await saveExamRecordingSegment(processId, segmentNo, blob)
          item.saved = null
        }
        for (let attempt = 0; attempt < 3; attempt++) {
          try { await schoolApi.uploadExamRecording(processId, segmentNo, blob); break } catch (uploadError) {
            if (attempt === 2) throw uploadError
            await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)))
          }
        }
        await deleteRecordingSession(item.key)
        pendingUploads.shift()
        pendingCount.value = pendingUploads.length
      }
      if (uploadFailed) error.value = ''
      uploadFailed = false
    }).catch(() => {
      uploadFailed = true
      error.value = pendingUploads.some(item => item.storageError && !item.key)
        ? '录像暂存失败，请保持页面打开并重试上传。'
        : '录像上传失败，片段已暂存。请检查网络后重试上传。'
      if (!stopping) void stop()
    }).finally(() => { uploading.value = false })
    return uploadQueue
  }

  async function retryUploads() {
    await scheduleUploads()
    return pendingUploads.length === 0
  }

  function recordSegment() {
    if (stopping || !outputStream) return
    const generation = captureGeneration
    const chunks = []
    const activeRecorder = new MediaRecorder(outputStream, { mimeType: 'video/webm' })
    recorder = activeRecorder
    activeRecorder.ondataavailable = event => { if (event.data.size) chunks.push(event.data) }
    activeRecorder.onstop = () => {
      queueUpload(new Blob(chunks, { type: 'video/webm' }))
      if (!stopping && generation === captureGeneration && outputStream?.getVideoTracks().some(track => track.readyState === 'live')) recordSegment()
    }
    activeRecorder.start()
  }

  async function start() {
    if (ready.value) return true
    stop()
    stopping = false
    error.value = ''
    try {
      if (!prepared) {
        await prepare()
        throw new Error('监控配置已加载，请再次点击授权设备')
      }
      if (!policy.cameraEnabled && !policy.screenRecordingEnabled) { ready.value = true; return true }
      if (!navigator.mediaDevices || (typeof MediaRecorder === 'undefined')) throw new Error('当前浏览器不支持设备采集和录像')
      if (!MediaRecorder.isTypeSupported('video/webm')) throw new Error('当前浏览器不支持 WebM 录像，请使用 Chrome 或 Edge')
      if (policy.screenRecordingEnabled) {
        screenStream = await navigator.mediaDevices.getDisplayMedia({ video: { displaySurface: 'monitor' }, audio: false })
        const surface = screenStream.getVideoTracks()[0]?.getSettings()?.displaySurface
        if (surface && surface !== 'monitor') throw new Error('请选择整个屏幕进行录制')
      }
      if (policy.cameraEnabled) cameraStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
      if (stopping) { stopTracks(); return false }
      if (cameraStream && cameraPreview.value) { cameraPreview.value.srcObject = cameraStream; await cameraPreview.value.play() }
      if (screenStream && cameraStream) {
        screenVideo = document.createElement('video')
        screenVideo.srcObject = screenStream
        screenVideo.muted = true
        await screenVideo.play()
        canvas = document.createElement('canvas')
        canvas.width = 1280
        canvas.height = 720
        const context = canvas.getContext('2d')
        drawTimer = setInterval(() => {
          if (screenVideo.readyState >= 2) context.drawImage(screenVideo, 0, 0, canvas.width, canvas.height)
          if (cameraPreview.value?.readyState >= 2) {
            context.fillStyle = '#111'
            context.fillRect(956, 526, 316, 186)
            context.drawImage(cameraPreview.value, 960, 530, 308, 178)
          }
        }, 100)
        outputStream = canvas.captureStream(10)
      } else outputStream = screenStream || cameraStream
      screenStream?.getVideoTracks().forEach(track => { track.onended = () => lostDevice('屏幕录制') })
      cameraStream?.getVideoTracks().forEach(track => { track.onended = () => lostDevice('摄像头') })
      if (pendingUploads.length) {
        await scheduleUploads()
        if (pendingUploads.length) throw new Error('之前的录像仍未上传，请检查网络后重试')
      }
      ready.value = true
      recording.value = true
      recordSegment()
      segmentTimer = setInterval(() => { if (recorder?.state === 'recording') recorder.stop() }, 20000)
      return true
    } catch (cause) {
      error.value = cause?.message || '设备授权失败，请检查浏览器权限。'
      stop()
      return false
    }
  }

  return { cameraPreview, ready, recording, error, pendingCount, uploading, retryUploads, prepare, start, stop, policy: () => policy }
}
