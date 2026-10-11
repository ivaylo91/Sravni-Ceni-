import { supabase } from './supabase'
import type {
  BasketResult, CategoryGroup, Chain, ImportRun, ListItem, Me, Page, ProductDetail,
  ProductOffer, SavedList, Settlement, Stats,
} from './types'

const BASE = (import.meta.env.VITE_API_URL as string | undefined) ?? ''

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message)
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body) headers.set('Content-Type', 'application/json')
  // Токенът от Supabase Auth се проверява от Spring Security
  const session = supabase ? (await supabase.auth.getSession()).data.session : null
  if (session) headers.set('Authorization', `Bearer ${session.access_token}`)

  let res: Response
  try {
    res = await fetch(BASE + path, { ...init, headers })
  } catch {
    throw new ApiError(0, 'Няма връзка със сървъра. Опитайте отново.')
  }
  if (!res.ok) {
    let message = 'Възникна грешка. Опитайте отново.'
    if (res.status === 401) message = 'Моля, влезте в профила си.'
    else if (res.status === 403) message = 'Нямате достъп до тази страница.'
    try {
      const body = await res.json()
      if (body?.detail) message = body.detail
    } catch { /* няма тяло */ }
    throw new ApiError(res.status, message)
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

const qs = (params: Record<string, string | number | undefined | null>) => {
  const p = new URLSearchParams()
  Object.entries(params).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  })
  return p.toString()
}

const toItems = (items: ListItem[]) =>
  items.map(({ categoryId, keyword, quantity, amount, unit }) => ({
    categoryId,
    keyword: keyword || undefined,
    quantity,
    amount: unit ? amount : undefined,
    unit,
  }))

export type SortOrder = 'PRICE' | 'UNIT_PRICE' | 'NAME'

export const api = {
  stats: () => request<Stats>('/api/public/stats'),
  settlements: () => request<Settlement[]>('/api/public/settlements'),
  categories: () => request<CategoryGroup[]>('/api/public/categories'),
  chains: (settlement?: string) => request<Chain[]>(`/api/public/chains?${qs({ settlement })}`),
  search: (p: { q?: string; settlement?: string; category?: number; chain?: number; sort?: SortOrder; page?: number }) =>
    request<Page<ProductOffer>>(`/api/public/products?${qs(p)}`),
  product: (id: number, settlement?: string) =>
    request<ProductDetail>(`/api/public/products/${id}?${qs({ settlement })}`),
  basket: (settlementCode: string, items: ListItem[], chainIds?: number[]) =>
    request<BasketResult>('/api/public/basket', {
      method: 'POST',
      body: JSON.stringify({ settlementCode, items: toItems(items), chainIds }),
    }),

  me: () => request<Me>('/api/me'),
  lists: () => request<SavedList[]>('/api/lists'),
  createList: (name: string, items: ListItem[]) =>
    request<SavedList>('/api/lists', { method: 'POST', body: JSON.stringify({ name, items: toItems(items) }) }),
  updateList: (id: number, name: string, items: ListItem[]) =>
    request<SavedList>(`/api/lists/${id}`, { method: 'PUT', body: JSON.stringify({ name, items: toItems(items) }) }),
  deleteList: (id: number) => request<void>(`/api/lists/${id}`, { method: 'DELETE' }),

  imports: () => request<ImportRun[]>('/api/admin/imports'),
  startImport: (date?: string) => request<{ message: string }>(`/api/admin/imports?${qs({ date })}`, { method: 'POST' }),
}
