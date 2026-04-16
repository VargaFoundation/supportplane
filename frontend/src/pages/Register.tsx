import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '@/lib/api'
import { Shield, CheckCircle } from 'lucide-react'
import { Button } from '@/components/ui'

export default function Register() {
  const [companyName, setCompanyName] = useState('')
  const [email, setEmail] = useState('')
  const [fullName, setFullName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)

    try {
      const { data } = await api.post('/auth/register', {
        companyName, email, fullName, password,
      })
      setSuccess(`Registration successful! Your tenant ID is: ${data.tenantId}`)
      setTimeout(() => navigate('/login'), 3000)
    } catch (err: any) {
      setError(err.response?.data?.error || 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  const inputCls = "w-full px-3 py-2.5 border rounded-lg text-sm bg-white focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition-colors placeholder:text-muted-foreground/60"

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-gray-50 to-gray-100/80">
      <div className="w-full max-w-[420px] px-4">
        <div className="bg-white rounded-xl shadow-lg shadow-gray-200/60 border border-gray-100 p-8">
          <div className="text-center mb-8">
            <div className="inline-flex items-center justify-center w-12 h-12 rounded-xl bg-primary mb-3">
              <Shield className="w-6 h-6 text-white" />
            </div>
            <h1 className="text-xl font-semibold text-foreground tracking-tight">Create an account</h1>
            <p className="text-sm text-muted-foreground mt-1">Register your company on SupportPlane</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block text-sm font-medium mb-1.5">Company Name</label>
              <input type="text" value={companyName}
                onChange={(e) => setCompanyName(e.target.value)}
                className={inputCls} required placeholder="Acme Corp" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1.5">Your Full Name</label>
              <input type="text" value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                className={inputCls} required placeholder="John Doe" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1.5">Email</label>
              <input type="email" value={email}
                onChange={(e) => setEmail(e.target.value)}
                className={inputCls} required placeholder="john@acme.com" />
            </div>
            <div>
              <label className="block text-sm font-medium mb-1.5">Password</label>
              <input type="password" value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={inputCls} required minLength={8} placeholder="Min. 8 characters" />
            </div>

            {error && (
              <div className="text-sm text-destructive bg-destructive/5 border border-destructive/10 p-3 rounded-lg">
                {error}
              </div>
            )}
            {success && (
              <div className="text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 p-3 rounded-lg flex items-start gap-2">
                <CheckCircle className="w-4 h-4 mt-0.5 shrink-0" />
                <span>{success}<br /><span className="text-xs text-emerald-600">Redirecting to login...</span></span>
              </div>
            )}

            <Button type="submit" loading={loading} className="w-full py-2.5">
              Register
            </Button>
          </form>

          <p className="text-center text-sm text-muted-foreground mt-6">
            Already registered?{' '}
            <Link to="/login" className="text-primary font-medium hover:underline">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  )
}
