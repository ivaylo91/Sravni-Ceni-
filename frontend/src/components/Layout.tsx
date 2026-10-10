import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import {
  faBars, faBasketShopping, faCartShopping, faClipboardList, faDatabase, faMagnifyingGlass,
  faRightFromBracket, faUser, faXmark,
} from '@fortawesome/free-solid-svg-icons'
import { SettlementSelect } from './SettlementSelect'
import { useList } from '../context/ListContext'
import { useAuth } from '../context/AuthContext'
import { authEnabled } from '../lib/supabase'

export function Layout() {
  const { items } = useList()
  const { session, isAdmin, signOut } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()

  const link = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 rounded-xl px-3 py-2 text-sm font-semibold transition ${
      isActive ? 'bg-brand-50 text-brand-700' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900'
    }`

  const nav = (
    <>
      <NavLink to="/search" className={link} onClick={() => setMenuOpen(false)}>
        <FontAwesomeIcon icon={faMagnifyingGlass} /> Сравни цени
      </NavLink>
      <NavLink to="/list" className={link} onClick={() => setMenuOpen(false)}>
        <FontAwesomeIcon icon={faCartShopping} /> Моят списък
        {items.length > 0 && (
          <span className="rounded-full bg-brand-600 px-2 py-0.5 text-xs text-white">{items.length}</span>
        )}
      </NavLink>
      {session && (
        <NavLink to="/lists" className={link} onClick={() => setMenuOpen(false)}>
          <FontAwesomeIcon icon={faClipboardList} /> Запазени
        </NavLink>
      )}
      {isAdmin && (
        <NavLink to="/admin" className={link} onClick={() => setMenuOpen(false)}>
          <FontAwesomeIcon icon={faDatabase} /> Данни
        </NavLink>
      )}
    </>
  )

  const account = authEnabled && (
    session ? (
      <button
        className="btn-ghost"
        onClick={async () => {
          await signOut()
          navigate('/')
        }}
        title={session.user.email}
      >
        <FontAwesomeIcon icon={faRightFromBracket} /> Изход
      </button>
    ) : (
      <Link to="/login" className="btn-secondary" onClick={() => setMenuOpen(false)}>
        <FontAwesomeIcon icon={faUser} /> Вход
      </Link>
    )
  )

  return (
    <div className="flex min-h-screen flex-col">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl items-center gap-3 px-4 py-3">
          <Link to="/" className="flex shrink-0 items-center gap-2 text-lg font-extrabold text-slate-900">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-brand-600 text-white">
              <FontAwesomeIcon icon={faBasketShopping} />
            </span>
            <span className="hidden sm:inline">Сравни Цени</span>
          </Link>
          <nav className="ml-4 hidden items-center gap-1 lg:flex">{nav}</nav>
          <div className="ml-auto flex items-center gap-2">
            <SettlementSelect className="w-40 sm:w-48" />
            <div className="hidden lg:block">{account}</div>
            <button
              className="btn-ghost px-3 lg:hidden"
              onClick={() => setMenuOpen((o) => !o)}
              aria-label={menuOpen ? 'Затвори менюто' : 'Отвори менюто'}
              aria-expanded={menuOpen}
            >
              <FontAwesomeIcon icon={menuOpen ? faXmark : faBars} />
            </button>
          </div>
        </div>
        {menuOpen && (
          <nav className="flex flex-col gap-1 border-t border-slate-200 px-4 py-3 lg:hidden">
            {nav}
            <div className="pt-2">{account}</div>
          </nav>
        )}
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>

      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-6 text-sm text-slate-500">
          Цените са от отворените данни на{' '}
          <a className="font-semibold text-brand-700 hover:underline" href="https://kolkostruva.bg" target="_blank" rel="noreferrer">
            КЗП „Колко струва“
          </a>{' '}
          и се обновяват всеки ден. Възможни са разлики спрямо цените в магазина.
        </div>
      </footer>
    </div>
  )
}
