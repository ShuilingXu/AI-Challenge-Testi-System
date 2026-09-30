export function answerForQuestion(previous, next, draft) {
  if (!next) return draft
  // Failed or interrupted evaluation may only retry the persisted answer.
  if (next.answerContent != null) return next.answerContent
  return previous?.id === next.id ? draft : ''
}
