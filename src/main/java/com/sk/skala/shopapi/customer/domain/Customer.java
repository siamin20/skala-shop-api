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
import jakarta.persistence.Version;
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
 * <p>{@code @Version}을 이용한 낙관적 락으로 갱신 유실을 막는다. (D22)
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
     * 낙관적 락 버전. (D22)
     *
     * <p>Hibernate가 이 값을 {@code UPDATE ... WHERE version = ?}에 넣어 갱신하고,
     * 바뀐 행이 0이면 <b>"내가 읽은 뒤 누군가 먼저 바꿨다"</b>고 판단해 예외를 던진다.
     * 이게 없으면 두 요청이 같은 잔액을 읽고 각자 계산해 덮어써 <b>갱신 유실</b>이 난다.
     *
     * <pre>
     *   요청A: 100만원 읽음 → 1만원 차감 → 99만원 저장
     *   요청B: 100만원 읽음 → 2만원 차감 → 98만원 저장   ← A의 차감이 사라진다
     * </pre>
     *
     * <p>비관적 락이 아니라 낙관적 락인 이유는 <b>경합이 드물기 때문</b>이다.
     * 포인트는 본인만 바꾼다. 충돌은 같은 사람이 더블클릭하거나 재시도할 때만 생긴다.
     * 드문 충돌을 위해 모든 요청에 락 대기를 걸면 처리량만 깎인다.
     *
     * <p>필드에 직접 접근할 일이 없어 Getter를 열지 않는다. Hibernate가 관리한다.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

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
    public boolean isOwner(String customerId) {
        return this.customerId.equals(customerId);
    }
}
