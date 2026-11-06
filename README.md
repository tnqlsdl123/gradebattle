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

## 대천사 AI

서버 실행 후 `http://localhost:8080/angel.html`에서 로그인하고 본인의 성적을 입력합니다.
전송 안내를 읽고 동의 체크박스를 선택한 다음 **대천사에게 분석받기** 버튼을 누르면
현재 점수와 함께 문제점·실천 방법 2~3개·응원 멘트를 받습니다.
페이지 접속, 로그인, 성적 입력 또는 저장만으로는 OpenAI API를 호출하지 않습니다.

`.env`에 `OPENAI_API_KEY`를 설정합니다. `OPENAI_MODEL` 기본값은 `gpt-4.1-mini`입니다.
키는 백엔드 환경설정에서만 읽고 프론트엔드나 응답에 전달하지 않습니다.

### 입력과 계산

성적은 출석·과제·중간·기말 각각 0~100점입니다. 미확정 항목은 `null`, 확정된 0점은 `0`입니다.
최소 한 항목은 입력해야 합니다. 이수학점은 선택 입력(0.5~30학점), 목표 점수 기본값은 60점입니다.
배점은 출석 20%, 과제 20%, 중간 30%, 기말 30%로 서버에서 계산합니다.
현재 확보 점수, 남은 배점, 가능한 최고 점수, 목표에 필요한 남은 항목의 평균을 제공합니다.
목표 60점은 사용자가 바꿀 수 있는 계산 기준이며 학교의 합격·학점 기준을 의미하지 않습니다.
AI는 성적의 약점과 학습 행동을 조언하고, 코드로 계산한 점수는 변경하지 않습니다.

### 동의와 본인 성적 검증

현재는 공식 성적 DB 연동 없이 사용자가 **본인의 성적을 직접 입력하는 방식**입니다.
성적의 실제 진위까지 확인하는 기능은 없습니다. 입력값은 인증된 사용자 ID에 묶어 서버 세션에만
임시 보관하고, 조회·분석 시 세션 성적의 소유자와 현재 로그인 사용자가 일치하는지 검증합니다.
클라이언트에서 사용자 ID나 다른 사용자의 성적 ID를 지정할 수 없습니다.
로그아웃·세션 만료 시 임시 입력은 제거됩니다. 분석 결과는 DB·세션에 저장하지 않고 화면에만 표시합니다.

| 메서드 | 경로 | 설명 |
| --- | --- | --- |
| GET | `/api/ai/consent` | 전송 안내와 `noticeVersion` 조회 |
| PUT | `/api/ai/grades/me` | 본인 성적 임시 입력, `gradeVersion` 반환 |
| GET | `/api/ai/grades/me` | 본인 세션의 입력값 조회 |
| POST | `/api/ai/analyze` | 이번 요청의 명시적 동의 후 AI 분석 |

모든 AI API는 로그인이 필요하며 PUT/POST에는 기존 사용자 API와 동일한 CSRF 헤더가 필요합니다.
성적 입력 예시(모든 값은 가상 데이터):

```json
{
  "courseName": "가상 수학",
  "credits": 3,
  "attendanceScore": 100,
  "assignmentScore": 80,
  "midtermScore": 50,
  "finalScore": null,
  "targetScore": 60
}
```

동의 후 분석 요청은 다음처럼 보냅니다. 성적이 변경되면 새 `gradeVersion`과 재동의가 필요합니다.
UI는 점수 변경 및 각 분석 완료 후 체크를 해제하며, 실패 시 자동 재시도하지 않습니다.

```json
{
  "consent": true,
  "noticeVersion": "angel-v1",
  "gradeVersion": "성적 입력 응답에서 받은 값"
}
```

### 외부 전송과 개인정보 보호

- OpenAI에는 실제 과목명도 보내지 않고 `courseName: "분석 과목"`으로 익명화합니다.
  외부 데이터 필드는 이 익명 과목명·이수학점·출석/과제/중간/기말 점수 6개로 제한합니다.
  목표 점수·이름·학번·이메일·사용자 ID·비밀번호·사용자의 인증 토큰은 포함하지 않습니다.
- 백엔드가 고정된 `https://api.openai.com/v1/responses`로만 호출합니다. 리다이렉트를 따르지 않습니다.
  OpenAI 인증을 위한 서버 API 키는 해당 서버의 Authorization 헤더에만 사용합니다.
- [OpenAI 공식 문서](https://developers.openai.com/api/docs/guides/structured-outputs)에 따른 JSON Schema로 응답 형식을 지정하고
  응답 내용을 다시 검증합니다. Responses API 요청에는 `store: false`를 지정합니다.
  이는 OpenAI 측 모든 로그·보관까지 없다는 의미는 아닙니다.
- 애플리케이션은 성적 요청·AI 응답 전문·외부 실패 응답을 로그에 기록하지 않습니다.
  외부 호출 오류는 일반화된 코드와 메시지로 변환합니다. 운영에서도 HTTP 본문을 기록하는 로깅을 추가하지 마세요.
- API 키 미설정/인증 실패는 `503 AI_503_CONFIG`, 외부 한도 초과는 `503 AI_503_LIMIT`,
  통신 실패/시간 초과는 `504 AI_504`, 응답 오류는 `502 AI_502`, 분석 거절은 `422 AI_422`입니다.
- 자동화 테스트는 가상 성적과 모의 OpenAI 응답만 사용하며 실제 외부 API를 호출하지 않습니다.

던전·출결/과제 기록 관리·카드·타락천사 변신·AI 분석 기록 저장은 아직 구현하지 않았습니다.
