import { useEffect, useState } from 'react'
import { api } from '../api/client.js'
import { preferences } from '../auth/preferences.js'
import Brand from './Brand.jsx'
import SignUpForm from './SignUpForm.jsx'

/**
 * 로그인과 회원가입.
 *
 * <h2>아이디 저장</h2>
 *
 * <p>아이디만 {@code localStorage}에 둔다. 비밀번호는 저장하지 않는다.
 * 저장하면 XSS 한 번에 계정이 통째로 넘어가고, 공용 PC에서는 다음 사람이 그대로 로그인한다.
 *
 * <h2>자동 로그인</h2>
 *
 * <p>토큰 자체는 이미 유지된다. 로그인하면 리프레시 토큰이 HttpOnly 쿠키로 14일 저장되고,
 * 앱을 다시 열 때 그 쿠키로 액세스 토큰을 새로 받는다(D18).
 * 액세스 토큰을 저장하지 않는데도 유지되는 것이 핵심이다. <b>저장이 아니라 갱신으로 푼다.</b>
 *
 * <p>체크박스는 그 복원을 <b>할지 말지</b>를 정한다. 꺼두면 앱을 다시 열 때
 * 쿠키가 있어도 쓰지 않고 로그인 화면을 보여준다. 공용 기기를 쓰는 사람에게 필요한 선택이다.
 *
 * <h2>선택은 로그아웃해도 남는다</h2>
 *
 * <p>자동 로그인을 켜뒀던 사람이 로그아웃하고 다시 들어오면 <b>체크가 켜진 채로</b> 보인다.
 * 로그아웃이 지우는 것은 세션이지 사용자의 설정이 아니다. (D39)
 */
export default function Login({ onLoggedIn, onError }) {
  const [mode, setMode] = useState('login')
  const [customerId, setCustomerId] = useState('')
  const [password, setPassword] = useState('')
  const [remember, setRemember] = useState(false)
  const [autoLogin, setAutoLogin] = useState(false)
  const [busy, setBusy] = useState(false)

  // 지난번 선택을 그대로 되살린다. 로그아웃해도 남아 있는 값이다.
  useEffect(() => {
    const saved = preferences.savedId()
    if (saved) {
      setCustomerId(saved)
      setRemember(true)
    }
    setAutoLogin(preferences.autoLogin())
  }, [])

  async function run(action) {
    setBusy(true)
    try {
      const me = await action()
      preferences.save({ customerId: me.customerId ?? customerId, remember, autoLogin })
      onLoggedIn(me)
    } catch (e) {
      onError(e)
    } finally {
      setBusy(false)
    }
  }

  const login = () => run(() => api.login(customerId, password))

  const signUp = (id, pw) => {
    setCustomerId(id)
    return run(async () => {
      await api.signUp(id, pw)
      // 가입에 성공하면 곧바로 로그인한다. 같은 값을 다시 치게 하지 않는다.
      return api.login(id, pw)
    })
  }

  return (
    <div className="auth">
      <div style={{ display: 'flex', justifyContent: 'center' }}>
        <Brand size="lg" />
      </div>

      {mode === 'login' ? (
        <>
          <p className="muted small" style={{ marginTop: 18 }}>
            뷰티의 모든 것, 한 번의 로그인으로
          </p>

          <div className="auth-form">
            <input
              placeholder="아이디"
              value={customerId}
              onChange={(e) => setCustomerId(e.target.value)}
              autoComplete="username"
            />
            <input
              placeholder="비밀번호"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              onKeyDown={(e) => e.key === 'Enter' && customerId && password && login()}
            />

            <div className="check-row">
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                />
                아이디 저장
              </label>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={autoLogin}
                  onChange={(e) => setAutoLogin(e.target.checked)}
                />
                자동 로그인
              </label>
            </div>

            <button className="primary" onClick={login} disabled={busy || !customerId || !password}>
              로그인
            </button>
            <button className="line" onClick={() => setMode('signup')} disabled={busy}>
              회원가입
            </button>
          </div>

          <div className="join-benefit">
            <span className="muted small">지금 가입하면,</span>
            <strong>웰컴 적립금 30,000P 즉시 지급</strong>
          </div>


        </>
      ) : (
        <>
          <p className="muted small" style={{ marginTop: 18 }}>
            30초면 가입이 끝납니다
          </p>
          <SignUpForm onSubmit={signUp} onCancel={() => setMode('login')} busy={busy} />
        </>
      )}
    </div>
  )
}
