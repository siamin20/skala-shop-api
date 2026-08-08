import { useState } from 'react'

/**
 * 회원가입 폼과 입력 검증. (D36)
 *
 * <h2>왜 화면에서도 검증하는가</h2>
 *
 * <p>서버가 이미 검증한다(D3). 그런데도 화면에서 다시 하는 이유는 <b>시점</b> 때문이다.
 * 서버 검증은 제출한 뒤에야 결과가 온다. 사용자는 다 채우고 버튼을 누른 다음에야
 * "비밀번호가 짧습니다"를 보게 된다.
 *
 * <p>화면 검증은 <b>입력하는 중에</b> 알려준다. 고칠 것을 그 자리에서 안다.
 *
 * <p>서버 검증을 대신하는 것이 아니다. 화면을 거치지 않고 API를 직접 부르면
 * 이 코드는 통째로 건너뛰어진다. <b>둘 다 있어야 한다.</b>
 *
 * <h2>언제 오류를 보여주는가</h2>
 *
 * <p>입력하자마자 빨간 글씨를 띄우지 않는다. 아이디를 한 글자 쳤을 때
 * "4자 이상"이라고 하면 아직 치는 중인 사람을 나무라는 꼴이다.
 * <b>칸을 벗어났을 때(blur)</b> 처음 보여주고, 그 뒤로는 입력할 때마다 갱신한다.
 */

const RULES = {
  customerId: (v) => {
    if (!v) return '아이디를 입력해 주세요'
    if (!/^[a-zA-Z0-9]{4,20}$/.test(v)) return '영문·숫자 4~20자로 입력해 주세요'
    return null
  },
  password: (v) => {
    if (!v) return '비밀번호를 입력해 주세요'
    if (v.length < 8) return '8자 이상 입력해 주세요'
    // 영문과 숫자를 섞게 한다. 8자여도 전부 숫자면 쉽게 뚫린다.
    if (!/[a-zA-Z]/.test(v) || !/\d/.test(v)) return '영문과 숫자를 함께 사용해 주세요'
    // BCrypt는 72바이트를 넘으면 조용히 잘라낸다. 그 뒤 글자는 검증에 쓰이지 않아
    // 서로 다른 비밀번호가 같은 것으로 취급된다. (D14)
    if (new TextEncoder().encode(v).length > 72) return '비밀번호가 너무 깁니다'
    return null
  },
  passwordConfirm: (v, all) => {
    if (!v) return '비밀번호를 한 번 더 입력해 주세요'
    if (v !== all.password) return '비밀번호가 일치하지 않습니다'
    return null
  },
}

export default function SignUpForm({ onSubmit, onCancel, busy }) {
  const [values, setValues] = useState({ customerId: '', password: '', passwordConfirm: '' })
  const [touched, setTouched] = useState({})

  const errors = Object.fromEntries(
    Object.entries(RULES).map(([field, rule]) => [field, rule(values[field], values)]))

  const valid = Object.values(errors).every((e) => e === null)

  /*
   * 이전 상태를 인자로 받아 갱신한다. { ...values } 처럼 바깥 변수를 쓰면 안 된다.
   *
   * 이 핸들러들은 만들어진 시점의 values/touched를 붙잡고 있다. 리렌더 사이에
   * 두 번 호출되면 두 번째가 첫 번째를 덮어쓴다.
   *
   * 실제로 그랬다. 세 칸을 연달아 벗어나면 마지막 칸의 오류만 남고
   * 앞의 두 개가 사라졌다. 한 칸씩 천천히 옮기면 리렌더가 끼어들어 안 보이지만,
   * 자동완성처럼 한꺼번에 채워지는 경우에는 그대로 드러난다.
   */
  const set = (field) => (e) => {
    const value = e.target.value
    setValues((prev) => ({ ...prev, [field]: value }))
  }
  const blur = (field) => () => setTouched((prev) => ({ ...prev, [field]: true }))
  const show = (field) => touched[field] && errors[field]

  return (
    <div className="auth-form">
      <Field label="아이디" hint="영문·숫자 4~20자">
        <input
          value={values.customerId}
          onChange={set('customerId')}
          onBlur={blur('customerId')}
          className={show('customerId') ? 'invalid' : ''}
          placeholder="아이디"
          autoComplete="username"
        />
        {show('customerId') && <p className="field-err">{errors.customerId}</p>}
      </Field>

      <Field label="비밀번호" hint="영문·숫자 조합 8자 이상">
        <input
          type="password"
          value={values.password}
          onChange={set('password')}
          onBlur={blur('password')}
          className={show('password') ? 'invalid' : ''}
          placeholder="비밀번호"
          autoComplete="new-password"
        />
        {show('password') && <p className="field-err">{errors.password}</p>}
      </Field>

      <Field label="비밀번호 확인">
        <input
          type="password"
          value={values.passwordConfirm}
          onChange={set('passwordConfirm')}
          onBlur={blur('passwordConfirm')}
          className={show('passwordConfirm') ? 'invalid' : ''}
          placeholder="비밀번호 확인"
          autoComplete="new-password"
        />
        {show('passwordConfirm') && <p className="field-err">{errors.passwordConfirm}</p>}
      </Field>

      <button
        className="primary"
        disabled={!valid || busy}
        onClick={() => onSubmit(values.customerId, values.password)}
      >
        가입하고 시작하기
      </button>
      <button className="line" onClick={onCancel} disabled={busy}>
        로그인으로 돌아가기
      </button>
    </div>
  )
}

function Field({ label, hint, children }) {
  return (
    <div className="field">
      <label>
        {label}
        {hint && <span className="field-hint">{hint}</span>}
      </label>
      {children}
    </div>
  )
}
