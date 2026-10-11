import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { readStorage, writeStorage } from '../lib/storage'
import type { ListItem } from '../lib/types'

/** Текущият списък се пази в браузъра – работи и без регистрация. */
interface ListState {
  items: ListItem[]
  /** Запазен списък на сървъра, който се редактира в момента. */
  savedList?: { id: number; name: string }
  add: (item: Omit<ListItem, 'id' | 'quantity'> & { quantity?: number }) => void
  update: (id: string, patch: Partial<ListItem>) => void
  remove: (id: string) => void
  replace: (items: Omit<ListItem, 'id'>[], savedList?: { id: number; name: string }) => void
  setSavedList: (saved?: { id: number; name: string }) => void
  clear: () => void
}

const ListContext = createContext<ListState | null>(null)

const newId = () => Math.random().toString(36).slice(2, 10)

export function ListProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ListItem[]>(() => readStorage<ListItem[]>('list', []))
  const [savedList, setSavedList] = useState<{ id: number; name: string } | undefined>(() =>
    readStorage<{ id: number; name: string } | undefined>('savedList', undefined),
  )

  useEffect(() => writeStorage('list', items), [items])
  useEffect(() => writeStorage('savedList', savedList), [savedList])

  const add: ListState['add'] = ({ categoryId, keyword, quantity = 1 }) => {
    const kw = keyword?.trim() || undefined
    setItems((prev) => {
      // Същият ред вече е в списъка → увеличаваме количеството
      const existing = prev.find(
        (i) => !i.unit && i.categoryId === categoryId && (i.keyword ?? '') === (kw ?? '').toLowerCase(),
      )
      if (existing) {
        return prev.map((i) => (i === existing ? { ...i, quantity: Math.min(99, i.quantity + quantity) } : i))
      }
      return [...prev, { id: newId(), categoryId, keyword: kw?.toLowerCase(), quantity }]
    })
  }

  const value: ListState = {
    items,
    savedList,
    add,
    update: (id, patch) => setItems((prev) => prev.map((i) => (i.id === id ? { ...i, ...patch } : i))),
    remove: (id) => setItems((prev) => prev.filter((i) => i.id !== id)),
    replace: (newItems, saved) => {
      setItems(newItems.map((i) => ({ ...i, id: newId() })))
      setSavedList(saved)
    },
    setSavedList,
    clear: () => {
      setItems([])
      setSavedList(undefined)
    },
  }
  return <ListContext.Provider value={value}>{children}</ListContext.Provider>
}

export function useList() {
  const ctx = useContext(ListContext)
  if (!ctx) throw new Error('useList must be used inside ListProvider')
  return ctx
}
