-- =====================================================================
--  Row Level Security (специфично за Supabase)
-- =====================================================================
--  Supabase публикува всяка таблица в схема public през REST API
--  (PostgREST) с публичния anon ключ. Без RLS всеки би могъл да чете и
--  променя данните директно. Затова:
--    * каталогът (вериги, магазини, продукти, цени) е само за четене;
--    * списъците за пазаруване са видими само за собственика им;
--    * import_run е недостъпна през REST API.
--  Spring Boot бекендът се свързва като собственик на таблиците и не се
--  влияе от тези политики.

alter table chain              enable row level security;
alter table settlement         enable row level security;
alter table category_group     enable row level security;
alter table category           enable row level security;
alter table store              enable row level security;
alter table product            enable row level security;
alter table price              enable row level security;
alter table price_history      enable row level security;
alter table import_run         enable row level security;
alter table shopping_list      enable row level security;
alter table shopping_list_item enable row level security;

-- Публично четене на каталога
create policy "catalog_read" on chain          for select to anon, authenticated using (true);
create policy "catalog_read" on settlement     for select to anon, authenticated using (true);
create policy "catalog_read" on category_group for select to anon, authenticated using (true);
create policy "catalog_read" on category       for select to anon, authenticated using (true);
create policy "catalog_read" on store          for select to anon, authenticated using (true);
create policy "catalog_read" on product        for select to anon, authenticated using (true);
create policy "catalog_read" on price          for select to anon, authenticated using (true);
create policy "catalog_read" on price_history  for select to anon, authenticated using (true);

-- Списъци: само собственикът
create policy "own_lists" on shopping_list
    for all to authenticated
    using (user_id = (select auth.uid()))
    with check (user_id = (select auth.uid()));

create policy "own_list_items" on shopping_list_item
    for all to authenticated
    using (exists (select 1 from shopping_list l
                   where l.id = list_id and l.user_id = (select auth.uid())))
    with check (exists (select 1 from shopping_list l
                        where l.id = list_id and l.user_id = (select auth.uid())));

-- Каталогът се пише само от бекенда
revoke insert, update, delete on chain, settlement, category_group, category,
    store, product, price, price_history, import_run from anon, authenticated;
