import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faEnvelope, faLock } from '@fortawesome/free-solid-svg-icons'
import { ErrorMessage } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { supabase } from '../lib/supabase'

const MESSAGES: Record<string, string> = {
  'Invalid login credentials': 'Грешен имейл или парола.',
  'Email not confirmed': 'Потвърдете имейла си от писмото, което ви изпратихме.',
  'User already registered': 'Вече има профил с този имейл.',
}

/** Вход и регистрация през Supabase Auth. */
export function LoginPage() {
  const { session } = useAuth()
  const navigate = useNavigate()
  const [mode, setMode] = useState<'signin' | 'signup'>('signin')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()
  const [info, setInfo] = useState<string>()

  const auth = supabase?.auth
  if (!auth) return <ErrorMessage message="Входът не е конфигуриран (липсват VITE_SUPABASE_URL и VITE_SUPABASE_ANON_KEY)." />
  if (session) return <Navigate to="/list" replace />

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(undefined)
    setInfo(undefined)
    const { data, error } =
      mode === 'signin'
        ? await auth.signInWithPassword({ email, password })
        : await auth.signUp({ email, password, options: { emailRedirectTo: window.location.origin } })
    setBusy(false)
    if (error) {
      setError(MESSAGES[error.message] ?? error.message)
    } else if (mode === 'signup' && !data.session) {
      setInfo('Изпратихме ви имейл за потвърждение. След това влезте с паролата си.')
      setMode('signin')
    } else {
      navigate('/list')
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <div className="card p-6 sm:p-8">
        <div className="mb-6 grid grid-cols-2 rounded-xl bg-slate-100 p-1 text-sm font-semibold">
          {(['signin', 'signup'] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              className={`rounded-lg py-2 transition ${mode === m ? 'bg-white text-slate-900 shadow-sm' : 'text-slate-500'}`}
            >
              {m === 'signin' ? 'Вход' : 'Регистрация'}
            </button>
          ))}
        </div>
        <form onSubmit={submit} className="space-y-4">
          <label className="block">
            <span className="mb-1 block text-sm font-semibold text-slate-700">Имейл</span>
            <div className="relative">
              <FontAwesomeIcon icon={faEnvelope} className="absolute top-1/2 left-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="email"
                required
                autoComplete="email"
                className="input pl-10"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
              />
            </div>
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-semibold text-slate-700">Парола</span>
            <div className="relative">
              <FontAwesomeIcon icon={faLock} className="absolute top-1/2 left-3.5 -translate-y-1/2 text-slate-400" />
              <input
                type="password"
                required
                minLength={8}
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                className="input pl-10"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            {mode === 'signup' && <span className="mt-1 block text-xs text-slate-500">Поне 8 символа.</span>}
          </label>
          {error && <ErrorMessage message={error} />}
          {info && <div className="rounded-xl bg-brand-50 p-3 text-sm text-brand-800">{info}</div>}
          <button type="submit" className="btn-primary w-full" disabled={busy}>
            {busy ? 'Моля, изчакайте…' : mode === 'signin' ? 'Вход' : 'Създай профил'}
          </button>
        </form>
      </div>
      <p className="mt-4 text-center text-sm text-slate-500">
        Профилът ви позволява да запазвате списъци и да ги отваряте от всяко устройство.
      </p>
    </div>
  )
}
