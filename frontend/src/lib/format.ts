const eur = new Intl.NumberFormat('bg-BG', { style: 'currency', currency: 'EUR' })
const int = new Intl.NumberFormat('bg-BG')
const date = new Intl.DateTimeFormat('bg-BG', { day: 'numeric', month: 'long', year: 'numeric' })
const dateTime = new Intl.DateTimeFormat('bg-BG', { dateStyle: 'short', timeStyle: 'short' })

export const formatPrice = (value: number) => eur.format(value)
export const formatNumber = (value: number) => int.format(value)
export const formatDate = (iso: string) => date.format(new Date(iso))
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))

export const unitLabel = (unit?: string) => (unit === 'kg' ? 'кг' : unit === 'l' ? 'л' : 'бр.')

/** Бял или тъмен текст върху цвета на веригата – според яркостта на фона. */
export function textOn(hex: string | null): string {
  if (!hex) return '#ffffff'
  const n = parseInt(hex.slice(1), 16)
  const r = (n >> 16) & 255
  const g = (n >> 8) & 255
  const b = n & 255
  return 0.299 * r + 0.587 * g + 0.114 * b > 160 ? '#1f2937' : '#ffffff'
}

export function pluralize(n: number, one: string, many: string) {
  return `${formatNumber(n)} ${n === 1 ? one : many}`
}
