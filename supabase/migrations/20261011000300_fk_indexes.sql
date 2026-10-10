-- Индекси за външните ключове (препоръка на Supabase Performance Advisor)
create index if not exists idx_category_group on category (group_id);
create index if not exists idx_price_history_settlement on price_history (settlement_code);
create index if not exists idx_shopping_list_item_category on shopping_list_item (category_id);
