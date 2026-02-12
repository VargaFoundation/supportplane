import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/auth'
import Dashboard from './Dashboard'

vi.mock('@/lib/api', () => ({
  default: {
    get: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}))

function renderDashboard() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <Dashboard />
    </QueryClientProvider>
  )
}

describe('Dashboard page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows stat cards when data loads', async () => {
    const api = await import('@/lib/api')
    const mockedGet = vi.mocked(api.default.get)
    mockedGet.mockResolvedValueOnce({
      data: {
        totalClusters: 5,
        activeClusters: 3,
        openTickets: 2,
        totalUsers: 8,
        totalBundles: 10,
      },
    })

    useAuthStore.getState().login('tok', 'ref', 'acme', 'ADMIN')
    renderDashboard()

    expect(await screen.findByText('5')).toBeInTheDocument()
    expect(screen.getByText('Clusters')).toBeInTheDocument()
    expect(screen.getByText('Open Tickets')).toBeInTheDocument()
    expect(screen.getByText('Users')).toBeInTheDocument()
  })

  it('shows operator-only cards for OPERATOR role', async () => {
    const api = await import('@/lib/api')
    const mockedGet = vi.mocked(api.default.get)
    mockedGet.mockResolvedValueOnce({
      data: {
        totalClusters: 20,
        activeClusters: 15,
        openTickets: 8,
        totalUsers: 30,
        totalBundles: 50,
        totalTenants: 5,
        pendingRecommendations: 3,
      },
    })

    useAuthStore.getState().login('tok', 'ref', 'support', 'OPERATOR')
    renderDashboard()

    expect(await screen.findByText('Tenants')).toBeInTheDocument()
    expect(screen.getByText('Pending Recommendations')).toBeInTheDocument()
  })

  it('shows loading state initially', async () => {
    const api = await import('@/lib/api')
    const mockedGet = vi.mocked(api.default.get)
    mockedGet.mockReturnValue(new Promise(() => {}))

    useAuthStore.getState().login('tok', 'ref', 'acme', 'ADMIN')
    renderDashboard()

    expect(screen.getByText('Loading...')).toBeInTheDocument()
  })
})
