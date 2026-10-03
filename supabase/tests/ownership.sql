-- Local: run bootstrap.sql and schema.sql first in disposable refit_test.
-- Hosted: run this transaction only, without bootstrap; all fixtures roll back.
\set ON_ERROR_STOP on
begin;
do $$ declare table_name text; role_name text; privilege_name text; begin
  foreach table_name in array array['public.profiles', 'public.rehab_sessions'] loop
    foreach privilege_name in array array['SELECT', 'INSERT', 'UPDATE', 'DELETE'] loop
      if has_table_privilege('anon', table_name, privilege_name) then
        raise exception 'Anonymous client retains % on %', privilege_name, table_name;
      end if;
      if not has_table_privilege('authenticated', table_name, privilege_name) then
        raise exception 'Authenticated client lacks % on %', privilege_name, table_name;
      end if;
    end loop;
    foreach role_name in array array['anon', 'authenticated'] loop
      foreach privilege_name in array array['TRUNCATE', 'REFERENCES', 'TRIGGER', 'MAINTAIN'] loop
        if has_table_privilege(role_name, table_name, privilege_name) then
          raise exception 'Client % retains % on %', role_name, privilege_name, table_name;
        end if;
      end loop;
    end loop;
  end loop;
end $$;
insert into auth.users(id) values
 ('11111111-1111-1111-1111-111111111111'),
 ('22222222-2222-2222-2222-222222222222');
insert into public.profiles(id) values ('22222222-2222-2222-2222-222222222222');
insert into public.rehab_sessions(user_id, client_local_id, created_at, exercise_type, duration_seconds, sample_count, rms, mav, peak_activation)
values ('22222222-2222-2222-2222-222222222222', 'other-local', now(), 'wrist', 5, 100, 2, 1, 4);

set local role authenticated;
select set_config('request.jwt.claim.sub', '11111111-1111-1111-1111-111111111111', true);
insert into public.profiles(id, display_name) values ('11111111-1111-1111-1111-111111111111', 'owner');
insert into public.rehab_sessions(id, user_id, client_local_id, created_at, exercise_type, duration_seconds, sample_count, rms, mav, peak_activation)
values ('33333333-3333-3333-3333-333333333333', '11111111-1111-1111-1111-111111111111', 'own-local', now(), 'wrist', 5, 100, 2, 1, 4);
-- Retry with generated default id must keep the existing row and id.
insert into public.rehab_sessions(user_id, client_local_id, created_at, exercise_type, duration_seconds, sample_count, rms, mav, peak_activation)
values ('11111111-1111-1111-1111-111111111111', 'own-local', now(), 'wrist', 5, 100, 2, 1, 4)
on conflict (user_id, client_local_id) do update set rms = excluded.rms;

do $$ declare affected integer; begin
  if (select count(*) from public.rehab_sessions) <> 1 then raise exception 'Cross-owner read or duplicate retry'; end if;
  if (select id from public.rehab_sessions) <> '33333333-3333-3333-3333-333333333333'::uuid then raise exception 'Retry replaced row id'; end if;
  if (select count(*) from public.profiles) <> 1 then raise exception 'Cross-owner profile read'; end if;
  update public.rehab_sessions set rms = 99 where user_id = '22222222-2222-2222-2222-222222222222';
  get diagnostics affected = row_count;
  if affected <> 0 then raise exception 'Cross-owner update'; end if;
  delete from public.rehab_sessions where user_id = '22222222-2222-2222-2222-222222222222';
  get diagnostics affected = row_count;
  if affected <> 0 then raise exception 'Cross-owner delete'; end if;
  begin
    insert into public.rehab_sessions(user_id, client_local_id, created_at, exercise_type, duration_seconds, sample_count, rms, mav, peak_activation)
    values ('22222222-2222-2222-2222-222222222222', 'forged', now(), 'wrist', 5, 100, 2, 1, 4);
    raise exception 'Cross-owner insert accepted';
  exception when insufficient_privilege then null; end;
  begin
    update public.rehab_sessions set user_id = '22222222-2222-2222-2222-222222222222';
    raise exception 'Owner reassignment accepted';
  exception when insufficient_privilege then null; end;
  begin
    update public.profiles set id = '44444444-4444-4444-4444-444444444444';
    raise exception 'Profile owner reassignment accepted';
  exception when insufficient_privilege then null; end;
end $$;

select set_config('request.jwt.claim.sub', '22222222-2222-2222-2222-222222222222', true);
do $$ begin
  if (select count(*) from public.rehab_sessions where client_local_id = 'other-local' and rms = 2) <> 1 then raise exception 'Other owner data changed'; end if;
  if (select count(*) from public.rehab_sessions) <> 1 then raise exception 'Second owner sees first owner'; end if;
end $$;

reset role;
-- Give anon table access inside the rolled-back test to prove RLS itself denies rows.
grant select on public.profiles, public.rehab_sessions to anon;
set local role anon;
select set_config('request.jwt.claim.sub', '', true);
do $$ begin
  if (select count(*) from public.rehab_sessions) <> 0 or (select count(*) from public.profiles) <> 0 then
    raise exception 'Anonymous role can see owned rows';
  end if;
end $$;
reset role;
rollback;
