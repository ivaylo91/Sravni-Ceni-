import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faClipboardList, faFolderOpen, faTrash } from '@fortawesome/free-solid-svg-icons'
import { EmptyState, ErrorMessage, PageTitle, Spinner } from '../components/ui'
import { useCatalog } from '../context/CatalogContext'
import { useList } from '../context/ListContext'
import { api } from '../lib/api'
import { formatDateTime, pluralize } from '../lib/format'
import type { SavedList } from '../lib/types'

export function SavedListsPage() {
  const [lists, setLists] = useState<SavedList[]>()
  const [error, setError] = useState<string>()
  const { replace, savedList, setSavedList } = useList()
  const { category } = useCatalog()
  const navigate = useNavigate()

  useEffect(() => {
    api.lists().then(setLists).catch((e) => setError(e.message))
  }, [])

  const open = (list: SavedList) => {
    replace(list.items, { id: list.id, name: list.name })
    navigate('/list')
  }

  const remove = async (list: SavedList) => {
    if (!window.confirm(`Да изтрия ли списъка „${list.name}“?`)) return
    try {
      await api.deleteList(list.id)
      setLists((prev) => prev?.filter((l) => l.id !== list.id))
      if (savedList?.id === list.id) setSavedList(undefined)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <div>
      <PageTitle title="Запазени списъци" subtitle="Отворете списък, за да намерите най-евтината кошница" />
      {error && <ErrorMessage message={error} />}
      {!lists && !error && <Spinner />}
      {lists?.length === 0 && (
        <EmptyState icon={faClipboardList} title="Нямате запазени списъци">
          Създайте списък от „Моят списък“ и натиснете „Запази списъка“.
        </EmptyState>
      )}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {lists?.map((list) => (
          <article key={list.id} className="card flex flex-col p-5">
            <h2 className="font-bold break-words text-slate-900">{list.name}</h2>
            <p className="text-xs text-slate-500">
              {pluralize(list.items.length, 'продукт', 'продукта')} · {formatDateTime(list.updatedAt)}
            </p>
            <p className="mt-3 line-clamp-3 flex-1 text-sm text-slate-600">
              {list.items.map((i) => [category(i.categoryId)?.name, i.keyword].filter(Boolean).join(' ')).join(', ')}
            </p>
            <div className="mt-4 flex gap-2">
              <button className="btn-primary flex-1" onClick={() => open(list)}>
                <FontAwesomeIcon icon={faFolderOpen} /> Отвори
              </button>
              <button className="btn-secondary text-red-600" onClick={() => remove(list)} aria-label={`Изтрий ${list.name}`}>
                <FontAwesomeIcon icon={faTrash} />
              </button>
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}
