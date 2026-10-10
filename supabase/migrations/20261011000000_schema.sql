-- =====================================================================
--  Сравни Цени – основна схема на базата данни (PostgreSQL / Supabase)
-- =====================================================================
--  Данните идват от отворените данни на КЗП „Колко струва“
--  (https://kolkostruva.bg/opendata). Всеки ден има ZIP с по един CSV
--  файл за всяка търговска верига.

create extension if not exists pg_trgm;

-- Търговски вериги (Kaufland, Lidl, Billa, ...)
create table chain (
    id          bigserial primary key,
    eik         varchar(20)  not null unique,       -- ЕИК от името на файла
    name        varchar(255) not null,              -- пълно име от файла
    short_name  varchar(120) not null,              -- показвано име
    color       varchar(7)                          -- цвят за UI, напр. #E10915
);

-- Населени места по ЕКАТТЕ
create table settlement (
    code  varchar(5)   primary key,
    name  varchar(120) not null
);

-- Групи категории (за менюто)
create table category_group (
    id          smallint     primary key,
    name        varchar(100) not null,
    icon        varchar(50)  not null,              -- име на FontAwesome икона
    sort_order  smallint     not null default 0
);

-- 101 продукта от „голямата потребителска кошница“ на КЗП
create table category (
    id        smallint     primary key,
    group_id  smallint     not null references category_group (id),
    name      varchar(150) not null
);

-- Търговски обекти (магазини)
create table store (
    id               bigserial    primary key,
    chain_id         bigint       not null references chain (id) on delete cascade,
    settlement_code  varchar(5)   not null references settlement (code),
    address          varchar(500) not null,
    constraint uq_store unique (chain_id, settlement_code, address)
);
create index idx_store_settlement on store (settlement_code);

-- Продукти – кодът е уникален само в рамките на веригата (няма EAN)
create table product (
    id            bigserial     primary key,
    chain_id      bigint        not null references chain (id) on delete cascade,
    code          varchar(64)   not null,
    name          varchar(500)  not null,
    category_id   smallint      not null references category (id),
    package_qty   numeric(10,3),                    -- количество в базова мярка
    package_unit  varchar(3),                       -- 'kg', 'l' или 'pcs'
    constraint uq_product unique (chain_id, code)
);
create index idx_product_category on product (category_id);
create index idx_product_name_trgm on product using gin (name gin_trgm_ops);

-- Последна известна цена на продукт в магазин
create table price (
    store_id     bigint        not null references store (id) on delete cascade,
    product_id   bigint        not null references product (id) on delete cascade,
    price        numeric(10,2) not null check (price > 0),
    promo_price  numeric(10,2) check (promo_price > 0),
    price_date   date          not null,
    primary key (store_id, product_id)
);
create index idx_price_product on price (product_id);

-- Дневна история (агрегирана по населено място, за да остане базата малка)
create table price_history (
    product_id       bigint        not null references product (id) on delete cascade,
    settlement_code  varchar(5)    not null references settlement (code),
    price_date       date          not null,
    min_price        numeric(10,2) not null,
    avg_price        numeric(10,2) not null,
    primary key (product_id, settlement_code, price_date)
);

-- Журнал на импортите
create table import_run (
    id             bigserial    primary key,
    price_date     date         not null,
    status         varchar(20)  not null,           -- RUNNING / SUCCESS / FAILED
    started_at     timestamptz  not null default now(),
    finished_at    timestamptz,
    chains         integer      not null default 0,
    rows_imported  integer      not null default 0,
    message        text
);

-- Списъци за пазаруване на потребителите (user_id = auth.users.id)
create table shopping_list (
    id          bigserial    primary key,
    user_id     uuid         not null,
    name        varchar(120) not null,
    created_at  timestamptz  not null default now(),
    updated_at  timestamptz  not null default now()
);
create index idx_shopping_list_user on shopping_list (user_id);

create table shopping_list_item (
    id           bigserial    primary key,
    list_id      bigint       not null references shopping_list (id) on delete cascade,
    category_id  smallint     references category (id),
    keyword      varchar(100),
    quantity     integer      not null default 1 check (quantity between 1 and 99),
    position     integer      not null default 0,
    constraint chk_item_criteria check (category_id is not null or keyword is not null)
);
create index idx_shopping_list_item_list on shopping_list_item (list_id);
