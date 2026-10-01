import { readFileSync } from 'node:fs'
import vm from 'node:vm'
import test from 'node:test'
import assert from 'node:assert/strict'

const source = readFileSync(new URL('../src/composables/useExamMonitoring.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export function', 'function')

function environment(cache, upload) {
  const track = { readyState: 'live', stop() { this.readyState = 'ended' } }
  const stream = { getTracks: () => [track], getVideoTracks: () => [track] }
  const context = {
    ref: value => ({ value }), reactive: value => value, Blob, Promise,
    setInterval, clearInterval, setTimeout: callback => setTimeout(callback, 1),
    navigator: { mediaDevices: { getUserMedia: async () => stream } },
    schoolApi: { monitoringPolicy: async () => ({ data: { cameraEnabled: 1, nextSegmentNo: 0 } }), uploadExamRecording: upload },
    saveExamRecordingSegment: async (processId, segmentNo, blob) => {
      const key = `${processId}:${segmentNo}`
      cache.set(key, { key, segmentNo, blob })
      return key
    },
    loadExamRecordingSegments: async () => [...cache.values()],
    deleteRecordingSession: async key => cache.delete(key),
    MediaRecorder: class {
      static isTypeSupported() { return true }
      constructor() { this.state = 'inactive'; this.listeners = [] }
      start() { this.state = 'recording' }
      addEventListener(name, callback) { this.listeners.push(callback) }
      stop() {
        this.state = 'inactive'
        queueMicrotask(() => {
          this.ondataavailable({ data: new Blob(['final video']) })
          this.onstop()
          this.listeners.forEach(callback => callback())
        })
      }
    },
  }
  vm.createContext(context)
  vm.runInContext(source + '\nthis.monitoring = useExamMonitoring(41)', context)
  return context.monitoring
}

test('failed final upload survives reopening a completed exam and retries without capture', async () => {
  const cache = new Map()
  let attempts = 0
  const first = environment(cache, async () => { attempts++; throw new Error('offline') })
  await first.prepare()
  assert.equal(await first.start(), true)
  await first.stop()
  assert.equal(attempts, 3)
  assert.equal(cache.size, 1)
  assert.equal(first.pendingCount.value, 1)
  assert.match(first.error.value, /已暂存/)

  const uploaded = []
  const reopened = environment(cache, async (id, segmentNo, blob) => uploaded.push({ id, segmentNo, size: blob.size }))
  await reopened.prepare()
  assert.equal(await reopened.retryUploads(), true)
  assert.equal(reopened.recording.value, false)
  assert.equal(reopened.pendingCount.value, 0)
  assert.equal(cache.size, 0)
  assert.deepEqual(uploaded.map(item => item.segmentNo), [0])
  // A resumed attempt must advance past the restored local segment even if
  // the policy was fetched before that segment reached the server.
  assert.equal(await reopened.start(), true)
  await reopened.stop()
  assert.deepEqual(uploaded.map(item => item.segmentNo), [0, 1])
})

test('an ended capture remains paused after its final upload succeeds', async () => {
  const monitoring = environment(new Map(), async () => {})
  await monitoring.prepare()
  await monitoring.start()
  monitoring.error.value = '摄像头已停止共享'
  await monitoring.stop()
  assert.equal(monitoring.error.value, '摄像头已停止共享')
  assert.equal(monitoring.ready.value, false)
})
