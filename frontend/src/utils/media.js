export async function requestCameraAndMicrophone() {
  const constraints = { video: true, audio: true }
  if (navigator.mediaDevices?.getUserMedia) {
    return navigator.mediaDevices.getUserMedia(constraints)
  }

  const legacyGetUserMedia = navigator.getUserMedia || navigator.webkitGetUserMedia || navigator.mozGetUserMedia || navigator.msGetUserMedia
  if (legacyGetUserMedia) {
    return new Promise((resolve, reject) => legacyGetUserMedia.call(navigator, constraints, resolve, reject))
  }

  throw new Error(buildMediaUnsupportedMessage())
}

export function createPeerConnection(iceServers = defaultIceServers()) {
  return new RTCPeerConnection({ iceServers, iceTransportPolicy: 'all' })
}

export function isRelayIceCandidate(candidate) {
  return / typ relay(?: |$)/.test(candidate?.candidate || '')
}

export function defaultIceServers() {
  return [
    { urls: 'stun:stun.l.google.com:19302' },
    { urls: 'stun:stun.cloudflare.com:3478' },
  ]
}

export function attachRemoteTrack(videoElement, event, existingStream) {
  const stream = existingStream || event.streams?.[0] || new MediaStream()
  if (!event.streams?.[0] && event.track && !stream.getTracks().some((track) => track.id === event.track.id)) {
    stream.addTrack(event.track)
  }
  if (videoElement && videoElement.srcObject !== stream) {
    videoElement.srcObject = stream
  }
  playVideo(videoElement)
  return stream
}

export function playVideo(videoElement) {
  const playPromise = videoElement?.play?.()
  if (playPromise?.catch) {
    playPromise.catch(() => {})
  }
}

export function buildMediaErrorMessage(error) {
  if (error?.name === 'NotAllowedError' || error?.name === 'PermissionDeniedError') {
    return '摄像头/麦克风权限被拒绝，请在浏览器地址栏权限设置中允许后重试'
  }
  if (error?.name === 'NotFoundError' || error?.name === 'DevicesNotFoundError') {
    return '未检测到摄像头或麦克风设备，请连接设备后重试'
  }
  if (error?.name === 'NotReadableError' || error?.name === 'TrackStartError') {
    return '摄像头或麦克风正被其他程序占用，请关闭占用程序后重试'
  }
  if (error?.message === buildMediaUnsupportedMessage()) {
    return error.message
  }
  return error?.message || '无法启动摄像头/麦克风'
}

function buildMediaUnsupportedMessage() {
  return '当前页面无法访问摄像头/麦克风。请使用 Chrome/Edge，并通过 HTTPS 或 localhost 访问系统；如果使用局域网 IP，请配置 HTTPS 后重试'
}
