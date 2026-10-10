import type { ReactNode } from 'react'
import { BrowserRouter, Link, Navigate, Route, Routes } from 'react-router-dom'
import { faSignsPost } from '@fortawesome/free-solid-svg-icons'
import { Layout } from './components/Layout'
import { EmptyState, Spinner } from './components/ui'
import { ToastProvider } from './components/Toast'
import { AuthProvider, useAuth } from './context/AuthContext'
import { CatalogProvider } from './context/CatalogContext'
import { ListProvider } from './context/ListContext'
import { AdminPage } from './pages/AdminPage'
import { HomePage } from './pages/HomePage'
import { ListPage } from './pages/ListPage'
import { LoginPage } from './pages/LoginPage'
import { ProductPage } from './pages/ProductPage'
import { SavedListsPage } from './pages/SavedListsPage'
import { SearchPage } from './pages/SearchPage'

function RequireAuth({ children, admin = false }: { children: ReactNode; admin?: boolean }) {
  const { session, isAdmin, loading } = useAuth()
  if (loading) return <Spinner />
  if (!session) return <Navigate to="/login" replace />
  if (admin && !isAdmin) return <Navigate to="/" replace />
  return children
}

function NotFound() {
  return (
    <EmptyState icon={faSignsPost} title="Страницата не е намерена">
      <Link to="/" className="font-semibold text-brand-700 hover:underline">
        Към началната страница
      </Link>
    </EmptyState>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <CatalogProvider>
          <ListProvider>
            <ToastProvider>
              <Routes>
                <Route element={<Layout />}>
                  <Route index element={<HomePage />} />
                  <Route path="search" element={<SearchPage />} />
                  <Route path="product/:id" element={<ProductPage />} />
                  <Route path="list" element={<ListPage />} />
                  <Route path="login" element={<LoginPage />} />
                  <Route path="lists" element={<RequireAuth><SavedListsPage /></RequireAuth>} />
                  <Route path="admin" element={<RequireAuth admin><AdminPage /></RequireAuth>} />
                  <Route path="*" element={<NotFound />} />
                </Route>
              </Routes>
            </ToastProvider>
          </ListProvider>
        </CatalogProvider>
      </AuthProvider>
    </BrowserRouter>
  )
}
