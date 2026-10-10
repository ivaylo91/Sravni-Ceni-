import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faMagnifyingGlass } from '@fortawesome/free-solid-svg-icons'

export function SearchBox({ initial = '', large = false }: { initial?: string; large?: boolean }) {
  const [q, setQ] = useState(initial)
  const navigate = useNavigate()
  useEffect(() => setQ(initial), [initial])

  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (q.trim()) navigate(`/search?q=${encodeURIComponent(q.trim())}`)
  }

  return (
    <form onSubmit={submit} role="search" className="relative flex w-full">
      <FontAwesomeIcon
        icon={faMagnifyingGlass}
        className={`pointer-events-none absolute top-1/2 -translate-y-1/2 text-slate-400 ${large ? 'left-5 text-lg' : 'left-4'}`}
      />
      <input
        type="search"
        value={q}
        onChange={(e) => setQ(e.target.value)}
        placeholder="Търсете продукт, напр. „прясно мляко верея“"
        aria-label="Търсене на продукт"
        maxLength={100}
        className={`input rounded-r-none ${large ? 'py-4 pl-13 text-base' : 'pl-11'}`}
      />
      <button type="submit" className={`btn-primary shrink-0 rounded-l-none ${large ? 'px-6 text-base' : ''}`}>
        Търси
      </button>
    </form>
  )
}
