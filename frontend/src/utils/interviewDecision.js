function sameProcess(left, right) {
  return left !== null && left !== undefined && right !== null && right !== undefined && String(left) === String(right)
}

export function buildInterviewDecision({ process, approved, finalApproval, detailProcessId, draft = {} }) {
  if (!approved) return { payload: { approved }, missing: null }

  const useDraft = sameProcess(process?.id, detailProcessId)
  const departmentId = process?.jobDepartmentId || (useDraft ? draft.departmentId : null) || null
  if (finalApproval && !departmentId) return { payload: null, missing: 'department' }

  const jobId = useDraft ? draft.jobId || null : null
  const baseSalary = useDraft ? draft.baseSalary : null
  if (finalApproval && (!jobId || Number(baseSalary) <= 0)) return { payload: null, missing: 'jobAndSalary' }

  return {
    payload: {
      approved,
      departmentId,
      jobId: finalApproval ? jobId : null,
      baseSalary: finalApproval ? baseSalary : null,
    },
    missing: null,
  }
}
