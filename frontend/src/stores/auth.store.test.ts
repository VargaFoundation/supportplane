import { describe, it, expect, beforeEach } from 'vitest'
import { useAuthStore } from './auth'

describe('auth store', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('starts unauthenticated', () => {
    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(false)
    expect(state.token).toBeNull()
    expect(state.tenantId).toBeNull()
    expect(state.role).toBeNull()
  })

  it('login sets token and tenant', () => {
    useAuthStore.getState().login('tok-123', 'ref-456', 'acme-corp')
    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(true)
    expect(state.token).toBe('tok-123')
    expect(state.refreshToken).toBe('ref-456')
    expect(state.tenantId).toBe('acme-corp')
  })

  it('login with support tenant sets OPERATOR role', () => {
    useAuthStore.getState().login('tok', 'ref', 'support')
    expect(useAuthStore.getState().role).toBe('OPERATOR')
  })

  it('login with regular tenant sets USER role', () => {
    useAuthStore.getState().login('tok', 'ref', 'acme-corp')
    expect(useAuthStore.getState().role).toBe('USER')
  })

  it('login with explicit role uses that role', () => {
    useAuthStore.getState().login('tok', 'ref', 'acme-corp', 'ADMIN')
    expect(useAuthStore.getState().role).toBe('ADMIN')
  })

  it('logout clears all state', () => {
    useAuthStore.getState().login('tok', 'ref', 'acme-corp')
    useAuthStore.getState().logout()
    const state = useAuthStore.getState()
    expect(state.isAuthenticated).toBe(false)
    expect(state.token).toBeNull()
    expect(state.refreshToken).toBeNull()
    expect(state.tenantId).toBeNull()
    expect(state.role).toBeNull()
  })
})
