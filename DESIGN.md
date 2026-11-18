# 우리 가족이 어디 있나? (Family Point) 안드로이드 앱 기획 및 상세 설계서

> [!IMPORTANT]
> **백그라운드 위치 공유 및 알림 정책 안내**
> 기기 사용자의 인지나 동의 없이 백그라운드에서 앱 실행 사실을 숨긴 채 위치를 추적하는 스텔스(Stealth) 기능은 개인정보 보호법(위치정보의 보호 및 이용 등에 관한 법률) 및 Android OS 보안 정책(Foreground Service 상시 알림 및 위치 접근 표시기 강제)상 지원하지 않습니다.
> 대신 본 앱은 **가족 구성원 간 명시적 동의를 전제로 동작**하되, 평상시 스마트폰 사용에 방해가 되지 않도록 **무음·무진동 상태바 알림(`IMPORTANCE_LOW`)**과 **최근 실행 목록(Recents) 종료 및 재부팅 후에도 위치를 계속 전송하는 3중 영속 아키텍처**로 설계·구현되었습니다.

---

## 1. 서비스 개요 및 핵심 컨셉
- **앱 명칭**: 우리 가족 어디 있나 (Family Point)
- **패키지명**: `com.familypoint.app`
- **플랫폼**: Android 8.0 (API 26) 이상 / Target SDK: Android 15 (API 35, Edge-to-Edge 대응)
- **지도 엔진**: OpenStreetMap (`osmdroid-android`, 별도 유료 API 키 불필요)
- **백엔드/DB**: Supabase (`PostgreSQL` + `PostgREST` + `RPC` + `pg_cron`)
- **핵심 가치**:
  1. 앱을 설치한 가족 구성원끼리 별도 가족 코드 입력 없이 자동으로 단일 가족 그룹(`FAMILY`)으로 묶여 서로의 위치를 실시간 확인
  2. 20종의 자체 제작 벡터 아바타와 별칭을 결합한 직관적인 커스텀 지도 마커 제공
  3. 최근 1일(24시간) 이동 경로 보관 및 24시간 경과 데이터 자동 삭제
  4. 가족 중 연결이 끊긴(미수신 35분 이상) 구성원을 지도 위에서 즉시 인지할 수 있는 경고 시스템
  5. 앱 최초 실행 후 최근 실행 목록(백그라운드)에서 스와이프 종료해도 위치 전송을 지속하는 3중 영속 백그라운드 엔진
  6. 비밀번호(`family`) 인증 기반의 가족관리(등록된 가족 삭제) 기능 제공
  7. 지도 마커(또는 목록)에서 가족 클릭 시 **`전화하기` / `문자하기` / `알림보내기`** 액션 메뉴 및 실시간 가족 알림 메시지 송수신 지원

---

## 2. 화면(UI/UX) 상세 기획 및 설계

### 2.1 전용 앱 아이콘 (Adaptive Icon)
- **컨셉**: 블루 레이더 동심원 배경(`ic_launcher_background.xml`) 위에 화이트&코랄 위치 핀(Location Pin)과 가족 3인 실루엣 및 하트(`ic_launcher_foreground.xml`)를 배치하여 "우리 가족 어디 있나"의 정체성을 표현했습니다.
- **미리보기 파일**: `assets/app_icon.svg`

### 2.2 온보딩(프로필 설정) 화면 (`OnboardingScreen.kt`)
1. **단순화된 입력 플로우**:
   - 가족 코드 입력란을 제거하고(내부적으로 `FAMILY` 그룹 자동 매핑), **`내 별칭 입력`** 단일 텍스트 필드만 전체 폭으로 배치했습니다.
   - 상단 미리보기 카드에는 선택한 원형 아바타 이미지와 입력한 별칭만 깔끔하게 표시됩니다.
2. **20종 대표 아바타 그리드 (타이틀 제거)**:
   - 아바타 하단의 부연 타이틀(`아빠(안경)` 등)을 모두 제거하고 4열 × 5행 그리드에 20종의 벡터 아바타 아이콘만 큼직하게 배치하여 스크롤 없이 한 화면에서 선택할 수 있습니다.
3. **시스템 바 겹침 방지**:
   - `Modifier.systemBarsPadding()` 및 `Modifier.imePadding()`을 적용하여 상단 시계/상태바 및 하단 제스처·3버튼 네비게이션 바와 UI가 겹치지 않습니다.

### 2.3 전체 화면 지도 및 슬라이드 패널 (`MapScreen.kt`)
1. **기본 전체 화면(Full-Screen) 지도 뷰**:
   - 오픈스트리트맵(`MapView`)이 상·하단 고정 패널 없이 화면 100%를 차지합니다.
   - 지도 위 각 가족의 위치에는 **원형 대표 아바타 + 상태 테두리 + 하단 별칭·배터리 잔량(%) 알약 배지**가 합성된 커스텀 마커가 표시됩니다.
   - 선택된 가족 구성원의 마커가 항상 다른 마커들보다 위(최상단 Z-order)에 렌더링됩니다.
2. **우측에서 열고 닫는 가족 목록 드로어 (`우측 슬라이드 패널`)**:
   - 기본 상태에서는 지도 우측 상단에 **`< 가족 (N)`** 손잡이 탭만 작게 표시됩니다.
   - 손잡이 탭을 터치하면 우측에서 가족 목록 카드가 슬라이드 인(`AnimatedVisibility`)되며, 각 구성원의 아바타, 별칭, 연결 상태(`연결됨`/`연결끊김`), 배터리 잔량, 최근 갱신 시각을 보여줍니다.
   - 목록에서 특정 가족을 터치하면 해당 가족의 위치로 지도 카메라가 즉시 이동하며, 우측 상단 `>` 버튼으로 패널을 닫을 수 있습니다.
3. **지도 위 설정 아이콘 클릭 시 열리는 하단 설정 시트**:
   - 지도 우측 하단에 **내 위치 즉시 공유(`MyLocation`) 소형 플로팅 버튼**과 **설정(`Settings`) 메인 플로팅 버튼**을 배치했습니다.
   - 설정 아이콘을 클릭하면 하단에서 설정 시트가 슬라이드 업되어 열리며 다음 기능을 제공합니다:
     - 선택된 가족 구성원 상태 요약 및 **별칭/아바타 변경(연필 아이콘)**, **시트 닫기(`X` 아이콘)**
     - **최근 1일(24시간) 이동 경로 보기 스위치**: 활성화 시 선택된 가족의 최근 24시간 이동 궤적을 파란색 `Polyline`으로 지도에 표시
     - **저전력 백그라운드 위치 공유 스위치**: 무음 상태 알림 기반 백그라운드 위치 전송 ON/OFF
     - 하단 3열 액션 버튼: **`[공유]`**, **`[새로고침]`**, **`[가족관리]`**

### 2.4 연결 끊김(Offline) 가족 인지 설계 (`MarkerIconFactory.kt`, `MapScreen.kt`)
가족 중 위치 정보가 없거나 마지막 위치 갱신으로부터 **35분 이상 경과**한 구성원은 **연결 끊김** 상태로 판정하며, 지도 화면에서 다음 4가지 시각 요소로 즉시 인지할 수 있습니다.
1. **지도 마커 변화**:
   - 아바타 얼굴이 **흑백(채도 감소)**으로 변환됩니다.
   - 아바타 외곽 링이 **빨간색 점선 테두리**로 바뀌고, 우측 상단에 **빨간색 `!` 경고 배지**가 추가됩니다.
   - 하단 말풍선 라벨이 붉은색(`#DC2626`) 배경의 **`별칭 · 연결끊김 (경과시간)`**으로 표시됩니다.
2. **지도 좌측 상단 `⚠️ 연결 끊김 (N명)` 플로팅 경고 배너**:
   - 연결이 끊긴 가족의 마커가 현재 지도 화면 바깥에 있더라도 즉시 알 수 있도록 좌측 상단에 경고 배너가 표시됩니다.
   - 배너 안의 가족 칩을 터치하면 해당 가족의 마지막 위치로 지도 카메라가 즉시 이동합니다.
3. **우측 `< 가족 (N)` 손잡이 탭 경고 카운트**:
   - 가족 목록이 닫혀 있을 때도 손잡이 탭 우측에 빨간색 **`! N`** 배지가 표시됩니다.
4. **가족 목록 및 하단 설정 시트 상태 배지**:
   - 각 가족 항목 우측에 초록색 **`연결됨`** 또는 빨간색 **`연결끊김`** 배지가 표시됩니다.

### 2.5 가족관리 (비밀번호 인증 및 가족 삭제)
1. **진입 경로**: 하단 설정 시트의 **`[가족관리]`** 버튼 클릭
2. **비밀번호 인증 다이얼로그**:
   - 고정 비밀번호 **`family`**를 입력해야 통과할 수 있습니다 (불일치 시 오류 메시지 출력).
3. **가족관리 목록 및 삭제 다이얼로그**:
   - 현재 등록된 모든 가족 구성원 목록과 각각의 빨간색 **`[삭제]`** 버튼이 표시됩니다.
   - `[삭제]` 클릭 시 Supabase DB의 `family_members` 테이블(및 `ON DELETE CASCADE`로 연결된 `location_history` 이력)에서 즉시 삭제되며 지도 마커와 목록이 갱신됩니다.

### 2.6 가족 클릭 액션 메뉴 (`전화하기` / `문자하기` / `알림보내기`)
1. **진입 경로**: 지도 위의 가족 마커 클릭 또는 우측 가족 목록에서 구성원 클릭
2. **액션 메뉴 팝업 카드**:
   - 선택한 가족의 아바타, 별칭, 연결 상태, 배터리 잔량, 전화번호(입력 시)를 표시하고 하단에 3개 버튼을 제공합니다:
     - **`[전화하기]`**: 안드로이드 기본 전화 다이얼러 앱(`Intent.ACTION_DIAL`, `tel:`)으로 즉시 이동합니다.
     - **`[문자하기]`**: 안드로이드 기본 문자(메시지) 앱(`Intent.ACTION_SENDTO`, `smsto:`)으로 즉시 이동합니다.
     - **`[알림보내기]`**: 해당 가족에게 보낼 알림 메시지를 직접 입력하거나 빠른 문구(`지금 어디야?`, `집으로 오세요`, `연락 부탁해요`, `식사하러 오세요`)를 선택해 전송할 수 있는 다이얼로그를 엽니다.
3. **실시간 알림 수신**:
   - 대상 가족의 스마트폰에서는 포그라운드 상태일 때 인앱 팝업 다이얼로그로, 백그라운드 상태일 때 `LocationSyncService`를 통해 소리·진동이 포함된 상단 헤드업 시스템 알림(`IMPORTANCE_HIGH`)으로 즉시 수신됩니다.

---

## 3. 대표 아바타 20종 에셋 명세

20종의 아바타는 SVG 원본(`assets/avatars/`)과 안드로이드 VectorDrawable(`app/src/main/res/drawable/avatar_01.xml` ~ `avatar_20.xml`)로 모두 제작되어 앱 내부에 탑재되어 있습니다.

| 번호 | VectorDrawable 리소스 | SVG 원본 파일 | 디자인 특징 |
| :---: | :--- | :--- | :--- |
| 01 | `R.drawable.avatar_01` | `avatar_01_dad_glasses.svg` | 안경 쓴 단정한 남성 |
| 02 | `R.drawable.avatar_02` | `avatar_02_mom_perm.svg` | 단발 웨이브 여성 |
| 03 | `R.drawable.avatar_03` | `avatar_03_grandpa.svg` | 둥근 안경과 콧수염 어르신 |
| 04 | `R.drawable.avatar_04` | `avatar_04_grandma.svg` | 은발 번 헤어 어르신 |
| 05 | `R.drawable.avatar_05` | `avatar_05_son_cap.svg` | 야구모자를 쓴 소년 |
| 06 | `R.drawable.avatar_06` | `avatar_06_daughter_ponytail.svg` | 리본 포니테일 소녀 |
| 07 | `R.drawable.avatar_07` | `avatar_07_teen_boy.svg` | 헤드폰을 착용한 청소년 |
| 08 | `R.drawable.avatar_08` | `avatar_08_teen_girl.svg` | 윙크하는 긴 생머리 소녀 |
| 09 | `R.drawable.avatar_09` | `avatar_09_kid_boy.svg` | 바가지 머리 어린이 |
| 10 | `R.drawable.avatar_10` | `avatar_10_kid_girl.svg` | 양갈래 머리 어린이 |
| 11 | `R.drawable.avatar_11` | `avatar_11_baby.svg` | 공갈젖꼭지를 문 아기 |
| 12 | `R.drawable.avatar_12` | `avatar_12_uncle.svg` | 턱수염과 스포츠 머리 남성 |
| 13 | `R.drawable.avatar_13` | `avatar_13_aunt.svg` | 빨간 베레모와 귀걸이 여성 |
| 14 | `R.drawable.avatar_14` | `avatar_14_ sporty_dad.svg` | 스포츠 헤어밴드 캐릭터 |
| 15 | `R.drawable.avatar_15` | `avatar_15_studious.svg` | 뿔테안경 학생 캐릭터 |
| 16 | `R.drawable.avatar_16` | `avatar_16_dog_puppy.svg` | 강아지 캐릭터 |
| 17 | `R.drawable.avatar_17` | `avatar_17_cat_kitty.svg` | 고양이 캐릭터 |
| 18 | `R.drawable.avatar_18` | `avatar_18_bear.svg` | 곰돌이 캐릭터 |
| 19 | `R.drawable.avatar_19` | `avatar_19_rabbit.svg` | 토끼 캐릭터 |
| 20 | `R.drawable.avatar_20` | `avatar_20_penguin.svg` | 펭귄 캐릭터 |

---

## 4. Supabase 데이터베이스 설계 및 1일치 보관 정책

전체 스키마 및 권한 설정은 `supabase_schema.sql`에 정의되어 있습니다.

### 4.1 테이블 구조
1. **`public.family_groups`**: 가족 그룹 테이블 (`id`, `invite_code`, `group_name`, `created_at`)
2. **`public.family_members`**: 가족 구성원 및 최신 위치 스냅샷 테이블 (`id`, `group_id`, `device_uid`, `nickname`, `avatar_code`, `battery_level`, `last_lat`, `last_lng`, `last_accuracy`, `activity_type`, `last_updated_at`, `created_at`)
3. **`public.location_history`**: 최근 1일(24시간) 위치 이력 테이블 (`id`, `member_id` ON DELETE CASCADE, `group_id` ON DELETE CASCADE, `latitude`, `longitude`, `accuracy_m`, `activity_type`, `recorded_at`)

### 4.2 단일 호출 RPC (`report_location`) 및 1일치 자동 삭제
- 안드로이드 앱이 `/rest/v1/rpc/report_location`을 1회 호출하면 DB 내부에서:
  1. `family_members`의 최신 좌표·배터리·활동 상태·갱신 시각 UPDATE
  2. `location_history`에 신규 이동 좌표 INSERT
  3. `delete from public.location_history where recorded_at < now() - interval '24 hours';` 실행으로 **1일이 지난 과거 데이터 즉시 삭제**
- 추가적으로 `pg_cron` 스케줄러(`0 * * * *`)를 등록하여 매시간 정각마다 24시간 경과 데이터를 자동 정리합니다.

---

## 5. 배터리·데이터 최적화 및 앱 종료 후 연속 위치 전송 설계

### 5.1 앱 최초 실행 후 백그라운드 종료 시에도 동작하는 3중 영속(Persistence) 구조
1. **1계층 (`LocationSyncService` - `stopWithTask="false"` & `onTaskRemoved` 자가 복구)**:
   - 앱 최초 실행 및 프로필 저장 시 백그라운드 위치 공유가 기본값(`true`)으로 즉시 시작됩니다.
   - 사용자가 최근 앱 목록(Recents)에서 앱을 스와이프해 종료하더라도 `android:stopWithTask="false"` 설정과 `onTaskRemoved()` 내 `AlarmManager`(`ELAPSED_REALTIME_WAKEUP`, 2초 후 재기동) 및 `WorkManager` 즉시 실행 큐를 통해 서비스가 유지·복구됩니다.
2. **2계층 (`LocationUpdatesReceiver` - Google Play Services `PendingIntent` 브로드캐스트)**:
   - `FusedLocationProviderClient`에 `PendingIntent` 기반 브로드캐스트 리시버를 등록하여, 앱 프로세스가 완전히 종료된 상태에서도 단말이 30m 이상 이동하면 Google Play Services가 리시버를 깨워 Supabase로 좌표를 전송합니다.
3. **3계층 (`LocationSyncWorker` & `BootCompletedReceiver`)**:
   - OS가 관리하는 `WorkManager` 주기 작업(15분 간격)과 단말 재부팅 감지 리시버(`BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`)를 통해 재부팅 후에도 위치 전송이 자동으로 재개됩니다.

### 5.2 배터리 및 모바일 데이터 절약 알고리즘
- **저전력 위치 우선순위**: `Priority.PRIORITY_BALANCED_POWER_ACCURACY`를 기본으로 사용하고 최소 이동 거리(`setMinUpdateDistanceMeters(30f)`) 조건을 부여해 정지 상태에서 불필요한 GPS 깨우기를 방지합니다.
- **30m 델타 필터링 & 30분 생존 신호(Heartbeat)**: 이전 전송 위치로부터 30m 이내의 미세한 오차는 전송을 생략하여 모바일 데이터를 아끼되, 30분마다 생존 신호를 갱신하여 가족 지도에서 `연결됨` 상태를 유지합니다.
- **배터리 방전 보호 모드**: 단말 배터리가 **15% 이하**이고 충전 중이 아닐 때는 백그라운드 자동 전송을 일시 보류하여 휴대폰 방전을 방지합니다.
- **무음 최소화 알림 채널 (`IMPORTANCE_LOW`)**: 소리·진동·헤드업 팝업 없이 상태바 하단에 조용하게 위치 공유 상태만 표시합니다.
