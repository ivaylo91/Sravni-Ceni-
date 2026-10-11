# Research: grocery price comparison in Bulgaria (October 2026)

## 1. Context

- **Euro adoption.** Bulgaria switched to the euro on **1 January 2026**. All prices in the data are in EUR, so the app shows EUR only (format `1,59 €`, `bg-BG` locale).
- **Legal basis for the data.** Under **Art. 55b of the Euro Introduction Act**, retail chains with prior-year turnover above 10 million BGN must report their prices to the Consumer Protection Commission (КЗП) every day. This covers food, drinks, tobacco, non-food goods and medicines.
- **Official portal.** The data is published at [kolkostruva.bg](https://kolkostruva.bg) („Колко струва“). The portal shows one product in one town at a time. It has no shopping list and no basket optimisation, and that gap is what this app fills.

## 2. Data source: KZP open data

| Item | Finding |
|---|---|
| URL | `https://kolkostruva.bg/opendata_files/YYYY-MM-DD.zip` (list at `/opendata`) |
| Publication | The file for day D appears at about 00:01 on D+1. Only the last ~14 days stay online. |
| Size | ~20 MB ZIP per day, with one CSV per chain (**74 files** on 2026-10-09) |
| File name | `Trade name (Company)_EIK.csv`, e.g. `T Market (Максима България ЕООД)_131324923.csv` |
| Columns | `Населено място` (EKATTE code), `Търговски обект`, `Наименование на продукта`, `Код на продукта`, `Категория` (1–101), `Цена на дребно`, `Цена в промоция` |
| Volume | ~1.4M rows/day across all chains. The 11 supermarket chains imported by default give **~580k prices, ~640 stores, ~15k products, ~170 settlements** |
| Categories | 101 products from the KZP "big consumer basket", from bread and milk to medicines. The names were reconstructed from the data in `supabase/migrations/…_reference_data.sql` |

### Data-quality issues found in the real files (and how the importer handles them)

| Issue | Example | Handling |
|---|---|---|
| Different delimiters | Lidl uses quoted `,`, others use `;` | Delimiter is detected from the header line |
| UTF-8 BOM | Some files | Stripped |
| Stray quotes inside fields | `Кашкавал "Витоша 400г` | Custom lenient CSV reader that works like Python's `csv` module, so one bad quote does not reject the whole file |
| EKATTE variations | `068134`, `68134-04`, `7079` | Normalised to 5 digits |
| "Promo price" of `0` or ≥ regular | Metro sends `0` | Treated as no promotion |
| Prices with 4 decimals | `1.3800` | Rounded to 2 |
| Category outside 1–101 | `150`, `1972047017` | Row skipped and counted |
| **One chain's file is a copy of another's** | This is common. Of the 14 days from 27 Sep to 10 Oct 2026, **Billa's file held the pharmacy Нове Фарм's prices on 8 days**, and **Kaufland's held another chain's prices on 4 days** (Жанет, Богат Беден, Lilly Drogerie). Without a check, Billa would show medicine prices on most days. | Files with identical content in the same ZIP are skipped. The chain keeps its previous day's prices and a warning is recorded in `import_run.message` |
| No EAN/barcode | Each chain has its own product codes | Products are matched across chains by **KZP category + keywords** (see §4) |

Other things worth knowing:
- **Metro** is a cash & carry chain and requires a customer card. Its prices can look very low.
- **eBag** is an online supermarket. Its "store" is the website.
- The data contains no promotional brochures or loyalty-card prices (Lidl Plus, Kaufland Card), only the reported retail and promo price.

## 3. Existing solutions

| Solution | What it does | Gap |
|---|---|---|
| kolkostruva.bg (КЗП) | Lowest price per chain for one product in one town | No list, no basket, no unit prices |
| Leuro (app, per its Product Hunt listing) | Tracks weekly brochures of Kaufland, Lidl, Billa and Fantastico, and scans receipts. €7.99/month | Paid, brochure-based, does not cover all chains. Not independently verified |
| electionsbg.com price index (open source) | Builds a price index from the same KZP data | Analytics only, not a shopping tool |

**How this app differs:** a free, data-driven basket optimiser that compares **every store** in the town, plus split shopping across several stores, unit prices (€/kg, €/l), price history and saved lists.

## 4. Design decisions

- **Cross-chain matching.** There is no EAN, so a list item is *category + optional keyword* (e.g. „Прясно мляко“ + „верея“). For each store the cheapest product matching all keywords is chosen. This is predictable and the user can see which product was picked. On a product page, the keyword is taken from the product name without the category words (usually the brand).
- **Basket algorithm.** For each item, one SQL query returns the cheapest match per store (`DISTINCT ON (store_id)`). Stores are ranked by coverage first, then total. The report shows:
  - the best store overall;
  - the best store per chain;
  - a "split" basket (each item where it is cheapest);
  - savings between the cheapest and most expensive chain with the same coverage.

  On real data, a 6–7 item basket for Sofia (224 stores) is computed in about 0.5 s.
- **Unit prices.** The package size is parsed from the name (`500 Гр.`, `1 Л.`, `2Х5КГ`, `на кг`) so products of different sizes can be compared per kg or per litre.
- **Storage size.** Only the latest price per (store, product) is kept, plus daily min/avg history per settlement (90 days by default). A full day of all chains does not fit the Supabase free tier (500 MB), so the default import is limited to the 11 supermarket chains (`IMPORT_CHAINS`).
- **Supabase usage.**
  - **Postgres** is the database for Spring Data JPA.
  - **Supabase Auth (REST API via `supabase-js`)** handles sign-up and login.
  - **Spring Security** acts as an OAuth2 resource server. It verifies Supabase JWTs with the project's JWKS (ES256/RS256), or with the legacy HS256 secret, and checks issuer and audience.
- **Security.** Supabase exposes every `public` table over its REST API with the public anon key, so **RLS is enabled on all tables**:
  - the catalog is read-only;
  - shopping lists are visible only to their owner (`auth.uid()`);
  - `import_run` is hidden.

  The backend also checks list ownership itself and returns 404 for other users' lists.

## 5. Possible next steps

1. Better product matching: normalise brands and sizes, or use trigram clustering like the electionsbg catalogue.
2. "Nearest store" using geocoded store addresses. KZP gives only free-text addresses.
3. Price-drop alerts for saved lists (Supabase Edge Function or Spring scheduler + e-mail).
4. PWA and offline shopping-list mode.
5. Cache the catalog endpoints (Caffeine) and move history to a partitioned table if all chains are imported.
6. Archive the daily ZIPs (the portal keeps only ~14 days).

## Sources

- [kolkostruva.bg – „Колко струва“, КЗП](https://kolkostruva.bg) and [open data](https://kolkostruva.bg/opendata)
- [BNT: new retail price platform kolkostruva.bg](https://bntnews.bg/news/consumer-basket-new-retail-price-platform-kolkostruvabg-to-be-launched-1358188news.html)
- [Sofia Globe: Consumer protection body launches prices website](https://sofiaglobe.com/?p=124932)
- [Sofia Globe: food price comparison platform](https://sofiaglobe.com/?p=111889)
- [Leuro on Product Hunt](https://www.producthunt.com/p/leuro)
- [electionsbg „update-prices“ skill (KZP data structure notes)](https://skillsmp.com/creators/atanasster/electionsbg/claude-skills-update-prices)
- The CSV findings above come from analysing the real ZIP files for 2026-10-08, 2026-10-09 and 2026-10-10.
