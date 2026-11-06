# gradebattle

공부 기록으로 교수님을 토벌하고 카드를 수집하는 서비스의 백엔드입니다.

## 실행

1. Java 17과 MySQL을 준비하고 `gradefront` 데이터베이스를 생성합니다.
2. `.env.example`을 `.env`로 복사해 DB 접속 정보를 입력합니다. 실제 비밀번호와 API 키는 커밋하지 않습니다.
3. 프론트엔드 주소를 `CORS_ALLOWED_ORIGINS`에 지정합니다. 여러 주소는 쉼표로 구분합니다.
4. Windows에서는 `.\gradlew.bat bootRun`으로 실행합니다. Flyway가 `users` 테이블을 생성합니다.

테스트는 `.\gradlew.bat test`로 실행합니다. 테스트 DB는 메모리 H2이며 로컬 MySQL과 `.env`를 사용하지 않습니다.

## 사용자 API

세션 쿠키(`JSESSIONID`)로 로그인 상태를 유지합니다. 회원가입은 자동 로그인하지 않습니다.

| 메서드 | 경로 | 요청 본문 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/users/csrf` | 없음 | 로그인 없이 CSRF 토큰 발급 |
| POST | `/api/users/signup` | `loginId`, `password`, `nickname` | 회원가입, 성공 시 201 |
| POST | `/api/users/login` | `loginId`, `password` | 로그인 |
| POST | `/api/users/logout` | 없음 | 세션 종료 |
| GET | `/api/users/me` | 없음 | 내 정보 조회 |
| PATCH | `/api/users/me` | `nickname` | 내 닉네임 변경 |

아이디는 영문·숫자·밑줄 4~50자, 비밀번호는 8자 이상이면서 UTF-8 72바이트 이하,
닉네임은 공백을 제외한 내용이 있어야 하며 최대 30자입니다. 중복 아이디는 409,
잘못된 로그인은 401, 잘못된 입력은 400, CSRF 토큰 누락은 403으로 응답합니다.

사용자 응답의 `result`에는 `userId`, `loginId`, `nickname`, `totalXp`, `createdAt`만 포함됩니다.
비밀번호는 BCrypt 해시로 저장하며 응답에 포함하지 않습니다. 누적 경험치는 0부터 시작하고 클라이언트가 변경할 수 없습니다.

### 프론트엔드 호출 예시

먼저 CSRF 토큰을 받은 뒤 POST/PATCH 요청에 해당 헤더를 넣어야 합니다.
모든 요청에 `credentials: 'include'`를 지정해 같은 세션을 유지합니다.

```javascript
const base = 'http://localhost:8080';
const csrfResponse = await fetch(`${base}/api/users/csrf`, { credentials: 'include' });
const { result: csrf } = await csrfResponse.json();

const signup = await fetch(`${base}/api/users/signup`, {
  method: 'POST',
  credentials: 'include',
  headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
  body: JSON.stringify({ loginId: 'student1', password: 'example123!', nickname: '용사' })
});
```

로그인도 같은 방식으로 호출합니다. 로그아웃 후에는 CSRF 토큰을 다시 발급받습니다.
세션은 30분 동안 요청이 없으면 만료되며, 만료된 경우에도 토큰을 다시 발급받아 로그인합니다.
현재 쿠키는 `HttpOnly`, `SameSite=Lax`입니다. HTTPS 배포 시 `SESSION_COOKIE_SECURE=true`를 설정합니다.
프론트엔드와 백엔드는 같은 사이트에 배치하는 구성을 기준으로 합니다.

현재 범위는 사용자 기능입니다. 던전·출결·과제·점수 계산·카드·AI 분석은 아직 구현하지 않았습니다.
