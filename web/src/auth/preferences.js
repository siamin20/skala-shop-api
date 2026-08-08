/**
 * 로그인 화면의 선택 상태. (D39)
 *
 * <p>아이디 저장과 자동 로그인은 <b>로그아웃해도 남아야 한다.</b>
 * 자동 로그인을 켜뒀던 사람이 로그아웃한 뒤 다시 들어오면, 체크가 풀려 있는 것이 아니라
 * 켜진 채로 있어야 "내가 켜둔 설정"이 유지된 것으로 느껴진다.
 *
 * <p>로그아웃이 지우는 것은 <b>세션(토큰)</b>이지 사용자의 선택이 아니다.
 */

const KEYS = {
  savedId: 'skala-beauty:saved-id',
  autoLogin: 'skala-beauty:auto-login',
}

export const preferences = {
  /** 저장된 아이디. 비밀번호는 절대 저장하지 않는다. */
  savedId() {
    return localStorage.getItem(KEYS.savedId) ?? ''
  },

  /**
   * 자동 로그인 사용 여부.
   *
   * <p>기본은 꺼짐이다. 공용 기기에서 처음 쓰는 사람이 자기도 모르게
   * 로그인 상태로 남는 일이 없어야 한다.
   */
  autoLogin() {
    return localStorage.getItem(KEYS.autoLogin) === 'true'
  },

  save({ customerId, remember, autoLogin }) {
    if (remember) localStorage.setItem(KEYS.savedId, customerId)
    else localStorage.removeItem(KEYS.savedId)

    // 껐을 때도 남긴다. "false를 골랐다"는 것도 선택이다.
    localStorage.setItem(KEYS.autoLogin, String(autoLogin))
  },
}
