import { useState } from 'react'
import { api } from '../api/client.js'
import Message from './Message.jsx'
import Brand from './Brand.jsx'

/**
 * 로그인과 회원가입.
 *
 * 화면 하나에 두 동작을 담았다. 가입 직후 바로 로그인시키므로
 * 사용자가 같은 정보를 두 번 입력하지 않는다.
 */
export default function Login({ onLoggedIn }) {
  const [customerId, setCustomerId] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)
  const [busy, setBusy] = useState(false)

  async function run(action) {
    setError(null)
    setNotice(null)
    setBusy(true)
    try {
      await action()
    } catch (e) {
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  const login = () => run(async () => {
    const me = await api.login(customerId, password)
    onLoggedIn(me)
  })

  const signUp = () => run(async () => {
    await api.signUp(customerId, password)
    // 가입에 성공하면 곧바로 로그인한다. 같은 값을 다시 치게 하지 않는다.
    const me = await api.login(customerId, password)
    onLoggedIn(me)
  })

  return (
    <div className="auth">
      <div style={{ display: 'flex', justifyContent: 'center' }}>
        <Brand size="lg" />
      </div>
      <p className="muted small" style={{ marginTop: 18 }}>
        뷰티의 모든 것, 한 번의 로그인으로
      </p>

      <div>
        <Message error={error} notice={notice} />

        <div className="auth-form">
          <input
            placeholder="아이디"
            value={customerId}
            onChange={(e) => setCustomerId(e.target.value)}
            autoComplete="username"
          />
          <input
            placeholder="비밀번호 (8자 이상)"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            onKeyDown={(e) => e.key === 'Enter' && login()}
          />
          <button className="primary" onClick={login} disabled={busy || !customerId || !password}>
            로그인
          </button>

        </div>
      </div>

      {/* 무신사·올리브영이 쓰는 가입 유도 문구 형식을 따랐다.
          "지금 가입하면, [혜택]" — 혜택을 먼저 보여주고 버튼을 뒤에 둔다. */}
      <button className="line" style={{ width: '100%' }} onClick={signUp}
              disabled={busy || !customerId || !password}>
        회원가입
      </button>

      <div className="join-benefit">
        <span className="muted small">지금 가입하면,</span>
        <strong>웰컴 적립금 30,000P 즉시 지급</strong>
      </div>
    </div>
  )
}
