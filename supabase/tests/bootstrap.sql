-- Disposable PostgreSQL test database ONLY; never run this against Supabase.
\set ON_ERROR_STOP on
do $$ begin
  if current_database() <> 'refit_test' then
    raise exception 'Use disposable database refit_test';
  end if;
end $$;
create role authenticated nologin nobypassrls;
create role anon nologin nobypassrls;
create schema auth;
create table auth.users (id uuid primary key);
-- Match the ownership claim semantics used by Supabase auth.uid().
create function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid;
$$;
grant usage on schema auth, public to authenticated, anon;
-- Reproduce hosted Supabase's permissive table defaults so schema.sql must
-- explicitly remove privileges that bypass row-level security (e.g. TRUNCATE).
alter default privileges in schema public grant all on tables to authenticated, anon;
