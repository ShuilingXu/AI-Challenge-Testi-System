export function updateReviewDrafts(drafts, records, savedRecordId = null) {
  for (const record of records || []) {
    if (savedRecordId != null && record.id !== savedRecordId && drafts[record.id]) continue
    drafts[record.id] = { score: record.averageScore ?? 0, note: record.teacherNote || '' }
  }
}
