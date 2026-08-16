const DATABASE_NAME = 'auto-hr-recordings'
const DATABASE_VERSION = 1
const SESSION_STORE = 'sessions'
const CHUNK_STORE = 'chunks'

export const MAX_RECORDING_UPLOAD_BYTES = 100 * 1024 * 1024
export const MAX_RECORDING_CACHE_BYTES = 96 * 1024 * 1024
export const RECORDING_STOP_THRESHOLD_BYTES = 88 * 1024 * 1024
export const RECORDING_WRITE_HIGH_WATER_BYTES = 8 * 1024 * 1024
export const RECORDING_WRITE_LOW_WATER_BYTES = 2 * 1024 * 1024

let databasePromise = null

function requestResult(request) {
  return new Promise((resolve, reject) => {
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error || new Error('浏览器录像暂存失败'))
  })
}

function transactionComplete(transaction) {
  return new Promise((resolve, reject) => {
    transaction.oncomplete = () => resolve()
    transaction.onabort = () => reject(transaction.error || new Error('浏览器录像暂存事务已中止'))
    transaction.onerror = () => reject(transaction.error || new Error('浏览器录像暂存失败'))
  })
}

function openRecordingDatabase() {
  if (!globalThis.indexedDB) return Promise.reject(new Error('当前浏览器不支持可靠录像暂存，请使用最新版 Chrome 或 Edge'))
  if (databasePromise) return databasePromise
  databasePromise = new Promise((resolve, reject) => {
    const request = globalThis.indexedDB.open(DATABASE_NAME, DATABASE_VERSION)
    request.onupgradeneeded = () => {
      const database = request.result
      if (!database.objectStoreNames.contains(SESSION_STORE)) {
        database.createObjectStore(SESSION_STORE, { keyPath: 'key' })
      }
      if (!database.objectStoreNames.contains(CHUNK_STORE)) {
        database.createObjectStore(CHUNK_STORE, { keyPath: ['sessionKey', 'sequence'] })
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => {
      databasePromise = null
      reject(request.error || new Error('无法打开浏览器录像暂存空间'))
    }
    request.onblocked = () => {
      databasePromise = null
      reject(new Error('录像暂存空间正在被其他页面占用，请关闭旧页面后重试'))
    }
  })
  return databasePromise
}

export async function getRecordingSession(key) {
  const database = await openRecordingDatabase()
  const transaction = database.transaction(SESSION_STORE, 'readonly')
  const completed = transactionComplete(transaction)
  const session = await requestResult(transaction.objectStore(SESSION_STORE).get(key))
  await completed
  if (!session) return null
  return session.byteSize > MAX_RECORDING_CACHE_BYTES
    ? { ...session, limitReached: true, omittedBytes: session.byteSize - MAX_RECORDING_CACHE_BYTES }
    : session
}

export async function beginRecordingSession(key, metadata = {}) {
  const existing = await getRecordingSession(key)
  if (existing?.byteSize > 0) return existing

  const database = await openRecordingDatabase()
  const transaction = database.transaction(SESSION_STORE, 'readwrite')
  const completed = transactionComplete(transaction)
  const session = {
    ...metadata,
    key,
    byteSize: 0,
    nextSequence: 0,
    status: 'recording',
    limitReached: false,
    omittedBytes: 0,
    error: '',
    createdAt: Date.now(),
    updatedAt: Date.now(),
  }
  transaction.objectStore(SESSION_STORE).put(session)
  await completed
  return session
}

export async function appendRecordingChunk(key, data) {
  if (!data?.size) return { appended: false, limitReached: false, session: null }
  const database = await openRecordingDatabase()
  const transaction = database.transaction([SESSION_STORE, CHUNK_STORE], 'readwrite')
  const completed = transactionComplete(transaction)
  const sessions = transaction.objectStore(SESSION_STORE)
  const session = await requestResult(sessions.get(key))
  if (!session) {
    await completed
    throw new Error('录像暂存会话不存在，请重新开始录制')
  }
  if ((session.byteSize || 0) + data.size > MAX_RECORDING_CACHE_BYTES) {
    const limitedSession = {
      ...session,
      status: 'limit_reached',
      limitReached: true,
      omittedBytes: (session.omittedBytes || 0) + data.size,
      updatedAt: Date.now(),
    }
    sessions.put(limitedSession)
    await completed
    return { appended: false, limitReached: true, session: limitedSession }
  }
  const sequence = session.nextSequence || 0
  const byteSize = (session.byteSize || 0) + data.size
  const limitReached = Boolean(session.limitReached) || byteSize >= RECORDING_STOP_THRESHOLD_BYTES
  transaction.objectStore(CHUNK_STORE).put({ sessionKey: key, sequence, data })
  const updatedSession = {
    ...session,
    byteSize,
    nextSequence: sequence + 1,
    status: limitReached ? 'limit_reached' : session.status,
    limitReached,
    updatedAt: Date.now(),
  }
  sessions.put(updatedSession)
  await completed
  return { appended: true, limitReached, session: updatedSession }
}

export async function updateRecordingSession(key, changes) {
  const database = await openRecordingDatabase()
  const transaction = database.transaction(SESSION_STORE, 'readwrite')
  const completed = transactionComplete(transaction)
  const sessions = transaction.objectStore(SESSION_STORE)
  const session = await requestResult(sessions.get(key))
  if (!session) {
    await completed
    return null
  }
  const updated = { ...session, ...changes, key, updatedAt: Date.now() }
  sessions.put(updated)
  await completed
  return updated
}

export async function buildRecordingFile(key) {
  const database = await openRecordingDatabase()
  const transaction = database.transaction([SESSION_STORE, CHUNK_STORE], 'readonly')
  const completed = transactionComplete(transaction)
  const session = await requestResult(transaction.objectStore(SESSION_STORE).get(key))
  if (!session) {
    await completed
    return null
  }
  const range = globalThis.IDBKeyRange.bound([key, 0], [key, Number.MAX_SAFE_INTEGER])
  const rows = await requestResult(transaction.objectStore(CHUNK_STORE).getAll(range))
  await completed
  if (!rows.length) return null
  const uploadRows = []
  let uploadSize = 0
  let omittedBytes = 0
  let exceededUploadPrefix = false
  for (const row of rows) {
    if (!exceededUploadPrefix && uploadSize + row.data.size <= MAX_RECORDING_CACHE_BYTES) {
      uploadRows.push(row)
      uploadSize += row.data.size
    } else {
      exceededUploadPrefix = true
      omittedBytes += row.data.size
    }
  }
  if (!uploadRows.length) return null
  const type = session.contentType || 'video/webm'
  return {
    file: new File(uploadRows.map((row) => row.data), session.fileName || 'recording.webm', { type }),
    session: { ...session, byteSize: uploadSize, limitReached: Boolean(session.limitReached) || exceededUploadPrefix, omittedBytes: (session.omittedBytes || 0) + omittedBytes },
  }
}

export async function deleteRecordingSession(key) {
  const database = await openRecordingDatabase()
  const transaction = database.transaction([SESSION_STORE, CHUNK_STORE], 'readwrite')
  const completed = transactionComplete(transaction)
  transaction.objectStore(SESSION_STORE).delete(key)
  const chunks = transaction.objectStore(CHUNK_STORE)
  const range = globalThis.IDBKeyRange.bound([key, 0], [key, Number.MAX_SAFE_INTEGER])
  const cursorRequest = chunks.openKeyCursor(range)
  cursorRequest.onsuccess = () => {
    const cursor = cursorRequest.result
    if (!cursor) return
    chunks.delete(cursor.primaryKey)
    cursor.continue()
  }
  await completed
}

export function formatRecordingSize(byteSize) {
  if (!byteSize) return '0 MB'
  return `${(byteSize / 1024 / 1024).toFixed(byteSize >= 10 * 1024 * 1024 ? 1 : 2)} MB`
}
