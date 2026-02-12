import { describe, it, expect, beforeEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useAuthStore } from '@/stores/auth'
import Login from './Login'

vi.mock('@/lib/api', () => ({
  default: {
    post: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  },
}))

function renderLogin() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('Login page', () => {
  beforeEach(() => {
    useAuthStore.getState().logout()
    vi.clearAllMocks()
  })

  it('renders form fields', () => {
    renderLogin()
    expect(screen.getByText('Tenant ID')).toBeInTheDocument()
    expect(screen.getByText('Username / Email')).toBeInTheDocument()
    expect(screen.getByText('Password')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument()
  })

  it('shows error on failed login', async () => {
    const api = await import('@/lib/api')
    const mockedPost = vi.mocked(api.default.post)
    mockedPost.mockRejectedValueOnce(new Error('401'))

    renderLogin()
    const user = userEvent.setup()

    await user.type(screen.getByPlaceholderText(/tenant id/i), 'acme')
    await user.type(screen.getAllByRole('textbox')[1], 'admin@acme.com')
    await user.type(screen.getByDisplayValue(''), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByText(/authentication failed/i)).toBeInTheDocument()
  })

  it('calls login on successful submit', async () => {
    const api = await import('@/lib/api')
    const mockedPost = vi.mocked(api.default.post)
    mockedPost.mockResolvedValueOnce({
      data: { accessToken: 'tok', refreshToken: 'ref', tenantId: 'acme' },
    })

    renderLogin()
    const user = userEvent.setup()

    const inputs = screen.getAllByRole('textbox')
    await user.type(inputs[0], 'acme')
    await user.type(inputs[1], 'admin@acme.com')

    // Password is type="password" so not a textbox
    const passwordInput = document.querySelector('input[type="password"]') as HTMLInputElement
    await user.type(passwordInput, 'pass1234')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(mockedPost).toHaveBeenCalledWith('/auth/login', {
      username: 'admin@acme.com',
      password: 'pass1234',
      tenantId: 'acme',
    })
  })

  it('has link to register page', () => {
    renderLogin()
    expect(screen.getByText('Register your company')).toBeInTheDocument()
  })
})
