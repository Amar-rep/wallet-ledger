import api from './axiosInstance.js'

export const flexApi = {
  // User — my received Flex credits
  myCredits: () => api.get('/flex/my-credits').then((r) => r.data),

  // Admin — all Flex credits, optionally filtered by status
  adminList: (status) =>
    api.get('/admin/flex', { params: status ? { status } : {} }).then((r) => r.data),

  // Admin — write off a RECOVERING Flex credit
  writeOff: (id, reason) =>
    api.post(`/admin/flex/${id}/write-off`, { reason }).then((r) => r.data),
}
