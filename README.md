# 멀티룸 채팅 서버 및 TCP/IP 프로토콜 설계 프로젝트

## 1. 프로젝트 개요
Java `ServerSocket`/`Socket` 기반의 멀티룸 채팅 서버와 콘솔 클라이언트입니다. 단순 채팅뿐 아니라 ThreadPool 상한, IP별 연결 제한, 전체 로그인 인원 제한, 인증 토큰, 요청 빈도 제한, 로그인/유휴 타임아웃, 안전 종료, 접속 로그를 포함하여 서버 안정성 제어 정책이 코드에 드러나도록 구성했습니다.

## 2. 주요 기능
- TCP Socket 서버 및 콘솔 클라이언트
- 다중 클라이언트 동시 접속 처리
- 로그인, UUID 인증 토큰 발급
- 채팅방 생성/입장/퇴장/자동 삭제
- 방 단위 일반 채팅 및 사용자 간 귓속말
- 접속자/방 목록 조회
- ThreadPoolExecutor 기반 작업자 풀 및 bounded queue
- ConcurrentHashMap 기반 세션/방/IP 카운터 관리
- 동일 IP 연결 수 제한, 전체 로그인 수 제한, rate limit
- 로그인 타임아웃 및 유휴 연결 타임아웃
- `/stop` 기반 서버 안전 종료
- `logs/chat-server.log` 접속/운영 이벤트 저장
- 4개 시나리오 부하 테스트 프로그램

## 3. 사용 기술
- Java 17
- Maven
- Gson 2.11.0
- TCP Socket (`ServerSocket`, `Socket`)
- `ThreadPoolExecutor`, `ArrayBlockingQueue`, `AbortPolicy`
- `ConcurrentHashMap`, `AtomicInteger`

## 4. 전체 요구사항 충족표
| 요구사항 | 구현 위치 |
|---|---|
| TCP Socket 서버 | `server.ChatServer`, `server.ClientHandler` |
| 다중 클라이언트 | `ThreadPoolExecutor`에 `ClientHandler` 제출 |
| 로그인/토큰 | `ClientHandler`, `TokenService`, `SessionManager` |
| 멀티룸 | `RoomManager`, `ChatRoom`, `RoomInfo` |
| 귓속말 | `ClientHandler#whisper` |
| 접속 로그 | `ChatLogService` |
| 안전 종료 | `ServerConsole`, `ChatServer#shutdown` |
| Thread Pool 상한 | `ChatServer` 생성자 |
| ConcurrentHashMap | 세션, 방, IP 카운터, rate limit 저장소 |
| JSON 포맷 | `Message`, `MessageType`, `JsonMessageConverter` |
| 전체 접속자 제한 | 로그인 처리 시 `MAX_LOGGED_IN_CLIENTS` 확인 |
| 동일 IP 제한 | `ConnectionLimitService` |
| 요청 빈도 제한 | `RateLimitService` |
| 로그인/유휴 타임아웃 | `ClientHandler`의 `setSoTimeout` |
| 부하 테스트 | `test.LoadTestClient` |
| 콘솔 UI | `client.ChatClient` |

## 5. 프로젝트 구조
```text
src/main/java/
 ├─ server/    ChatServer, ClientHandler, ServerConsole, ServerConfig
 ├─ client/    ChatClient, ServerMessageListener
 ├─ session/   ClientSession, SessionManager
 ├─ room/      ChatRoom, RoomInfo, RoomManager
 ├─ security/  ConnectionLimitService, RateLimitService, TokenService
 ├─ protocol/  Message, MessageType, JsonMessageConverter
 ├─ log/       ChatLogService
 └─ test/      LoadTestClient
```

## 6. 실행 방법
### 빌드
```bash
mvn clean package
```

### 서버 실행
```bash
mvn exec:java -Dexec.mainClass=server.ChatServer
```
서버 콘솔에서 종료하려면 다음을 입력합니다.
```text
/stop
```

### 클라이언트 실행
```bash
mvn exec:java -Dexec.mainClass=client.ChatClient
```
원격 호스트에 접속하려면 첫 번째 인자로 호스트를 넘깁니다.
```bash
mvn exec:java -Dexec.mainClass=client.ChatClient -Dexec.args="127.0.0.1"
```

## 7. 서버 설정값
`server.ServerConfig`에서 관리합니다.

| 상수 | 값 | 의미 |
|---|---:|---|
| `PORT` | 5000 | TCP listen port |
| `MAX_LOGGED_IN_CLIENTS` | 50 | 최대 로그인 세션 수 |
| `MAX_CONNECTIONS_PER_IP` | 3 | 동일 IP 동시 연결 수 |
| `CORE_POOL_SIZE` | 10 | 기본 worker 수 |
| `MAX_POOL_SIZE` | 20 | 최대 worker 수 |
| `TASK_QUEUE_CAPACITY` | 50 | 대기 큐 크기 |
| `LOGIN_TIMEOUT_MILLIS` | 30000 | 로그인 제한 시간 |
| `IDLE_TIMEOUT_MILLIS` | 300000 | 로그인 후 유휴 제한 시간 |
| `RATE_LIMIT_WINDOW_MILLIS` | 10000 | rate limit 윈도우 |
| `RATE_LIMIT_MAX_REQUESTS` | 20 | 윈도우당 최대 요청 수 |
| `THREAD_KEEP_ALIVE_SECONDS` | 60 | 초과 스레드 유지 시간 |

## 8. 클라이언트 명령어
- `/help`: 도움말
- `/rooms`: 방 목록 조회
- `/create [방이름]`: 방 생성 및 자동 입장
- `/join [방이름]`: 기존 방 입장
- `/leave`: 현재 방 퇴장
- `/users`: 접속자 목록
- `/w [닉네임] [메시지]`: 귓속말
- `/quit`: 로그아웃
- `/`로 시작하지 않는 문장: 현재 방에 일반 채팅 전송

## 9. JSON 프로토콜 예시
### 로그인 요청/응답
```json
{"type":"LOGIN","nickname":"kanghyun"}
```
```json
{"type":"LOGIN_SUCCESS","success":true,"message":"로그인 성공","nickname":"kanghyun","token":"uuid-token-value"}
```

### 방 생성
```json
{"type":"CREATE_ROOM","token":"token-value","roomName":"JavaStudy"}
```

### 채팅 브로드캐스트
```json
{"type":"CHAT_BROADCAST","roomName":"JavaStudy","sender":"kanghyun","message":"안녕하세요!","timestamp":"2026-05-15 16:10:11"}
```

### 서버 혼잡
```json
{"type":"SERVER_BUSY","success":false,"message":"서버가 혼잡하여 현재 접속을 처리할 수 없습니다."}
```

## 10. ThreadPoolExecutor를 사용한 이유
`Executors.newFixedThreadPool`은 내부 큐 크기와 rejection policy가 명확히 드러나지 않아 과제의 자원 보호 요구사항에 적합하지 않습니다. 이 프로젝트는 `ThreadPoolExecutor`를 직접 생성하여 `corePoolSize`, `maximumPoolSize`, `ArrayBlockingQueue`, `AbortPolicy`, keep-alive 시간을 명시합니다. 큐와 스레드가 모두 포화되면 `RejectedExecutionException`을 잡아 `SERVER_BUSY`를 응답하고 소켓을 닫습니다.

## 11. ConcurrentHashMap을 사용한 이유
여러 `ClientHandler`가 동시에 로그인, 로그아웃, 방 입장, 브로드캐스트를 수행하므로 공유 상태는 thread-safe해야 합니다. `SessionManager`는 닉네임/토큰 인덱스, `RoomManager`와 `ChatRoom`은 방/멤버 목록, `ConnectionLimitService`는 IP 카운터, `RateLimitService`는 요청 시각 저장소를 `ConcurrentHashMap`으로 관리합니다.

## 12. 인증 토큰 구현
로그인 성공 시 `TokenService`가 `UUID.randomUUID().toString()`으로 토큰을 발급합니다. 서버는 로그인 이후 모든 요청의 `token` 필드를 `SessionManager`의 `tokenSessionMap`으로 검증하며, 실패 시 `AUTH_FAIL`을 반환합니다.

## 13. 동일 IP 연결 제한 구현
`ConnectionLimitService`는 `ConcurrentHashMap<String, AtomicInteger>`로 IP별 연결 수를 관리합니다. `accept` 직후 IP 카운터 획득을 시도하고, `MAX_CONNECTIONS_PER_IP` 이상이면 `CONNECTION_LIMIT_EXCEEDED`를 전송한 뒤 연결을 닫고 `IP_CONNECTION_REJECT`를 기록합니다. 모든 cleanup 경로에서 카운터를 감소시키며 0이 되면 map에서 제거합니다.

## 14. Rate Limit 구현
`RateLimitService`는 토큰별 `Deque<Long>`에 요청 시각을 저장하는 sliding-window 방식입니다. 로그인 이후 요청마다 오래된 timestamp를 제거하고 10초 내 요청이 20개 이상이면 해당 요청만 `RATE_LIMIT_EXCEEDED`로 거절합니다.

## 15. 로그인/유휴 타임아웃 구현
`ClientHandler`는 로그인 전 `socket.setSoTimeout(LOGIN_TIMEOUT_MILLIS)`를 설정합니다. 로그인 성공 후에는 `IDLE_TIMEOUT_MILLIS`로 변경하여 일정 시간 요청이 없으면 `IDLE_TIMEOUT` 전송, 방 퇴장, 세션 제거, 토큰 제거, IP 카운터 감소, 소켓 close를 수행합니다.

## 16. 접속 로그 저장 방식
`ChatLogService`가 `logs/chat-server.log`에 timestamp와 이벤트명을 append합니다. `log` 메서드는 `synchronized`로 구현되어 멀티스레드 환경에서도 로그 라인이 섞이지 않습니다.

## 17. 부하 테스트 실행 방법
서버를 먼저 실행한 뒤 별도 터미널에서 실행합니다.

```bash
mvn exec:java -Dexec.mainClass=test.LoadTestClient -Dexec.args="NORMAL_LOAD"
mvn exec:java -Dexec.mainClass=test.LoadTestClient -Dexec.args="RATE_LIMIT_TEST"
mvn exec:java -Dexec.mainClass=test.LoadTestClient -Dexec.args="SAME_IP_LIMIT_TEST"
mvn exec:java -Dexec.mainClass=test.LoadTestClient -Dexec.args="THREAD_POOL_SATURATION_TEST"
```

참고: 기본 설정은 동일 IP 동시 연결을 3개로 제한하므로, localhost에서 `NORMAL_LOAD` 30명을 완전히 동시에 실행하면 일부 연결은 의도적으로 제한될 수 있습니다. 이는 IP 제한 정책이 정상 동작한다는 의미입니다.

## 18. 향후 개선점: NIO 기반 서버 리팩토링
현재 구현은 학습 목적에 맞춘 blocking I/O + bounded thread pool 구조입니다. 대규모 접속을 더 효율적으로 처리하려면 Java NIO `Selector` 기반 event loop, back-pressure, 비동기 로그 writer, heartbeat/ping-pong 프로토콜로 리팩토링할 수 있습니다.
