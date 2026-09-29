export function sessionRedirect(to, session) {
  if (session && Number(session.mustChangePassword) === 1 && to.name !== 'change-password') {
    return '/change-password'
  }
  return null
}
