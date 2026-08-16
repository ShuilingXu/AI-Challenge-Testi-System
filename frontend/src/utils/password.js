export const strongPasswordMessage = '密码必须至少8位且同时包含字母和数字'

export function isStrongPassword(value) {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(String(value || ''))
}
