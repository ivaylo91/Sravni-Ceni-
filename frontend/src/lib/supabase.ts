import { createClient, type SupabaseClient } from '@supabase/supabase-js'

const url = import.meta.env.VITE_SUPABASE_URL as string | undefined
const anonKey = import.meta.env.VITE_SUPABASE_ANON_KEY as string | undefined

/**
 * Supabase се ползва за вход/регистрация (Supabase Auth REST API).
 * Без конфигурация приложението работи, но само за гости.
 */
export const supabase: SupabaseClient | null = url && anonKey ? createClient(url, anonKey) : null

export const authEnabled = supabase !== null
