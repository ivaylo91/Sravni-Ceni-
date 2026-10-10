// localStorage може да липсва (частен режим, блокирани бисквитки) – тогава просто не помним.

export function readStorage<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(`sravni-ceni:${key}`)
    return raw === null ? fallback : (JSON.parse(raw) as T)
  } catch {
    return fallback
  }
}

export function writeStorage<T>(key: string, value: T) {
  try {
    localStorage.setItem(`sravni-ceni:${key}`, JSON.stringify(value))
  } catch {
    /* няма достъп до localStorage */
  }
}
