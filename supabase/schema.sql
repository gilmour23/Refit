-- Re:Fit V1.0 Supabase schema
-- Run in a Supabase project after reviewing the target project.

create extension if not exists pgcrypto;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text,
    birth_year smallint check (
        birth_year is null or
        birth_year between 1900 and extract(year from now())::int
    ),
    affected_side text check (
        affected_side is null or
        affected_side in ('left', 'right', 'bilateral', 'unknown')
    ),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.rehab_sessions (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    client_local_id text not null,
    created_at timestamptz not null,
    exercise_type text not null,
    duration_seconds bigint not null check (duration_seconds >= 0),
    sample_count integer not null check (sample_count >= 0),
    rms real not null,
    mav real not null,
    peak_activation real not null,
    activation_duration real,
    updated_at timestamptz not null default now(),
    constraint rehab_sessions_user_client_unique
        unique (user_id, client_local_id)
);

alter table public.profiles enable row level security;
alter table public.rehab_sessions enable row level security;

grant select, insert, update, delete
on public.profiles, public.rehab_sessions
to authenticated;

drop policy if exists "profiles_select_own" on public.profiles;
create policy "profiles_select_own"
on public.profiles
for select
to authenticated
using ((select auth.uid()) = id);

drop policy if exists "profiles_insert_own" on public.profiles;
create policy "profiles_insert_own"
on public.profiles
for insert
to authenticated
with check ((select auth.uid()) = id);

drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_update_own"
on public.profiles
for update
to authenticated
using ((select auth.uid()) = id)
with check ((select auth.uid()) = id);

drop policy if exists "profiles_delete_own" on public.profiles;
create policy "profiles_delete_own"
on public.profiles
for delete
to authenticated
using ((select auth.uid()) = id);

drop policy if exists "rehab_sessions_select_own" on public.rehab_sessions;
create policy "rehab_sessions_select_own"
on public.rehab_sessions
for select
to authenticated
using ((select auth.uid()) = user_id);

drop policy if exists "rehab_sessions_insert_own" on public.rehab_sessions;
create policy "rehab_sessions_insert_own"
on public.rehab_sessions
for insert
to authenticated
with check ((select auth.uid()) = user_id);

drop policy if exists "rehab_sessions_update_own" on public.rehab_sessions;
create policy "rehab_sessions_update_own"
on public.rehab_sessions
for update
to authenticated
using ((select auth.uid()) = user_id)
with check ((select auth.uid()) = user_id);

drop policy if exists "rehab_sessions_delete_own" on public.rehab_sessions;
create policy "rehab_sessions_delete_own"
on public.rehab_sessions
for delete
to authenticated
using ((select auth.uid()) = user_id);

create index if not exists rehab_sessions_user_created_idx
on public.rehab_sessions(user_id, created_at desc);
