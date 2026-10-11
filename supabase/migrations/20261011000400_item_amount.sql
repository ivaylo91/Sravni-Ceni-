-- Ред от списък може да е в кг/л вместо в брой опаковки
alter table shopping_list_item
    add column amount numeric(7,3),
    add column unit varchar(3),
    add constraint chk_item_amount check (
        (amount is null and unit is null) or (amount > 0 and unit in ('kg', 'l')));
