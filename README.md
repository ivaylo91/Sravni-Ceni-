# Сравни Цени: grocery price comparison for Bulgaria

A web app, in Bulgarian, that compares supermarket prices in your town, builds a shopping list and finds the **cheapest basket**. Prices come from the official KZP open data ([kolkostruva.bg](https://kolkostruva.bg)) and are refreshed every day.

**Stack:**
- Spring Boot 3.5 · Spring Security (OAuth2 resource server) · Spring Data JPA · Maven
- React 19 · TypeScript · Vite · TailwindCSS 4 · FontAwesome
- Supabase (Postgres + Auth REST API + RLS)

See [docs/RESEARCH.md](docs/RESEARCH.md) for the market and data research behind the design.

## Features

- **Compare prices** of any product in your town. You get the lowest price per chain, promotions, the number of stores, and the unit price (€/кг, €/л).
- **Browse the 101 KZP basket categories**, grouped into 16 sections.
- **Product page** with prices in every store, 60‑day price history, and similar products in other chains.
- **Shopping list** (category + optional keyword + quantity). It works without an account, stored in the browser.
  Each item's quantity is either a number of packs (бр.) or an amount in **kg or litres**. With an amount, the basket compares what it costs to reach that amount, e.g. 5 l of oil as 1 × 5 l or 5 × 1 l, instead of comparing single-pack prices.
- **Cheapest basket**, recalculated as you edit the list:
  - the best single store;
  - the best store per chain;
  - split shopping across several stores;
  - your savings.

  You can exclude chains.
- **Accounts** (Supabase Auth): save lists to the cloud and open them on any device.
- **Admin page**: import history and manual import for a chosen date.
- **Daily import** from GitHub Actions, so it works even when the web server is asleep or off.
- **Daily ZIP archive** in Supabase Storage. KZP keeps only about 14 days online.

## Project structure

```
backend/     Spring Boot REST API (Java 21, Maven wrapper included)
frontend/    React + Vite + Tailwind SPA
supabase/    SQL migrations (schema, reference data, RLS policies)
docs/        Research
```

## Quick start (no Supabase needed)

This runs the backend on an **embedded PostgreSQL** and imports yesterday's prices from KZP on first start. Search, compare and basket work fully; login needs Supabase.

```bash
# Terminal 1 – backend on http://localhost:8080 (first start downloads ~20 MB and imports ~580k prices in ~30 s)
cd backend
./mvnw spring-boot:test-run          # Windows: mvnw.cmd spring-boot:test-run

# Terminal 2 – frontend on http://localhost:5173
cd frontend
npm install
npm run dev
```

The dev database lives in `backend/.dev-db`. Delete that folder to start fresh.

## Setup with Supabase

1. **Create a project** at [supabase.com](https://supabase.com). The `eu-central-1` (Frankfurt) region is closest to Bulgaria.
2. **Apply the migrations** in `supabase/migrations`, in order. Either paste each file into the SQL Editor, or use the CLI:
   ```bash
   supabase link --project-ref <project-ref>
   supabase db push
   ```
3. **Auth.** Go to Authentication → Providers and enable Email. Set the Site URL to your frontend URL.
4. **Backend.** Copy `backend/.env.example` to `backend/.env` and fill it in:
   - DB connection: use the **Session pooler** string, port 5432.
   - `SUPABASE_URL`.
   - Your e-mail in `ADMIN_EMAILS`.

   Then run:
   ```bash
   cd backend && ./mvnw spring-boot:run
   ```
5. **Frontend.** Copy `frontend/.env.example` to `frontend/.env`, set `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY` (the publishable key), then run `npm run dev`.
6. **First data.** Sign in with the admin e-mail, open **Данни** and click **Импортирай**, or call `POST /api/admin/imports`.

### How auth works

1. The browser signs in through Supabase Auth with `supabase-js` and gets a JWT.
2. Every API call sends `Authorization: Bearer <jwt>`.
3. Spring Security verifies the signature against `SUPABASE_URL/auth/v1/.well-known/jwks.json` and checks the issuer (`…/auth/v1`) and audience (`authenticated`). Legacy projects can set `SUPABASE_JWT_SECRET` (HS256) instead.
4. The user id is the token's `sub`. A user is admin if their e-mail is in `ADMIN_EMAILS`, or if `app_metadata.role = "admin"`.

Supabase exposes all tables over its REST API, so **RLS is enabled everywhere**:
- the catalog is read-only;
- shopping lists are owner-only.

## Deployment

| Part | Where | Config |
|---|---|---|
| Database + Auth + Storage | Supabase | `supabase/migrations/*` |
| Backend (Docker) | Render, or any Docker host | [`render.yaml`](render.yaml), [`backend/Dockerfile`](backend/Dockerfile) |
| Frontend | Vercel | [`frontend/vercel.json`](frontend/vercel.json). It proxies `/api/*` to the backend, so no CORS setup is needed |
| Daily import + archive | GitHub Actions | [`.github/workflows/daily-import.yml`](.github/workflows/daily-import.yml) |
| Tests on every push | GitHub Actions | [`.github/workflows/ci.yml`](.github/workflows/ci.yml) |

1. **Backend on Render.** Go to New → Blueprint, pick this repository, and fill in the `sync: false` variables (the same values as `backend/.env`).
2. **Frontend on Vercel.**
   - Import the repository with **Root Directory** `frontend`.
   - Set `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`.
   - If your Render URL is not `sravni-ceni-api.onrender.com`, update it in `frontend/vercel.json`.
3. **Supabase Auth.** Set the Site URL and redirect URLs to the Vercel domain.
4. **GitHub.** Go to Settings → Secrets and variables → Actions and add these secrets:
   - `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`;
   - `SUPABASE_URL`;
   - `SUPABASE_SERVICE_ROLE_KEY` (the secret key, needed for the ZIP archive).

   The import runs every morning. You can also run it by hand (Actions → Daily price import → Run workflow), with `yesterday`, a single date, or a range like `2026-09-27..2026-10-09`.

### One-off import from the command line

```bash
cd backend && ./mvnw -q -DskipTests package
java -jar target/sravni-ceni-backend-*.jar --spring.main.web-application-type=none --app.import.run-once=yesterday
```

The importer looks for each date's ZIP in this order:
1. `backend/.kzp-archive/<date>.zip`, if present;
2. kolkostruva.bg (and then copies it to the Supabase Storage archive);
3. the Supabase Storage archive.

Importing an older date only adds price history; it never overwrites newer current prices.

## REST API

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/api/public/stats` | – | Chains / stores / products / last update |
| GET | `/api/public/settlements` | – | Towns that have stores |
| GET | `/api/public/categories` | – | 16 groups with 101 categories |
| GET | `/api/public/chains?settlement=` | – | Chains in a town |
| GET | `/api/public/products?q=&settlement=&category=&chain=&sort=PRICE\|UNIT_PRICE\|NAME&page=&size=` | – | Search; every word must match |
| GET | `/api/public/products/{id}?settlement=` | – | Store prices, history, alternatives |
| POST | `/api/public/basket` | – | `{settlementCode, items:[{categoryId?, keyword?, quantity, amount?, unit?: "kg"\|"l"}], chainIds?}` |
| GET | `/api/me` | user | Current user and admin flag |
| GET/POST | `/api/lists` | user | Saved lists |
| GET/PUT/DELETE | `/api/lists/{id}` | user | One saved list (owner only, 404 otherwise) |
| GET/POST | `/api/admin/imports?date=` | admin | Import history / start an import |

Errors come back as RFC 9457 Problem Details, with Bulgarian messages in `detail`.

## Configuration (backend environment variables)

| Variable | Default | Meaning |
|---|---|---|
| `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` | local Supabase | Postgres connection |
| `SUPABASE_URL` | `http://127.0.0.1:54321` | Used to verify JWTs |
| `SUPABASE_JWT_SECRET` | – | Only for the legacy HS256 secret |
| `ADMIN_EMAILS` | – | Comma-separated admin e-mails |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Frontend origin(s) |
| `IMPORT_ENABLED` / `IMPORT_CRON` | `true` / `0 30 6 * * *` | Daily import |
| `IMPORT_CHAINS` | 11 supermarket chains (EIKs) | Empty = all ~70 chains (~1.4M rows/day; too big for the Supabase free tier) |
| `IMPORT_HISTORY_DAYS` | `60` | How many days of price history to keep |
| `IMPORT_ON_STARTUP` | `true` | Import yesterday's prices on startup when the database is empty |
| `IMPORT_RUN_ONCE` | – | `yesterday` / date / range: import, then exit |
| `KZP_LOCAL_ARCHIVE` | `.kzp-archive` | Local folder with already-downloaded ZIPs |
| `SUPABASE_SERVICE_ROLE_KEY` | – | Secret key used to archive ZIPs in Supabase Storage (`kzp-archive` bucket). Server only |

## Tests

```bash
cd backend && ./mvnw test
```

There are 31 tests. CI runs them on every push:
- the CSV parser, using quirks taken from the real KZP files;
- package-size parsing;
- a full integration test on a real embedded PostgreSQL: import → search → basket (including kg/l amounts) → history backfill → saved lists with security → admin access.

## Data caveats

- There is no barcode in the data, so cross-chain matching is by KZP category + keywords. The basket shows exactly which product was picked in each store.
- Metro is cash & carry and needs a customer card.
- KZP occasionally publishes broken files. For example, on 8–9 Oct 2026 Kaufland's file was a copy of Lilly Drogerie's. The importer detects identical files and keeps the previous day's prices for the affected chain.
