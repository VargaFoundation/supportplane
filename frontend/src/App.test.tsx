import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/auth'
import App from './App'

function renderApp(initialRoute = '/') {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialRoute]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('App routing', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
  })

  it('unauthenticated user sees login page', () => {
    renderApp('/login')
    expect(screen.getByText('SupportPlane')).toBeInTheDocument()
    expect(screen.getByText('Sign In')).toBeInTheDocument()
  })

  it('unauthenticated user on / is redirected to login', () => {
    renderApp('/')
    expect(screen.getByText('Sign In')).toBeInTheDocument()
  })

  it('login page has register link', () => {
    renderApp('/login')
    expect(screen.getByText('Register your company')).toBeInTheDocument()
  })
})
