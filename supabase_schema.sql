-- ============================================================================
-- Family Point (우리 가족이 어디 있나?) - Supabase 초기화 SQL 스크립트
-- Supabase Dashboard > SQL Editor 에 붙여넣고 실행(Run)하세요.
-- ============================================================================

-- 1. 확장 기능 활성화 (UUID 생성 및 1일치 자동 삭제용 pg_cron)
create extension if not exists "pgcrypto";
create extension if not exists "pg_cron";

-- 2. 가족 그룹 테이블 (초대 코드로 가족 묶기)
create table if not exists public.family_groups (
    id uuid primary key default gen_random_uuid(),
    invite_code varchar(12) not null unique,
    group_name varchar(50) not null default '우리 가족',
    created_at timestamptz not null default now()
);

-- 3. 가족 구성원 테이블 (별칭, 대표 아바타 20종 코드, 최신 위치 스냅샷)
create table if not exists public.family_members (
    id uuid primary key default gen_random_uuid(),
    group_id uuid not null references public.family_groups(id) on delete cascade,
    device_uid varchar(128) not null unique,      -- 기기 고유 식별자 또는 Supabase Auth UID
    nickname varchar(30) not null,                -- 별칭 (위치 화면 표시용)
    avatar_code varchar(40) not null,             -- avatar_01_dad_glasses ~ avatar_20_penguin
    battery_level smallint not null default 100,  -- 배터리 잔량 (%)
    last_lat double precision,                    -- 최신 위도
    last_lng double precision,                    -- 최신 경도
    last_accuracy real,                           -- 최신 정확도(m)
    activity_type varchar(16) default 'STILL',    -- STILL, WALKING, IN_VEHICLE
    last_updated_at timestamptz default now(),    -- 최신 위치 갱신 시각
    created_at timestamptz not null default now()
);

create index if not exists idx_family_members_group_id
    on public.family_members(group_id);

-- 4. 위치 이력 테이블 (최근 1일치 데이터만 보관)
create table if not exists public.location_history (
    id bigint generated always as identity primary key,
    member_id uuid not null references public.family_members(id) on delete cascade,
    group_id uuid not null references public.family_groups(id) on delete cascade,
    latitude double precision not null,
    longitude double precision not null,
    accuracy_m real,
    activity_type varchar(16) default 'STILL',
    recorded_at timestamptz not null default now()
);

create index if not exists idx_location_history_group_time
    on public.location_history(group_id, recorded_at desc);

create index if not exists idx_location_history_member_time
    on public.location_history(member_id, recorded_at desc);

create index if not exists idx_location_history_recorded_at
    on public.location_history(recorded_at);

-- ============================================================================
-- 5. 배터리·데이터 절약용 단일 호출 RPC 함수 (report_location)
-- 안드로이드 앱에서 위치 전송 시 REST API를 1번만 호출하면:
--   (1) family_members 의 최신 위치/배터리 갱신
--   (2) location_history 에 이력 추가
--   (3) 24시간 지난 오래된 데이터 정리
-- 가 서버 내부에서 한 번에 처리됩니다.
-- ============================================================================
create or replace function public.report_location(
    p_member_id uuid,
    p_lat double precision,
    p_lng double precision,
    p_accuracy real default 10.0,
    p_battery smallint default 100,
    p_activity varchar default 'STILL'
)
returns void
language plpgsql
security definer
as $$
declare
    v_group_id uuid;
begin
    -- 1) 구성원 최신 위치 및 배터리 상태 업데이트
    update public.family_members
       set last_lat = p_lat,
           last_lng = p_lng,
           last_accuracy = p_accuracy,
           battery_level = p_battery,
           activity_type = p_activity,
           last_updated_at = now()
     where id = p_member_id
     returning group_id into v_group_id;

    if v_group_id is null then
        raise exception 'Member not found: %', p_member_id;
    end if;

    -- 2) 1일치 이력 테이블에 위치 기록 추가
    insert into public.location_history (
        member_id, group_id, latitude, longitude, accuracy_m, activity_type, recorded_at
    ) values (
        p_member_id, v_group_id, p_lat, p_lng, p_accuracy, p_activity, now()
    );

    -- 3) 24시간(1일)이 지난 위치 이력 즉시 정리 (pg_cron 미사용 환경에서도 1일 보관 보장)
    delete from public.location_history
     where recorded_at < now() - interval '24 hours';
end;
$$;

-- ============================================================================
-- 6. pg_cron 기반 1일치(24시간) 초과 데이터 자동 삭제 스케줄러 (매시간 정각 실행)
-- ============================================================================
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        perform cron.schedule(
            'cleanup_location_history_24h',
            '0 * * * *',
            $cron$delete from public.location_history where recorded_at < now() - interval '24 hours'$cron$
        );
    end if;
exception when others then
    -- pg_cron 권한이 없는 경우 report_location 내부 삭제 로직으로 동작
    null;
end
$$;

-- ============================================================================
-- 7. Supabase Realtime 활성화 (지도 화면을 보고 있을 때 실시간 마커 이동)
-- ============================================================================
alter publication supabase_realtime add table public.family_members;

-- ============================================================================
-- 8. Row Level Security (RLS) 및 역할 권한(GRANT) 설정
-- ============================================================================
grant usage on schema public to anon, authenticated;
grant all on table public.family_groups to anon, authenticated;
grant all on table public.family_members to anon, authenticated;
grant all on table public.location_history to anon, authenticated;
grant usage, select on all sequences in schema public to anon, authenticated;
grant execute on function public.report_location to anon, authenticated;

alter table public.family_groups enable row level security;
alter table public.family_members enable row level security;
alter table public.location_history enable row level security;

drop policy if exists "Allow anon access to family_groups" on public.family_groups;
create policy "Allow anon access to family_groups"
    on public.family_groups for all to anon, authenticated using (true) with check (true);

drop policy if exists "Allow anon access to family_members" on public.family_members;
create policy "Allow anon access to family_members"
    on public.family_members for all to anon, authenticated using (true) with check (true);

drop policy if exists "Allow anon access to location_history" on public.location_history;
create policy "Allow anon access to location_history"
    on public.location_history for all to anon, authenticated using (true) with check (true);

