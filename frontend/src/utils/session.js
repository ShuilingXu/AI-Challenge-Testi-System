const SESSION_USER_KEY = 'session-user'
const TOKEN_KEY = 'autohr-access-token'
const LEGACY_TOKEN_KEY = 'demo-token'

function isSessionUser(value) {
  return value && typeof value === 'object' && !Array.isArray(value)
}

export function readSessionToken() {
  // The access token is HttpOnly and is attached by the browser cookie jar.
  // Keep this helper for the streaming API and old callers, but never read or
  // migrate a bearer token from script-readable storage.
  return null
}

export function readSessionUser() {
  const raw = window.localStorage.getItem(SESSION_USER_KEY)
  if (!raw) return null
  try {
    const session = JSON.parse(raw)
    if (!isSessionUser(session)) {
      throw new Error('Invalid session')
    }
    return session
  } catch {
    clearSession()
    return null
  }
}

export function writeSessionUser(user) {
  if (!isSessionUser(user)) {
    window.localStorage.removeItem(SESSION_USER_KEY)
    return
  }
  window.localStorage.setItem(SESSION_USER_KEY, JSON.stringify(user))
}

export function writeSession(token, user) {
  if (!isSessionUser(user)) {
    clearSession()
    return
  }
  window.localStorage.removeItem(TOKEN_KEY)
  window.localStorage.removeItem(LEGACY_TOKEN_KEY)
  writeSessionUser(user)
}

export function clearSession() {
  window.localStorage.removeItem(SESSION_USER_KEY)
  window.localStorage.removeItem(TOKEN_KEY)
  window.localStorage.removeItem(LEGACY_TOKEN_KEY)
}
