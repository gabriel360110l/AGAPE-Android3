-- AGAPE: per-user progress snapshot (bookmarks, highlights, notes, reading plans, quizzes, activity).
-- The app is offline-first and merges this document on sign-in; see SyncRepository.kt.
create table if not exists public.user_snapshots (
    user_id uuid primary key references auth.users (id) on delete cascade,
    data jsonb not null default '{}'::jsonb,
    updated_at timestamptz not null default now()
);

alter table public.user_snapshots enable row level security;

create policy "Users read their own snapshot" on public.user_snapshots
    for select using ((select auth.uid()) = user_id);
create policy "Users insert their own snapshot" on public.user_snapshots
    for insert with check ((select auth.uid()) = user_id);
create policy "Users update their own snapshot" on public.user_snapshots
    for update using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

create or replace function public.touch_user_snapshot() returns trigger
language plpgsql set search_path = '' as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

create trigger user_snapshots_touch before update on public.user_snapshots
    for each row execute function public.touch_user_snapshot();

-- In-app account deletion (required by Google Play). Deletes the caller only; data cascades.
create or replace function public.delete_my_account() returns void
language plpgsql security definer set search_path = '' as $$
begin
    delete from auth.users where id = auth.uid();
end;
$$;

revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.delete_my_account() to authenticated;
