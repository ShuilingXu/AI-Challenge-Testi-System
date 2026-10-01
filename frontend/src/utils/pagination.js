// Existing list screens and selectors expect the complete collection. Callers
// that supply a page explicitly retain normal server pagination.
export async function requestCollection(getPage, params = {}) {
  const query = { page: 1, pageSize: 200, ...params }
  const response = await getPage(query)
  if (Array.isArray(response?.data)) return response
  const pagination = response?.data || {}
  const items = [...(pagination.items || [])]
  if (params.page == null) {
    while (items.length < Number(pagination.total || 0)) {
      query.page += 1
      const next = await getPage({ ...query })
      const batch = next?.data?.items || []
      if (!batch.length) break
      items.push(...batch)
    }
  }
  return { ...response, data: items, pagination: { ...pagination, loaded: items.length } }
}
