import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  token: string | null
  refreshToken: string | null
  tenantId: string | null
  role: string | null
  isAuthenticated: boolean
  login: (token: string, refreshToken: string, tenantId: string, role?: string) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      refreshToken: null,
      tenantId: null,
      role: null,
      isAuthenticated: false,
      login: (token, refreshToken, tenantId, role) =>
        set({
          token,
          refreshToken,
          tenantId,
          role: role || (tenantId === 'support' ? 'OPERATOR' : 'USER'),
          isAuthenticated: true,
        }),
      logout: () =>
        set({
          token: null,
          refreshToken: null,
          tenantId: null,
          role: null,
          isAuthenticated: false,
        }),
    }),
    { name: 'supportplane-auth' }
  )
)
