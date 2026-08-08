package com.sk.skala.shopapi.global.config;

import org.springframework.boot.ApplicationRunner;
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

            if (customerRepository.existsById(adminProperties.id())) {
                log.info("관리자 계정이 이미 있습니다: {}", adminProperties.id());
                return;
            }

            customerRepository.save(new Customer(
                    adminProperties.id(),
                    passwordEncoder.encode(adminProperties.password()),
                    Money.ZERO,     // 관리자는 주문하지 않으므로 포인트가 필요 없다
                    Role.ADMIN));

            log.info("관리자 계정을 생성했습니다: {}", adminProperties.id());
        };
    }
}
