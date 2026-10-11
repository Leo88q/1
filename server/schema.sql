-- Схема Амперграда (Postgres). Применять: psql "$DATABASE_URL" -f server/schema.sql
create table if not exists challenges (
  nonce      text primary key,
  address    text not null,
  message    text not null,
  reward_id  text not null,
  engine     text not null,
  issued_at  timestamptz not null default now(),
  used_at    timestamptz
);

create table if not exists wallet_solves (
  address text not null,
  level   int  not null,
  at      timestamptz not null default now(),
  primary key (address, level)
);

create table if not exists wallet_dailies (
  address text not null,
  day     bigint not null,
  moves   int not null,
  at      timestamptz not null default now(),
  primary key (address, day)
);

create table if not exists claims (
  id        bigserial primary key,
  address   text not null,
  reward_id text not null,
  day       bigint not null,
  asset_id  text,
  signature text,
  at        timestamptz not null default now()
);
create index if not exists claims_address_idx on claims (address);
create index if not exists claims_reward_idx on claims (address, reward_id);

create table if not exists daily_scores (
  day     bigint not null,
  address text not null,
  moves   int not null,
  at      timestamptz not null default now(),
  primary key (day, address)
);
create index if not exists daily_scores_day_idx on daily_scores (day, moves);
