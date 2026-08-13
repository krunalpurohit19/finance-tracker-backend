# Personal Finance Manager

A native mobile personal finance app: manual transaction entry, accounts,
budgets, savings goals and analytics, on a financial data model designed to be
correct before it is convenient.

Full engineering plan: [docs/IMPLEMENTATION_PLAN.md](docs/IMPLEMENTATION_PLAN.md)

## Stack

| Layer   | Choice                                                 |
| ------- | ------------------------------------------------------ |
| Mobile  | Expo SDK 57 · React Native 0.86 · expo-router          |
| Styling | NativeWind 4 (Tailwind) + a TS token set               |
| API     | Hono 4 on Node 24                                      |
| Data    | Prisma 7 + `@prisma/adapter-pg` → PostgreSQL 17        |
| Shared  | TypeScript packages for domain logic and validation    |
| Testing | Vitest (unit) · testcontainers (integration, Phase 1+) |

## Layout

```
apps/
  api/         Hono API — thin transport over the service layer
  mobile/      Expo app (iOS + Android)
packages/
  db/          Prisma schema, migrations, client singleton
  domain/      Pure money/date/Result logic. No Prisma, no React.
  validation/  Zod schemas shared by the API and the app
docs/          Implementation plan
```

`packages/domain` is dependency-free on purpose: every calculation that has to
be correct lives there and is unit-tested with plain values.

## Getting started

Requires Node 20.19+ (24 recommended) and Docker.

```bash
cp .env.example .env          # then set BETTER_AUTH_SECRET
npm install
npm run db:up                 # PostgreSQL 17 on port 5433
npm run db:generate
npm run db:migrate

npm run api:dev               # http://localhost:4000
npm run mobile:dev            # Expo dev server
```

Check the API is alive:

```bash
curl localhost:4000/health/ready
```

## Commands

| Command              | Does                          |
| -------------------- | ----------------------------- |
| `npm test`           | Unit tests                    |
| `npm run typecheck`  | Typecheck every workspace     |
| `npm run lint`       | ESLint across the monorepo    |
| `npm run format`     | Prettier write                |
| `npm run db:up`      | Start PostgreSQL              |
| `npm run db:migrate` | Create and apply a migration  |
| `npm run db:studio`  | Prisma Studio                 |
| `npm run db:psql`    | psql shell into the container |

## Rules that are not negotiable

These exist because getting them wrong corrupts financial data silently.

1. **Money is never a `number`.** `NUMERIC(18,4)` at rest, `Decimal` in
   calculations, `string` across every boundary. `z.coerce.number()` on a
   monetary field is banned by lint rule.
2. **A transfer is not an expense.** One row, two account FKs, with a DB
   `CHECK` constraint making a malformed transfer unrepresentable.
3. **Dates are calendar dates.** `occurredOn` is a PostgreSQL `DATE`; "today"
   resolves server-side from the user's timezone, never a device clock.
4. **`userId` comes from the session, never from client input.** No request
   schema contains a `userId` field.
5. **Soft delete everywhere.** Every financial read filters `deletedAt`.
6. **Savings rate is `null` at zero income, not `0`.** The UI renders `—`.

## Status

All 12 phases complete. See `docs/HANDOFF.md` for status and `docs/RELEASE.md`
for shipping to the Play Store.

## Running it

```bash
npm run setup   # install deps, start PostgreSQL, generate client, migrate
npm start       # API + Expo together
```
