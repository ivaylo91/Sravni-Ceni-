import { createContext, useCallback, useContext, useRef, useState, type ReactNode } from 'react'
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'
import { faCircleCheck } from '@fortawesome/free-solid-svg-icons'

const ToastContext = createContext<(message: string) => void>(() => {})

/** Кратко съобщение в долната част на екрана („Добавено в списъка“). */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string>()
  const timer = useRef<number>(undefined)

  const show = useCallback((text: string) => {
    setMessage(text)
    window.clearTimeout(timer.current)
    timer.current = window.setTimeout(() => setMessage(undefined), 2500)
  }, [])

  return (
    <ToastContext.Provider value={show}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-6 z-50 flex justify-center px-4">
        {message && (
          <div className="flex items-center gap-2 rounded-xl bg-slate-900 px-4 py-3 text-sm font-medium text-white shadow-lg">
            <FontAwesomeIcon icon={faCircleCheck} className="text-brand-500" />
            {message}
          </div>
        )}
      </div>
    </ToastContext.Provider>
  )
}

export const useToast = () => useContext(ToastContext)
