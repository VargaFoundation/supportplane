import { cn } from '@/lib/utils'
import { Loader2 } from 'lucide-react'

// ─── Loading Spinner ──────────────────────────────────────────────
export function Spinner({ className }: { className?: string }) {
  return <Loader2 className={cn("w-5 h-5 animate-spin text-primary", className)} />
}

export function PageLoader({ message = 'Loading...' }: { message?: string }) {
  return (
    <div className="flex items-center justify-center py-20">
      <div className="flex flex-col items-center gap-3">
        <Spinner className="w-6 h-6" />
        <p className="text-sm text-muted-foreground">{message}</p>
      </div>
    </div>
  )
}

// ─── Skeleton ─────────────────────────────────────────────────────
export function Skeleton({ className }: { className?: string }) {
  return <div className={cn("skeleton", className)} />
}

export function CardSkeleton() {
  return (
    <div className="bg-white rounded-lg border shadow-card p-6 space-y-3">
      <div className="flex items-center gap-3">
        <Skeleton className="w-10 h-10 rounded-lg" />
        <Skeleton className="h-4 w-24" />
      </div>
      <Skeleton className="h-8 w-16" />
      <Skeleton className="h-3 w-20" />
    </div>
  )
}

export function TableSkeleton({ rows = 5, cols = 4 }: { rows?: number; cols?: number }) {
  return (
    <div className="bg-white border rounded-lg overflow-hidden shadow-card">
      <div className="border-b bg-gray-50/80 p-3 flex gap-4">
        {Array.from({ length: cols }).map((_, i) => (
          <Skeleton key={i} className="h-3 w-20" />
        ))}
      </div>
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="p-3 border-b last:border-0 flex gap-4">
          {Array.from({ length: cols }).map((_, j) => (
            <Skeleton key={j} className={cn("h-4", j === 0 ? "w-40" : "w-20")} />
          ))}
        </div>
      ))}
    </div>
  )
}

// ─── Status / Priority Badge ──────────────────────────────────────
const STATUS_COLORS: Record<string, string> = {
  ACTIVE: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  PENDING: 'bg-amber-50 text-amber-700 border-amber-200',
  DETACHED: 'bg-gray-50 text-gray-600 border-gray-200',
  OPEN: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  IN_PROGRESS: 'bg-blue-50 text-blue-700 border-blue-200',
  ASSIGNED: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  WAITING: 'bg-amber-50 text-amber-700 border-amber-200',
  RESOLVED: 'bg-gray-50 text-gray-600 border-gray-200',
  CLOSED: 'bg-gray-50 text-gray-500 border-gray-200',
  STARTED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
  INSTALLED: 'bg-blue-50 text-blue-700 border-blue-200',
  UNKNOWN: 'bg-gray-50 text-gray-500 border-gray-200',
  DRAFT: 'bg-gray-50 text-gray-600 border-gray-200',
  VALIDATED: 'bg-blue-50 text-blue-700 border-blue-200',
  DELIVERED: 'bg-emerald-50 text-emerald-700 border-emerald-200',
}

const PRIORITY_COLORS: Record<string, string> = {
  CRITICAL: 'bg-red-50 text-red-700 border-red-200',
  HIGH: 'bg-orange-50 text-orange-700 border-orange-200',
  MEDIUM: 'bg-amber-50 text-amber-700 border-amber-200',
  LOW: 'bg-blue-50 text-blue-700 border-blue-200',
  INFO: 'bg-blue-50 text-blue-700 border-blue-200',
  WARNING: 'bg-amber-50 text-amber-700 border-amber-200',
}

const TIER_COLORS: Record<string, string> = {
  BASIC: 'bg-gray-50 text-gray-700 border-gray-200',
  STANDARD: 'bg-blue-50 text-blue-700 border-blue-200',
  PREMIUM: 'bg-purple-50 text-purple-700 border-purple-200',
  ENTERPRISE: 'bg-amber-50 text-amber-800 border-amber-200',
}

export function StatusBadge({ value, className }: { value: string; className?: string }) {
  return (
    <span className={cn(
      "inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border",
      STATUS_COLORS[value] || 'bg-gray-50 text-gray-600 border-gray-200',
      className,
    )}>
      {value?.replace(/_/g, ' ')}
    </span>
  )
}

export function PriorityBadge({ value, className }: { value: string; className?: string }) {
  return (
    <span className={cn(
      "inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border",
      PRIORITY_COLORS[value] || 'bg-gray-50 text-gray-600 border-gray-200',
      className,
    )}>
      {value}
    </span>
  )
}

export function TierBadge({ value, className }: { value: string; className?: string }) {
  return (
    <span className={cn(
      "inline-flex items-center px-2 py-0.5 text-xs font-medium rounded-full border",
      TIER_COLORS[value] || 'bg-gray-50 text-gray-600 border-gray-200',
      className,
    )}>
      {value}
    </span>
  )
}

// ─── Empty State ──────────────────────────────────────────────────
export function EmptyState({ icon: Icon, title, description, action }: {
  icon: any; title: string; description?: string; action?: React.ReactNode
}) {
  return (
    <div className="text-center py-16">
      <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-gray-100 mb-4">
        <Icon className="w-7 h-7 text-gray-400" />
      </div>
      <h3 className="text-sm font-medium text-foreground">{title}</h3>
      {description && <p className="text-sm text-muted-foreground mt-1 max-w-sm mx-auto">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

// ─── Card ─────────────────────────────────────────────────────────
export function Card({ children, className, hover }: {
  children: React.ReactNode; className?: string; hover?: boolean
}) {
  return (
    <div className={cn(
      "bg-white rounded-lg border shadow-card",
      hover && "transition-shadow hover:shadow-card-hover",
      className,
    )}>
      {children}
    </div>
  )
}

// ─── Stat Card ────────────────────────────────────────────────────
export function StatCard({ icon: Icon, label, value, sub, className }: {
  icon: any; label: string; value: number | string; sub?: string; className?: string
}) {
  return (
    <Card className={cn("p-5", className)}>
      <div className="flex items-center gap-3 mb-3">
        <div className="p-2 bg-primary/8 rounded-lg">
          <Icon className="w-4.5 h-4.5 text-primary" />
        </div>
        <span className="text-[13px] text-muted-foreground">{label}</span>
      </div>
      <div className="text-2xl font-semibold tracking-tight">{value}</div>
      {sub && <p className="text-[13px] text-muted-foreground mt-1">{sub}</p>}
    </Card>
  )
}

// ─── Button ───────────────────────────────────────────────────────
export function Button({ children, variant = 'primary', size = 'default', loading, className, ...props }: {
  children: React.ReactNode
  variant?: 'primary' | 'secondary' | 'ghost' | 'destructive'
  size?: 'sm' | 'default'
  loading?: boolean
  className?: string
} & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const variants = {
    primary: 'bg-primary text-primary-foreground hover:bg-primary/90 shadow-sm',
    secondary: 'bg-white border border-gray-200 text-foreground hover:bg-gray-50',
    ghost: 'text-muted-foreground hover:text-foreground hover:bg-gray-50',
    destructive: 'bg-destructive text-destructive-foreground hover:bg-destructive/90 shadow-sm',
  }
  const sizes = {
    sm: 'px-3 py-1.5 text-xs',
    default: 'px-4 py-2 text-sm',
  }
  return (
    <button
      className={cn(
        "inline-flex items-center justify-center gap-2 rounded-md font-medium transition-all duration-150 disabled:opacity-50 disabled:pointer-events-none",
        variants[variant],
        sizes[size],
        className,
      )}
      disabled={loading || props.disabled}
      {...props}
    >
      {loading && <Spinner className="w-3.5 h-3.5" />}
      {children}
    </button>
  )
}
