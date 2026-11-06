'use strict';
const element = id => document.getElementById(id);
let csrf;
let noticeVersion;
let busy = false;

async function api(path, method = 'GET', body) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') headers[csrf.headerName] = csrf.token;
  const response = await fetch(path, {
    method, credentials: 'same-origin', headers, cache: 'no-store',
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const data = await response.json();
  if (!response.ok) {
    if (response.status === 401) {
      element('analysis-panel').hidden = true;
      element('login-panel').hidden = false;
      clearResult();
      element('analysis-form').reset();
    }
    throw new Error(data.message || '요청을 완료하지 못했습니다.');
  }
  return data.result;
}

async function showAnalysis() {
  const notice = await api('/api/ai/consent');
  noticeVersion = notice.noticeVersion;
  element('notice').textContent = notice.notice;
  element('consent').disabled = false;
  element('consent').checked = false;
  element('login-panel').hidden = true;
  element('analysis-panel').hidden = false;
}

function clearResult() {
  element('result').hidden = true;
  for (const id of ['score', 'problem', 'actions', 'encouragement']) element(id).textContent = '';
}

function resetConsent() {
  element('consent').checked = false;
  element('analyze').disabled = true;
}

element('login-form').addEventListener('submit', async event => {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector('button');
  button.disabled = true;
  try {
    csrf = await api('/api/users/csrf');
    await api('/api/users/login', 'POST', Object.fromEntries(new FormData(form)));
    form.reset();
    csrf = await api('/api/users/csrf');
    await showAnalysis();
    element('status').textContent = '본인의 성적을 입력해 주세요.';
  } catch (error) {
    element('status').textContent = error.message;
  } finally {
    button.disabled = false;
  }
});

element('analysis-form').addEventListener('input', event => {
  if (event.target.id !== 'consent') {
    resetConsent();
    clearResult();
  }
});
element('consent').addEventListener('change', () => {
  element('analyze').disabled = busy || !element('consent').checked || !noticeVersion;
});

// AI 호출은 이 submit 처리에서만 실행한다. 자동 호출·재시도·백그라운드 작업은 없다.
element('analysis-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (busy || !element('consent').checked || !noticeVersion) return;
  busy = true;
  element('analyze').disabled = true;
  clearResult();
  const fields = Array.from(event.currentTarget.querySelectorAll('input'));
  const data = new FormData(event.currentTarget);
  const grades = { courseName: String(data.get('courseName')).trim() };
  for (const key of ['credits', 'attendanceScore', 'assignmentScore', 'midtermScore', 'finalScore', 'targetScore']) {
    const raw = data.get(key);
    grades[key] = raw === '' ? null : Number(raw);
  }
  fields.forEach(field => { field.disabled = true; });
  element('logout').disabled = true;
  element('status').textContent = '대천사가 분석하고 있습니다.';
  try {
    const saved = await api('/api/ai/grades/me', 'PUT', grades);
    const result = await api('/api/ai/analyze', 'POST', {
      consent: true, noticeVersion, gradeVersion: saved.gradeVersion
    });
    const score = result.score;
    element('score').textContent = `현재 확보 ${score.earnedScore}점 / 남은 배점 ${score.remainingWeight}% / 목표 ${score.targetScore}점. `
      + (score.remainingWeight ? `남은 항목에 필요한 평균: ${score.requiredRemainingAverage}점. ` : '')
      + (score.targetReachable ? '남은 항목 만점 가정 시 목표 도달 가능' : '현재 조건으로 목표 도달 어려움');
    element('problem').textContent = result.advice.problem;
    for (const action of result.advice.actions) {
      const item = document.createElement('li');
      item.textContent = action;
      element('actions').append(item);
    }
    element('encouragement').textContent = result.advice.encouragement;
    element('result').hidden = false;
    element('status').textContent = '분석을 완료했습니다. 결과는 DB에 저장하지 않습니다.';
  } catch (error) {
    element('status').textContent = error.message;
  } finally {
    busy = false;
    fields.forEach(field => { field.disabled = false; });
    element('logout').disabled = false;
    resetConsent();
  }
});

element('logout').addEventListener('click', async () => {
  try {
    await api('/api/users/logout', 'POST');
    clearResult();
    element('analysis-form').reset();
    resetConsent();
    element('analysis-panel').hidden = true;
    element('login-panel').hidden = false;
    csrf = await api('/api/users/csrf');
    element('status').textContent = '로그아웃했습니다.';
  } catch (error) {
    element('status').textContent = error.message;
  }
});

(async () => {
  try {
    csrf = await api('/api/users/csrf');
    await api('/api/users/me');
    await showAnalysis();
  } catch (error) {
    element('status').textContent = error.message;
  }
})();
