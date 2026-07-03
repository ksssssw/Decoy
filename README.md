# Peekaboo — Android Network Inspector & Mocker

A Chucker/LeakCanary-style debug tool for Android. Add **two lines per HTTP stack** and inspect/mock all your app's traffic in a built-in web UI — perfect for reproducing error screens and edge-case data during development and QA.

- 앱의 모든 HTTP 요청/응답을 자동 캡처
- 웹 UI에서 정규식 URL 패턴으로 **응답 모킹** (상태코드/바디/헤더/지연)
- 룰 **그룹핑**(화면/테스트케이스 단위) + 그룹·전체 on/off 스위치
- **드래그 앤 드롭**으로 룰 순서/그룹 이동, 그룹 순서 변경, 그룹 이름 인라인 수정
- **순서 기반 매칭** — 목록에서 위에 있는 룰이 먼저 적용 (중복 모킹 시 드래그로 우선순위 조정)
- 룰셋 **Export/Import** — 팀원·디자이너와 JSON 파일로 공유
- 다크/라이트 테마, 실행 중인 앱 패키지·버전 표시
- 모킹 룰은 파일로 영속화 — 앱을 재시작해도 순서·그룹까지 유지
- Retrofit(OkHttp)·Ktor client 모두 지원, DI(Hilt/Koin) 비종속
- ContentProvider 자동 초기화 — `Application` 코드 수정 불필요
- **release 빌드에는 서버/인터셉트 코드가 물리적으로 미포함** (no-op 스왑)

---

## Quick Start

### Retrofit / OkHttp 앱

```kotlin
// build.gradle.kts
debugImplementation("com.peekaboo:peekaboo-okhttp:<version>")
releaseImplementation("com.peekaboo:peekaboo-okhttp-noop:<version>")
```

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(PeekabooInterceptor()) // debug: 캡처+모킹 / release: no-op
    .build()
```

### Ktor client 앱

```kotlin
// build.gradle.kts
debugImplementation("com.peekaboo:peekaboo-ktor:<version>")
releaseImplementation("com.peekaboo:peekaboo-ktor-noop:<version>")
```

```kotlin
HttpClient(CIO) {
    installPeekaboo()                      // debug: 캡처+모킹 / release: no-op
    install(ContentNegotiation) { gson() } // Peekaboo를 ContentNegotiation보다 먼저!
}
```

그게 전부입니다. debug 아티팩트가 인스펙터 서버 + 웹 UI를 함께 가져오고, 앱 실행 시 자동으로 기동됩니다.

### 인스펙터 열기

서버는 기기의 **loopback(127.0.0.1)에만 바인딩**됩니다. 접속 방법:

| 위치 | 방법 |
|---|---|
| PC 브라우저 (권장) | `adb forward tcp:8090 tcp:8090` → `http://localhost:8090` |
| 기기 브라우저 | `http://localhost:8090` (또는 `PeekabooLauncher.getInspectorUrl()`로 인텐트 실행) |

포트 8090이 사용 중이면 8091~8099로 자동 폴백하며, 실제 포트는 Logcat(`Peekaboo` 태그)에 출력됩니다.

```kotlin
// 앱에서 인스펙터 URL 얻기 — release에서는 null 반환
val url: String? = PeekabooLauncher.getInspectorUrl()
```

---

## DI 연동 예시

### Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(PeekabooInterceptor())
            .build()
}
```

### Koin

```kotlin
val networkModule = module {
    single {
        HttpClient(CIO) {
            installPeekaboo()
            install(ContentNegotiation) { gson() }
        }
    }
}
```

---

## 모듈 구조

```
:peekaboo-okhttp        OkHttp 인터셉터 (서버+웹UI transitively 포함)   ← debugImplementation
:peekaboo-okhttp-noop   proceed-only 스텁                              ← releaseImplementation
:peekaboo-ktor          Ktor client 플러그인 (서버+웹UI transitively)  ← debugImplementation
:peekaboo-ktor-noop     no-op 스텁                                     ← releaseImplementation
:peekaboo-core          순수 JVM, 의존성 0 — 모델/저장소/Launcher (직접 선언 불필요)
:peekaboo-android       인스펙터 서버 + 웹 UI + 자동초기화 (직접 선언 불필요)
:app                    샘플 앱 — Retrofit·Ktor 두 스택 시연
```

no-op 아티팩트는 실제 모듈과 동일한 패키지/시그니처를 제공하므로, `main` 소스셋의 Peekaboo 호출 코드를 그대로 두고 의존성 스왑만으로 release에서 완전히 무력화됩니다.

## Mock Rules

웹 UI의 **Mock Rules** 탭 또는 Traffic 상세의 **Create Mock from Request** 버튼으로 룰을 만듭니다.

| 필드 | 설명 |
|---|---|
| URL Pattern | 정규식. URL 일부와 매치되면 적용 (`containsMatchIn`) |
| Method | GET/POST/… 또는 ANY |
| Status Code / Body / Headers | 모킹 응답 내용 |
| Delay | 응답 지연(ms) — 느린 네트워크 재현 |
| Description | 룰 목록에 표시 — 디자이너/기획자도 알 수 있게 용도를 적어두는 필드 |
| Group | 화면/테스트케이스 단위 그룹. 그룹 헤더의 스위치로 한꺼번에 on/off |

**매칭 우선순위는 목록 순서입니다** — 여러 룰이 같은 요청에 매치되면 목록에서 가장 위의 활성 룰이 적용됩니다(DUP 배지로 표시). 드래그 앤 드롭으로:

- 룰을 위/아래로 끌어 순서 변경, 다른 그룹 위나 그룹 헤더에 놓아 그룹 이동
- 그룹이 없는 룰 두 개를 겹쳐 놓으면 새 그룹 생성 (이름 바로 입력)
- 그룹 헤더를 끌어 그룹 간 순서 변경, 연필 아이콘으로 그룹 이름 수정(같은 이름으로 바꾸면 병합)

룰은 `files/peekaboo/rules.json`에 순서·그룹까지 저장되어 앱 재시작 후에도 유지됩니다. 캡처 트래픽은 인메모리(최근 500건)입니다.

### 룰셋 공유 (Export / Import)

Mock Rules 탭의 **Export**로 전체 룰을 JSON 파일로 내려받아 팀원에게 전달하고, 받은 쪽은 **Import**에서 **Merge**(기존 룰 유지+추가) 또는 **Replace**(전체 교체)로 불러옵니다. 파일 포맷은 기기의 `rules.json`과 동일해서 서로 호환됩니다. 잘못된 정규식이 섞여 있으면 해당 룰만 건너뛰고 개수를 알려줍니다.

## REST API

웹 UI가 사용하는 API는 직접 호출할 수도 있습니다 (CI에서 룰 주입 등).

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/calls` | 캡처된 요청 목록 |
| GET | `/api/calls/{id}` | 단건 조회 |
| DELETE | `/api/calls` | 캡처 초기화 |
| GET / POST | `/api/mocks` | 룰 목록 / 생성 (잘못된 정규식은 400) |
| PUT / DELETE | `/api/mocks/{id}` | 룰 수정 / 삭제 |
| PATCH | `/api/mocks/{id}/toggle` | 룰 활성 토글 |
| PATCH | `/api/mocks/group/toggle` | 그룹 전체 on/off — `{"group":"...","isEnabled":true}` |
| PATCH | `/api/mocks/group/rename` | 그룹 이름 변경 — `{"from":"...","to":"..."}` (기존 이름과 같으면 병합) |
| PATCH | `/api/mocks/all/toggle` | 모든 룰 on/off — `{"isEnabled":false}` |
| PUT | `/api/mocks/layout` | 전체 순서·그룹 배치 저장 — `{"items":[{"id":"...","group":"..."},…]}` (순서 = 매칭 우선순위) |
| POST | `/api/mocks/import` | 룰 일괄 추가 — `{"mode":"merge"\|"replace","rules":[…]}` |
| GET | `/api/status` | 서버 상태 + 호스트 앱 정보(패키지명/버전/기기) |
| WS | `/ws` | 신규 캡처 실시간 push |

---

## 보안

- 서버는 **127.0.0.1에만 바인딩** — 같은 Wi-Fi의 다른 기기에서는 접근할 수 없습니다. PC 접속은 `adb forward`(USB/adb 권한 필요)를 통해서만 가능합니다.
- release 빌드는 no-op 아티팩트만 포함하므로 서버·인터셉트 코드가 APK에 존재하지 않습니다.
- 남는 위협 모델: **같은 기기에 설치된 다른 앱**이 debug 빌드의 로컬 포트에 접근할 수 있습니다(Chucker 등 동종 도구와 동일). 민감한 트래픽을 다루는 앱이라면 debug 빌드 배포 범위에 유의하세요.
- 절대 `implementation`으로 (모든 빌드타입에) 실제 아티팩트를 넣지 마세요 — release에 오픈 포트 서버가 포함됩니다.

### release에 Peekaboo가 없는지 검증하기

```bash
./gradlew :app:assembleRelease

# 1) dex에 서버 클래스가 없어야 함 (core 모델 + 스텁만 존재)
$ANDROID_HOME/build-tools/<ver>/dexdump app-release.apk의 classes.dex | grep "com/peekaboo"
#   → com/peekaboo/core/*, com/peekaboo/okhttp/PeekabooInterceptor(스텁)만 출력
#   → com/peekaboo/android/*, io/ktor/server/* 는 없어야 함

# 2) 설치 후 리스닝 포트가 없어야 함 (8090 = 0x1F9A)
adb shell "cat /proc/net/tcp | grep 1F9A"   # 출력 없음이 정상
```

---

## 샘플 앱

`:app` 모듈이 Retrofit(OkHttp)과 Ktor client 두 경로를 한 화면에서 시연합니다.

1. `./gradlew :app:installDebug` 후 실행
2. `adb forward tcp:8090 tcp:8090` → PC 브라우저에서 `http://localhost:8090`
3. GET/POST/404/지연 버튼을 눌러 트래픽 확인
4. Mock Rules에서 룰 생성(예: `/posts`에 500 + 3000ms) → 앱에서 다시 호출 → 에러/지연 동작 확인
5. 앱을 강제 종료 후 재실행해도 룰이 유지되는지 확인

## Roadmap

- 서버사이드 request replay (앱의 실제 클라이언트 설정을 태우는 재요청)
- Ktor 3.x 지원 (`MockCallFactory`의 InternalAPI 의존 제거)
- Maven Central 배포

## License

MIT
