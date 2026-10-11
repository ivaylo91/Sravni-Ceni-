-- Частен bucket за архива на дневните ZIP файлове от КЗП (специфично за Supabase).
-- Достъп само със service_role / secret ключа на бекенда – без публични политики.
insert into storage.buckets (id, name, public)
values ('kzp-archive', 'kzp-archive', false)
on conflict (id) do nothing;
