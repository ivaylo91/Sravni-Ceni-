import { useCallback, useEffect, useState } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCloudArrowDown, faRotate } from '@fortawesome/free-solid-svg-icons'
import { ErrorMessage, PageTitle, Spinner } from '../components/ui'
import { useToast } from '../components/Toast'
import { api } from '../lib/api'
import { formatDate, formatDateTime, formatNumber } from '../lib/format'
import type { ImportRun } from '../lib/types'

const STATUS: Record<ImportRun['status'], [string, string]> = {
  RUNNING: ['Тече', 'bg-amber-100 text-amber-800'],
  SUCCESS: ['Успешен', 'bg-brand-100 text-brand-800'],
  FAILED: ['Неуспешен', 'bg-red-100 text-red-800'],
}

/** Импорт на данните от КЗП (само за администратори). */
export function AdminPage() {
  const [runs, setRuns] = useState<ImportRun[]>()
  const [date, setDate] = useState('')
  const [error, setError] = useState<string>()
  const toast = useToast()

  const load = useCallback(() => {
    api.imports().then(setRuns).catch((e) => setError(e.message))
  }, [])

  useEffect(load, [load])

  const start = async () => {
    setError(undefined)
    try {
      const res = await api.startImport(date || undefined)
      toast(res.message)
      setTimeout(load, 1500)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  return (
    <div>
      <PageTitle
        title="Данни"
        subtitle="Импорт на дневните цени от kolkostruva.bg (автоматично всеки ден в 06:30)"
        actions={
          <button className="btn-ghost" onClick={load}>
            <FontAwesomeIcon icon={faRotate} /> Обнови
          </button>
        }
      />
      <div className="card mb-6 flex flex-wrap items-end gap-3 p-4">
        <label className="block">
          <span className="mb-1 block text-sm font-semibold text-slate-700">Дата (празно = вчера)</span>
          <input type="date" className="input" value={date} onChange={(e) => setDate(e.target.value)} />
        </label>
        <button className="btn-primary" onClick={start}>
          <FontAwesomeIcon icon={faCloudArrowDown} /> Импортирай
        </button>
      </div>
      {error && <ErrorMessage message={error} />}
      {!runs && !error && <Spinner />}
      {runs && (
        <div className="card overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-slate-200 text-xs text-slate-500 uppercase">
              <tr>
                <th className="px-4 py-3">Дата на цените</th>
                <th className="px-4 py-3">Статус</th>
                <th className="px-4 py-3">Вериги</th>
                <th className="px-4 py-3">Цени</th>
                <th className="px-4 py-3">Стартиран</th>
                <th className="px-4 py-3">Бележки</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {runs.map((r) => (
                <tr key={r.id}>
                  <td className="px-4 py-3 font-semibold whitespace-nowrap">{formatDate(r.priceDate)}</td>
                  <td className="px-4 py-3">
                    <span className={`rounded-md px-2 py-0.5 text-xs font-bold ${STATUS[r.status][1]}`}>{STATUS[r.status][0]}</span>
                  </td>
                  <td className="price px-4 py-3">{r.chains}</td>
                  <td className="price px-4 py-3">{formatNumber(r.rowsImported)}</td>
                  <td className="px-4 py-3 whitespace-nowrap text-slate-500">{formatDateTime(r.startedAt)}</td>
                  <td className="px-4 py-3 text-xs whitespace-pre-line text-slate-500">{r.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
