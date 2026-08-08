/**
 * 성공·실패 안내.
 *
 * 서버가 내려준 문장을 그대로 보여준다. 화면에서 다시 지어내면
 * "품절입니다"와 "잔액이 부족합니다"를 구분하지 못하고 뭉뚱그리게 된다. (D4)
 */
export default function Message({ error, notice }) {
  if (error) {
    return (
      <div className="msg err">
        {error.message}
        {/* 검증 실패는 필드별로 이유가 다르다. 뭉뚱그리면 무엇을 고쳐야 할지 모른다. */}
        {error.errors && (
          <ul style={{ margin: '6px 0 0', paddingLeft: 18 }}>
            {Object.entries(error.errors).map(([field, msg]) => (
              <li key={field}>{field}: {msg}</li>
            ))}
          </ul>
        )}
      </div>
    )
  }
  if (notice) return <div className="msg ok">{notice}</div>
  return null
}
