package com.sk.skala.shopapi.payment.app;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * 카드번호(PAN) 암호화. (D32)
 *
 * <p>카드번호를 평문으로 네트워크에 흘리지 않는다. HTTPS가 있어도 그것만 믿지 않는 이유는,
 * 프록시·로드밸런서·로그 수집기처럼 <b>구간마다 평문이 드러나는 지점</b>이 있기 때문이다.
 * 종단 간 암호화는 그 구간들을 건너뛴다.
 *
 * <h2>AES-GCM을 쓰는 이유</h2>
 *
 * <p>GCM은 암호화와 <b>무결성 검증을 함께</b> 한다. CBC 같은 모드는 암호문을 조금 바꿔도
 * 복호화가 되어버려서, 공격자가 금액이나 카드번호를 조작할 여지가 생긴다.
 * GCM은 한 비트만 바뀌어도 복호화 자체가 실패한다.
 *
 * <h2>IV는 매번 새로 만든다</h2>
 *
 * <p>같은 IV를 재사용하면 <b>GCM은 키가 통째로 노출된다.</b> 같은 카드번호를 두 번
 * 암호화했을 때 결과가 같아지는 것도 문제다. 어떤 사용자가 같은 카드를 쓰는지
 * 암호문만 보고 알 수 있게 된다.
 *
 * <p>IV는 비밀이 아니므로 암호문 앞에 그대로 붙여 보낸다. 복호화하는 쪽이 알아야 한다.
 *
 * <h2>이 구현의 한계</h2>
 *
 * <p>실제 결제망은 대칭키를 이렇게 공유하지 않는다. 카드사가 발급한 공개키로 암호화하거나
 * HSM에 키를 두고, 키 교체 절차까지 갖춘다. 여기서는 <b>암호화 흐름 자체를 보이는 것</b>이
 * 목적이라 대칭키 하나로 단순화했다.
 */
@Component
@RequiredArgsConstructor
public class PanCipher {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;      // GCM 권장 96비트
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final PaymentProperties properties;

    /** 암호화한 뒤 {@code IV || 암호문}을 Base64로 돌려준다. */
    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(payload);

        } catch (Exception e) {
            // 예외 메시지에 평문이 섞이지 않게 원인만 감싼다.
            throw new IllegalStateException("카드 정보를 암호화하지 못했습니다", e);
        }
    }

    /** 복호화. 카드사 쪽에서 부른다. */
    public String decrypt(String encoded) {
        try {
            byte[] payload = Base64.getDecoder().decode(encoded);

            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));

            byte[] decrypted = cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH);
            return new String(decrypted, StandardCharsets.UTF_8);

        } catch (Exception e) {
            // 변조됐거나 키가 다르다. 어느 쪽인지 알려주지 않는다.
            throw new IllegalStateException("카드 정보를 복호화하지 못했습니다", e);
        }
    }

    private SecretKey key() {
        byte[] raw = properties.encryptionKey().getBytes(StandardCharsets.UTF_8);
        // AES-256에 맞춰 32바이트로 자르거나 채운다. 설정 검증이 길이를 이미 보장한다.
        byte[] normalized = new byte[32];
        System.arraycopy(raw, 0, normalized, 0, Math.min(raw.length, 32));
        return new SecretKeySpec(normalized, "AES");
    }
}
