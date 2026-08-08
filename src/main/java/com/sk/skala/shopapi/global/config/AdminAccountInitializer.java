package com.sk.skala.shopapi.global.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.sk.skala.shopapi.customer.domain.Customer;
import com.sk.skala.shopapi.customer.domain.CustomerRepository;
import com.sk.skala.shopapi.customer.domain.Role;
import com.sk.skala.shopapi.global.common.Money;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 계정 초기화.
 *
 * <p>관리자는 회원가입 API로 만들 수 없다. 만들 수 있게 하면 누구나 관리자가 된다.
 * 그래서 설정으로만 지정하고 기동 시 없으면 생성한다.
 *
 * <p>마이그레이션(`INSERT`)으로 넣지 않은 이유는 <b>비밀번호 해시 때문</b>이다.
 * SQL에 해시를 박으면 저장소에 그대로 올라가고, 환경마다 다른 비밀번호를 쓸 수도 없다.
 * 해싱은 실행 시점에 해야 한다.
 *
 * <p>이미 있으면 아무것도 하지 않는다. 덮어쓰면 운영 중 비밀번호를 바꿔도
 * 재시작할 때마다 설정값으로 되돌아간다.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AdminAccountInitializer {

    private final AdminProperties adminProperties;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public ApplicationRunner createAdminAccount() {
        return args -> {
            if (!adminProperties.isConfigured()) {
                log.info("관리자 계정 설정이 없어 생성하지 않습니다. "
                        + "필요하면 SHOP_ADMIN_ID와 SHOP_ADMIN_PASSWORD를 설정하세요.");
                return;
            }

            // 로그에 관리자 아이디를 남기지 않는다.
            //
            // 로그는 수집 시스템으로 흘러가 애플리케이션보다 접근 범위가 넓다.
            // 관리자 아이디가 노출되면 공격자가 대상 계정을 특정할 수 있다.
            // 생성 여부만 알면 운영에 충분하다.
            if (customerRepository.existsById(adminProperties.id())) {
                log.info("관리자 계정이 이미 있습니다.");
                return;
            }

            try {
                customerRepository.save(new Customer(
                        adminProperties.id(),
                        passwordEncoder.encode(adminProperties.password()),
                        Money.ZERO,     // 관리자는 주문하지 않으므로 포인트가 필요 없다
                        Role.ADMIN));

                log.info("관리자 계정을 생성했습니다.");

            } catch (DataIntegrityViolationException e) {
                // 위의 존재 확인과 여기 저장 사이에 다른 인스턴스가 먼저 만든 경우다.
                // 인스턴스를 두 개 이상 띄우면 실제로 일어난다. (K8s replicas: 2)
                //
                // ApplicationRunner에서 예외가 나가면 그 인스턴스는 기동에 실패한다.
                // 원하던 상태(관리자 계정이 존재함)는 이미 이뤄졌으므로 실패시킬 이유가 없다.
                log.info("관리자 계정이 이미 있습니다. 다른 인스턴스가 먼저 생성했습니다.");
            }
        };
    }
}
