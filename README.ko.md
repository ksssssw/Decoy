# Decoy — Android Network Inspector & Mocker

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ksssssw/decoy-core?label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ksssssw/decoy-core)
[![CI](https://github.com/ksssssw/Decoy/actions/workflows/ci.yml/badge.svg)](https://github.com/ksssssw/Decoy/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-blue.svg?logo=kotlin)

[English](README.md) | **한국어**

debug 빌드 전용 Android 네트워크 인스펙터·모커. HTTP 스택당 **두 줄**만 추가하면 앱의 모든 트래픽을 내장 웹 UI에서 인스펙션·모킹할 수 있습니다 — 개발·QA 중 에러 화면이나 엣지케이스 데이터 재현에 최적입니다. release 빌드에는 이 코드가 전혀 포함되지 않습니다.

- 앱의 모든 HTTP 요청/응답을 자동 캡처
- 웹 UI에서 정규식 URL 패턴으로 **응답 모킹** (상태코드/바디/헤더/지연)
- 룰 **그룹핑**(화면/테스트케이스 단위) + 그룹·전체 on/off 스위치
- **드래그 앤 드롭**으로 룰 순서/그룹 이동, 그룹 순서 변경, 그룹 이름 인라인 수정
- **순서 기반 매칭** — 목록에서 위에 있는 룰이 먼저 적용 (중복 모킹 시 드래그로 우선순위 조정)
- 룰셋 **Export/Import** — 전체/특정 그룹/선택한 룰만 JSON으로 추출, import 직후 **Undo** 지원
- 새로 디자인된 인스펙터 UI: 라이트/다크 디자인 시스템, 크기 조절 가능한 트래픽 사이드바, 최대화 가능한 룰 에디터, 실시간 연결 상태 표시
- 실행 중인 앱의 이름·패키지·버전 표시 — 여러 앱을 띄워도 탭이 서로 구분됨
- 모킹 룰은 파일로 영속화 — 앱을 재시작해도 순서·그룹까지 유지
- Retrofit(OkHttp)·Ktor 3.x client 모두 지원, DI(Hilt/Koin/수동) 비종속
- ContentProvider 자동 초기화 — `Application` 코드 수정 불필요
- **release 빌드에는 서버/인터셉트 코드가 물리적으로 미포함** (no-op 스왑)

---

## Requirements

| | 최소 버전 |
|---|---|
| Android | minSdk 24 |
| Kotlin | 2.2.20+ |
| OkHttp (`decoy-okhttp`) | 4.12.0+ |
| Ktor client (`decoy-ktor`) | 3.3.0+ |

Decoy는 의도적으로 이 최소 버전들로 빌드됩니다 — 배포된 아티팩트가 앱의 Kotlin/OkHttp/Ktor 버전을 강제로 올리는 일이 없습니다.

## Quick Start

### Retrofit / OkHttp 앱

```kotlin
// build.gradle.kts
debugImplementation("io.github.ksssssw:decoy-okhttp:0.2.0")
releaseImplementation("io.github.ksssssw:decoy-okhttp-noop:0.2.0")
```

```kotlin
val client = OkHttpClient.Builder()
    .addInterceptor(DecoyInterceptor()) // debug: 캡처+모킹 / release: no-op
    .build()
```

### Ktor client 앱

```kotlin
// build.gradle.kts
debugImplementation("io.github.ksssssw:decoy-ktor:0.2.0")
releaseImplementation("io.github.ksssssw:decoy-ktor-noop:0.2.0")
```

```kotlin
HttpClient(CIO) {
    installDecoy()                      // debug: 캡처+모킹 / release: no-op
    install(ContentNegotiation) { gson() } // Decoy를 ContentNegotiation보다 먼저!
}
```

> **Ktor 버전:** Decoy는 **Ktor 3.x**를 대상으로 합니다(3.3에서 검증). Gradle이 debug 클래스패스의 Ktor 버전을 통일하기 때문에, 아직 Ktor 2.x를 쓰는 앱은 지원되지 않습니다 — 먼저 Ktor 3.x로 올리세요. 엔진은 무엇이든(`CIO`, `OkHttp`, …) 무관하며 `installDecoy()`는 엔진 비종속입니다.

그게 전부입니다. debug 아티팩트가 인스펙터 서버 + 웹 UI를 함께 가져오고, 앱 실행 시 자동으로 기동됩니다.

### 인스펙터 열기

서버는 기기의 **loopback(127.0.0.1)에만 바인딩**됩니다. 접속 방법:

| 위치 | 방법 |
|---|---|
| PC 브라우저 (권장) | `adb forward tcp:8090 tcp:8090` → `http://localhost:8090` |
| 기기 브라우저 | `http://localhost:8090` (또는 `DecoyLauncher.getInspectorUrl()`로 인텐트 실행) |

포트 8090이 사용 중이면 8091~8099로 자동 폴백하며, 실제 포트는 Logcat(`Decoy` 태그)에 출력됩니다. 서버는 `SO_REUSEADDR`로 바인딩하므로, 앱을 재시작해도 이전 연결이 TIME_WAIT에 남아 있는 동안에도 같은 포트를 다시 획득합니다 — 이미 걸어둔 `adb forward`가 재시작 후에도 계속 동작합니다.

```kotlin
// 앱에서 인스펙터 URL 얻기 — release에서는 null 반환
val url: String? = DecoyLauncher.getInspectorUrl()
```

### 여러 앱 / 여러 기기에서 쓰기

한 기기에서 Decoy를 쓰는 앱마다 각자 포트를 갖습니다: 먼저 실행된 앱이 8090, 다음이 8091… (실행 순서가 결정). 어떤 앱이 어떤 포트를 잡았는지는:

```bash
adb logcat -s Decoy
# Decoy: Inspector for My App (com.example.myapp) running at http://localhost:8090 (loopback only)
# Decoy: Inspector for Other App (com.example.other) running at http://localhost:8091 (loopback only)
```

필요한 포트를 각각 포워딩하세요(`adb forward --list`로 현재 매핑 확인). 기기/에뮬레이터가 여러 대라면 시리얼로 지정하고, 호스트 포트가 기기 포트와 같을 필요는 없습니다:

```bash
adb devices                                        # 시리얼 목록
adb -s emulator-5554 forward tcp:8090 tcp:8090
adb -s R3CX90ABCDE forward tcp:8091 tcp:8090       # 호스트 :8091 → 기기 :8090
```

브라우저 탭 타이틀에 앱 이름과 호스트 측 포트가 표시되므로(예: `My App · Decoy :8091`) 탭이 서로 구분됩니다.

### 트러블슈팅: 인스펙터 접속 불가 (예: 다음날 아침)

`adb forward` 매핑은 기기가 슬립하거나 재연결되거나 호스트 측 adb 데몬이 꼬이면 stale해집니다 — 기기 안의 SDK가 고칠 수 있는 영역이 아닙니다. 아래 순서로 복구하세요:

```bash
adb forward --list                       # 매핑이 아직 있는지 확인
adb forward --remove-all                 # stale 매핑 제거 후…
adb forward tcp:8090 tcp:8090            # …다시 포워딩
adb kill-server && adb start-server      # 최후의 수단: adb 데몬 재시작 (후 재포워딩)
```

앱 쪽은 할 일이 없습니다: 앱을 재시작해도 포트가 바뀌지 않으므로(위의 `SO_REUSEADDR` 참고) 같은 포트로 다시 포워딩만 하면 됩니다.

---

## 기존 NetworkModule에 통합하기

실서비스라면 이미 HTTP 클라이언트를 소유한 DI 모듈이 있을 것입니다. Decoy는 그 모듈에 **스택당 한 줄**로 들어가도록 설계되었고, 그 외 구조 변경은 필요 없습니다. 샘플 앱의 [`NetworkModule.kt`](app/src/main/kotlin/com/ksssssw/decoy/NetworkModule.kt)가 전체 패턴을 시연합니다.

### Hilt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(DecoyInterceptor()) // ← Decoy는 이 한 줄뿐
            .build()
}
```

### Koin

```kotlin
val networkModule = module {
    single {
        HttpClient(CIO) {
            installDecoy()                      // ← Decoy는 이 한 줄뿐
            install(ContentNegotiation) { gson() }
        }
    }
}
```

### 수동 DI / 프레임워크 없음

```kotlin
object Network {
    val okHttp: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(DecoyInterceptor())     // ← Decoy는 이 한 줄뿐
        .build()
}
```

`-noop` 아티팩트가 동일한 패키지/시그니처를 제공하므로 이 호출 코드는 모든 빌드타입에서 그대로 컴파일되고, debug/release 동작은 **오로지 Gradle 의존성 스왑**(`debugImplementation` 실제 / `releaseImplementation` noop)으로 결정됩니다. 실제 아티팩트를 `implementation`으로 넣지 마세요 — release 빌드에 오픈 포트 서버가 포함됩니다.

---

## 모듈 구조

```
:decoy-okhttp        OkHttp 인터셉터 (서버+웹UI transitively 포함)   ← debugImplementation
:decoy-okhttp-noop   proceed-only 스텁                              ← releaseImplementation
:decoy-ktor          Ktor client 플러그인 (서버+웹UI transitively)  ← debugImplementation
:decoy-ktor-noop     no-op 스텁                                     ← releaseImplementation
:decoy-core          순수 JVM, 의존성 0 — 모델/저장소/Launcher (직접 선언 불필요)
:decoy-android       인스펙터 서버 + 웹 UI + 자동초기화 (직접 선언 불필요)
:app                    샘플 앱 — Retrofit·Ktor 두 스택 시연
```

no-op 아티팩트는 실제 모듈과 동일한 패키지/시그니처를 제공하므로, `main` 소스셋의 Decoy 호출 코드를 그대로 두고 의존성 스왑만으로 release에서 완전히 무력화됩니다.

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

룰은 `files/decoy/rules.json`에 순서·그룹까지 저장되어 앱 재시작 후에도 유지됩니다. 캡처 트래픽은 인메모리 전용입니다(최근 500건, 총 32 MB 예산).

모킹된 호출은 Traffic 목록에서 duration이 보라색으로 표시됩니다. duration은 (설정한 지연을 포함한) **실제 경과 시간**이고, 상세 화면에는 설정된 지연이 `Mock Delay`로 함께 표시됩니다.

### 룰셋 공유 (Export / Import)

Mock Rules 탭의 **Export**에서 전체 룰 또는 체크박스 트리로 고른 일부만 JSON 파일로 내려받을 수 있고, 각 그룹 헤더에도 그룹 단위 즉시 export 버튼이 있습니다. 받은 쪽은 **Import**에서 **Merge**(기존 룰 유지+추가) 또는 **Replace**(전체 교체)로 불러옵니다. import 직후 토스트의 **Undo** 버튼으로 import 이전 상태로 되돌릴 수 있습니다(다음 룰 변경 전까지 유효). 파일 포맷은 기기의 `rules.json`과 동일해서 서로 호환됩니다. 잘못된 정규식이 섞여 있으면 해당 룰만 건너뛰고 개수를 알려줍니다.

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
- **DNS 리바인딩 방어**: 모든 엔드포인트가 `Host` 헤더가 로컬(`localhost`/`127.0.0.1`)이 아닌 요청과 cross-origin 요청을 403으로 거부합니다 — DNS가 127.0.0.1로 재해석되는 웹 페이지도 캡처를 읽거나 룰을 주입할 수 없습니다. `/ws` 실시간 피드는 추가로 **cross-origin WebSocket을 거부**합니다.
- 자격 증명 헤더는 캡처 시점에 `[redacted]`로 **마스킹**됩니다: `Authorization`, `Proxy-Authorization`, `Cookie`/`Set-Cookie`, 흔한 API 키/토큰 이름(`X-Api-Key`, `X-Auth-Token`, …), 그리고 `*-key`/`*-token`/`*-secret`/`*-auth`로 끝나는 모든 헤더(`HeaderRedactor` 참고). 단 **헤더만** 대상입니다 — URL이나 응답 바디에 포함된 토큰은 그대로 캡처됩니다.
- Mock 룰은 서버에서 검증되므로(정규식, 상태코드, 지연, 헤더 문자셋), 조작된 룰 파일이 앱을 크래시시킬 수 없습니다.
- release 빌드는 no-op 아티팩트만 포함하므로 서버·인터셉트 코드가 APK에 존재하지 않습니다.
- 남는 위협 모델: **같은 기기에 설치된 다른 앱**이 debug 빌드의 로컬 포트에 접근할 수 있습니다. 캡처 열람뿐 아니라 **REST API로 mock 룰을 주입·삭제해 debug 앱이 받는 응답을 조작**할 수도 있습니다. 민감한 트래픽을 다루는 앱이라면 debug 빌드 배포 범위에 유의하세요.
- 절대 `implementation`으로 (모든 빌드타입에) 실제 아티팩트를 넣지 마세요 — release에 오픈 포트 서버가 포함됩니다.

### release에 Decoy가 없는지 검증하기

CI가 매 빌드마다 이와 동일한 검사를 수행합니다(`.github/workflows/ci.yml`). 로컬에서 재현하려면:

```bash
./gradlew :app:assembleRelease

# 1) dex에 서버 클래스가 없어야 함 (core 모델 + 스텁만 존재).
#    release APK는 multidex일 수 있으므로 classes*.dex 전부를 스캔합니다.
APK=$(ls app/build/outputs/apk/release/app-release*.apk | head -1)
unzip -o -q "$APK" "classes*.dex" -d dexout
DEXDUMP="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)/dexdump"
for d in dexout/classes*.dex; do "$DEXDUMP" "$d" | grep "Class descriptor"; done \
  | grep -E "Lcom/decoy/android/|Lio/ktor/server/"
#   → 출력 없음이 정상 (com/decoy/core/*와 스텁 인터셉터는 존재해도 됨)

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
- 내장 인스펙터 서버를 소비자 Ktor 버전에서 디커플링 (shade/relocate 또는 의존성 가벼운 서버로 교체) — 단일 아티팩트로 어떤 Ktor 버전이든(혹은 Ktor 미사용 앱까지) 지원

## Contributing

브랜치 모델과 릴리스 프로세스는 [CONTRIBUTING.md](CONTRIBUTING.md)에 문서화되어 있습니다.

## License

MIT — [LICENSE](LICENSE) 참고.
