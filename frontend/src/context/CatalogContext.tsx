import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api } from '../lib/api'
import { readStorage, writeStorage } from '../lib/storage'
import type { Category, CategoryGroup, Settlement } from '../lib/types'

const SOFIA = '68134'

interface CatalogState {
  settlements: Settlement[]
  groups: CategoryGroup[]
  settlement: string
  settlementName: string
  setSettlement: (code: string) => void
  category: (id?: number) => (Category & { groupIcon: string }) | undefined
  error?: string
}

const CatalogContext = createContext<CatalogState | null>(null)

export function CatalogProvider({ children }: { children: ReactNode }) {
  const [settlements, setSettlements] = useState<Settlement[]>([])
  const [groups, setGroups] = useState<CategoryGroup[]>([])
  const [settlement, setSettlementState] = useState(() => readStorage('settlement', SOFIA))
  const [error, setError] = useState<string>()

  useEffect(() => {
    Promise.all([api.settlements(), api.categories()])
      .then(([s, g]) => {
        setSettlements(s)
        setGroups(g)
      })
      .catch((e) => setError(e.message))
  }, [])

  const setSettlement = useCallback((code: string) => {
    setSettlementState(code)
    writeStorage('settlement', code)
  }, [])

  const categoryIndex = useMemo(() => {
    const map = new Map<number, Category & { groupIcon: string }>()
    groups.forEach((g) => g.categories.forEach((c) => map.set(c.id, { ...c, groupIcon: g.icon })))
    return map
  }, [groups])

  const value: CatalogState = {
    settlements,
    groups,
    settlement,
    settlementName: settlements.find((s) => s.code === settlement)?.name ?? (settlement === SOFIA ? 'София' : '…'),
    setSettlement,
    category: (id) => (id === undefined ? undefined : categoryIndex.get(id)),
    error,
  }
  return <CatalogContext.Provider value={value}>{children}</CatalogContext.Provider>
}

export function useCatalog() {
  const ctx = useContext(CatalogContext)
  if (!ctx) throw new Error('useCatalog must be used inside CatalogProvider')
  return ctx
}
