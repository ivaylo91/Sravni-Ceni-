export interface Settlement {
  code: string
  name: string
  storeCount: number
}

export interface Chain {
  id: number
  name: string
  color: string | null
  storeCount: number
}

export interface Category {
  id: number
  name: string
}

export interface CategoryGroup {
  id: number
  name: string
  icon: string
  categories: Category[]
}

export interface ProductOffer {
  productId: number
  name: string
  categoryId: number
  chainId: number
  chainName: string
  chainColor: string | null
  price: number
  regularPrice: number
  promo: boolean
  storeCount: number
  unitPrice?: number
  unit?: 'kg' | 'l'
  priceDate: string
}

export interface Page<T> {
  items: T[]
  page: number
  size: number
  hasMore: boolean
}

export interface StorePrice {
  storeId: number
  address: string
  settlementName: string
  price: number
  promoPrice?: number
  priceDate: string
}

export interface HistoryPoint {
  date: string
  minPrice: number
  avgPrice: number
}

export interface ProductDetail {
  productId: number
  name: string
  code: string
  categoryId: number
  categoryName: string
  chainId: number
  chainName: string
  chainColor: string | null
  packageQty?: number
  packageUnit?: string
  stores: StorePrice[]
  history: HistoryPoint[]
  alternatives: ProductOffer[]
}

export interface Stats {
  chains: number
  stores: number
  products: number
  prices: number
  lastUpdate?: string
}

/** Ред от списъка за пазаруване. */
export interface ListItem {
  id: string
  categoryId?: number
  keyword?: string
  quantity: number
}

export interface BasketLine {
  itemIndex: number
  productId: number
  productName: string
  price: number
  promo: boolean
  quantity: number
  lineTotal: number
  storeId: number
  chainName: string
  address: string
}

export interface StoreBasket {
  storeId: number
  chainId: number
  chainName: string
  chainColor: string | null
  address: string
  total: number
  foundCount: number
  missingItems: number[]
  lines: BasketLine[]
}

export interface BasketResult {
  settlementCode: string
  itemCount: number
  bestStores: StoreBasket[]
  bestPerChain: StoreBasket[]
  split: { total: number; storeCount: number; missingItems: number[]; lines: BasketLine[] }
  savings: number
}

export interface SavedList {
  id: number
  name: string
  createdAt: string
  updatedAt: string
  items: { categoryId?: number; keyword?: string; quantity: number }[]
}

export interface ImportRun {
  id: number
  priceDate: string
  status: 'RUNNING' | 'SUCCESS' | 'FAILED'
  startedAt: string
  finishedAt?: string
  chains: number
  rowsImported: number
  message?: string
}

export interface Me {
  id: string
  email?: string
  admin: boolean
}
