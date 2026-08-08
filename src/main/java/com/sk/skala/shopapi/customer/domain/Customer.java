package com.sk.skala.shopapi.customer.domain;

import com.sk.skala.shopapi.global.common.Money;
import com.sk.skala.shopapi.global.error.BusinessException;
import com.sk.skala.shopapi.global.error.ErrorCode;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 쇼핑몰 고객.
 *
 * <p>식별자가 자동 증가 숫자가 아니라 사용자가 정한 문자열 {@code customerId}다.
 * 과제 명세를 따른 것이며, 로그인 아이디가 곧 기본 키가 된다.
 *
 * <p>D2: 포인트는 {@link #deductPoint(Money)}와 {@link #refundPoint(Money)}로만 바뀐다.
 * Setter가 없으므로 "잔액 검사 없이 포인트를 깎는" 코드는 작성 자체가 불가능하다.
 * 규칙을 서비스에 두면 새 호출 경로가 생길 때마다 검사를 다시 넣어야 하고, 한 번만 빠뜨려도 잔액이 깨진다.
 *
 * <p>비밀번호는 BCrypt 해시로만 저장한다(D5). 이 클래스는 해싱을 직접 하지 않고
 * 이미 해시된 값을 받는다. 암호화 방식을 아는 것은 도메인이 아니라 인증 서비스의 몫이다.
 *
 * <p>{@code @Version}을 이용한 낙관적 락은 P4에서 추가한다.
 */
@Entity
@Table(name = "customer")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Customer {

    /** 로그인 아이디이자 기본 키. */
    @Id
    @Column(name = "customer_id", length = 50)
    private String customerId;

    /**
     * BCrypt 해시.
     *
     * <p>BCrypt 결과는 항상 60자다. 넉넉히 100으로 잡아 알고리즘을 바꿔도 견디게 한다.
     */
    @Column(name = "customer_password", nullable = false, length = 100)
    private String password;

    /**
     * 역할. 인가 판단의 근거다. (D17)
     *
     * <p>{@code EnumType.STRING}으로 저장한다. 기본값인 {@code ORDINAL}은 순서 번호를 넣는데,
     * 나중에 enum 상수 사이에 새 값을 끼워 넣으면 <b>기존 행의 의미가 통째로 밀린다.</b>
     * 문자열이면 그런 사고가 없고 DB를 직접 봐도 값을 읽을 수 있다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "customer_role", nullable = false, length = 20)
    private Role role;

    /** 보유 포인트. 주문 시 차감되고 취소 시 환급된다. */
    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "customer_point", nullable = false))
    private Money point;

    /**
     * 토큰 버전. 로그아웃하면 올라간다. (D25)
     *
     * <p>리프레시 토큰에 발급 시점의 이 값을 담아두고, 재발급할 때 지금 값과 비교한다.
     * 다르면 거부한다. 로그아웃으로 값이 올라가면 <b>그 전에 발급된 리프레시 토큰이
     * 한꺼번에 무효가 된다.</b>
     *
     * <p>이게 없으면 로그아웃은 쿠키만 지운다. 브라우저에서는 사라지지만 토큰 자체는
     * 그대로 유효해서, 공격자가 이미 확보했다면 <b>최대 14일 동안 계속 재발급받을 수 있다.</b>
     *
     * <p>폐기 목록 대신 숫자 하나를 쓰는 이유는 비용이다. 목록은 발급된 토큰 수만큼
     * 행이 늘고 만료된 것을 지우는 배치가 따로 필요하다. 버전은 고객당 8바이트로 끝난다.
     *
     * <p><b>액세스 토큰에는 적용하지 않는다.</b> 적용하려면 매 요청마다 DB를 읽어야 해서
     * JWT를 쓴 이유인 무상태성이 사라진다. 액세스 토큰은 15분이면 만료되므로
     * 로그아웃 후 최대 15분의 창이 남는다. 그 대가로 요청마다의 DB 조회를 피한다.
     */
    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    /**
     * 새 고객을 만든다.
     *
     * @param customerId     로그인 아이디. 비어 있을 수 없다
     * @param hashedPassword 이미 BCrypt로 해시된 비밀번호. 평문을 넘기면 안 된다
     * @param initialPoint   가입 시 지급할 초기 포인트
     * @throws IllegalArgumentException 아이디나 비밀번호가 비어 있는 경우
     */
    public Customer(String customerId, String hashedPassword, Money initialPoint) {
        this(customerId, hashedPassword, initialPoint, Role.CUSTOMER);
    }

    /**
     * 역할을 지정해 고객을 만든다. 관리자 계정 생성에만 쓴다.
     *
     * <p>일반 가입 경로가 역할을 고를 수 없게 생성자를 나눴다. 하나로 두면
     * 회원가입 요청에서 역할이 흘러들어올 여지가 생긴다.
     */
    public Customer(String customerId, String hashedPassword, Money initialPoint, Role role) {
        if (customerId == null || customerId.isBlank()) {
            throw new IllegalArgumentException("고객 아이디는 비어 있을 수 없습니다");
        }
        if (hashedPassword == null || hashedPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호는 비어 있을 수 없습니다");
        }
        this.customerId = customerId;
        this.password = hashedPassword;
        this.point = initialPoint == null ? Money.ZERO : initialPoint;
        this.role = role;
    }

    /** 관리자인지 확인한다. */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * 포인트를 차감한다.
     *
     * <p>잔액 검사를 이 메서드 안에서 하는 이유는, 호출하는 쪽에 맡기면 검사를 빠뜨린 경로가
     * 하나만 생겨도 잔액이 음수가 되기 때문이다. 여기서 막으면 어느 경로로 들어와도 안전하다.
     *
     * <p>부족한 금액을 메시지에 담아 사용자가 얼마를 더 채워야 하는지 알 수 있게 한다.
     *
     * @param amount 차감할 금액
     * @throws BusinessException 잔액이 부족하면 {@link ErrorCode#INSUFFICIENT_POINT}
     */
    public void deductPoint(Money amount) {
        if (point.isLessThan(amount)) {
            throw new BusinessException(
                    ErrorCode.INSUFFICIENT_POINT,
                    "필요 %s, 보유 %s".formatted(amount, point));
        }
        this.point = point.minus(amount);
    }

    /** 주문 취소로 포인트를 돌려준다. */
    public void refundPoint(Money amount) {
        this.point = point.plus(amount);
    }

    /**
     * 포인트를 충전한다.
     *
     * <p>구현은 {@link #refundPoint(Money)}와 같지만 메서드를 나눈다.
     * 충전 코드에서 {@code refundPoint}를 부르면 읽는 사람이 "여기서 왜 환불이 일어나지"라고
     * 멈추게 되고, 나중에 환급에만 로그나 이력이 필요해졌을 때 충전까지 함께 딸려간다.
     * 같은 동작이라도 <b>다른 사건</b>이면 이름을 나눠 둔다.
     */
    public void chargePoint(Money amount) {
        this.point = point.plus(amount);
    }

    /**
     * 포인트를 특정 값으로 맞춘다. 관리자 조정 전용이다.
     *
     * <p>{@link #chargePoint(Money)}·{@link #refundPoint(Money)}와 달리 이전 잔액을 무시하고
     * 덮어쓴다. 그래서 <b>고객 본인에게는 절대 열어서는 안 되는 동작</b>이다.
     * 인가에서 관리자만 호출하도록 막는다(P2).
     *
     * <p>도메인에 두는 이유는, 서비스가 필드를 직접 대입하게 두면 Setter를 없앤 의미가 사라지기 때문이다.
     * 위험한 동작일수록 이름을 붙여 드러내는 편이 낫다.
     */
    public void adjustPointTo(Money point) {
        this.point = point;
    }

    /** 이 고객이 {@code customerId}의 주인인지 확인한다. 남의 자원 접근을 막을 때 쓴다. */
    /**
     * 발급된 리프레시 토큰을 모두 무효화한다.
     *
     * <p>로그아웃할 때 부른다. 값이 하나 올라가면 이전 버전을 담은 토큰은
     * 전부 재발급 심사를 통과하지 못한다. 여러 기기에서 로그인해 있었다면
     * <b>모두 함께 끊긴다.</b> 기기별로 끊으려면 세션 식별자가 따로 필요한데,
     * 그건 무상태 JWT의 범위를 벗어난다.
     */
    public void invalidateTokens() {
        this.tokenVersion++;
    }

    /** 리프레시 토큰에 담긴 버전이 지금도 유효한지 확인한다. */
    public boolean hasTokenVersion(long tokenVersion) {
        return this.tokenVersion == tokenVersion;
    }

    public boolean isOwner(String customerId) {
        return this.customerId.equals(customerId);
    }
}
