package com.pairing.account.domain.model;

import com.pairing.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 주소 값 객체.
 *
 * <p>한 줄로 합치는 규칙이 핵심이다. 이 값이 그대로 계약서 갑 주소와 프로젝트 근무지로 나간다.
 *
 * <p>테스트 데이터는 주소검색 위젯(다음·카카오 우편번호)이 실제로 주는 모양을 쓴다 —
 * {@code roadAddress} 에 시·도·시·군·구가 이미 포함되어 있다.
 */
class AddressTest {

    @Test
    @DisplayName("도로명 주소와 상세주소만 잇는다")
    void joinsRoadAddressAndDetail() {
        Address address = Address.of("서울", "강남구", "서울 강남구 테헤란로 123", "10층 1002호", "06234");

        assertThat(address.toSingleLine()).isEqualTo("서울 강남구 테헤란로 123 10층 1002호");
    }

    @Test
    @DisplayName("시·도·시·군·구를 다시 붙이지 않는다")
    void doesNotRepeatSidoAndSigungu() {
        // roadAddress 에 이미 들어 있어서, 함께 이으면
        // "서울 강남구 서울 강남구 테헤란로 123" 이 되고 그대로 계약서에 찍힌다.
        Address address = Address.of("서울", "강남구", "서울 강남구 테헤란로 123", null, "06234");

        assertThat(address.toSingleLine()).isEqualTo("서울 강남구 테헤란로 123");
    }

    @Test
    @DisplayName("우편번호는 한 줄 주소에 넣지 않는다")
    void zipCodeIsNotPartOfSingleLine() {
        Address address = Address.of("서울", "강남구", "서울 강남구 테헤란로 123", null, "06234");

        assertThat(address.toSingleLine()).doesNotContain("06234");
    }

    @Test
    @DisplayName("시·군·구가 없는 세종시도 그대로 합쳐진다")
    void joinsWithoutSigungu() {
        Address address = Address.of("세종특별자치시", "", "세종특별자치시 한누리대로 2130", "3층", "30151");

        assertThat(address.sigungu()).isNull();
        assertThat(address.toSingleLine()).isEqualTo("세종특별자치시 한누리대로 2130 3층");
    }

    @Test
    @DisplayName("상세주소를 안 쓰면 도로명 주소만 남는다")
    void joinsWithoutAddressDetail() {
        Address address = Address.of("부산", "해운대구", "부산 해운대구 센텀중앙로 79", "  ", null);

        assertThat(address.addressDetail()).isNull();
        assertThat(address.toSingleLine()).isEqualTo("부산 해운대구 센텀중앙로 79");
    }

    @Test
    @DisplayName("앞뒤 공백은 떼고 담는다")
    void trimsEachPart() {
        Address address = Address.of("  서울 ", " 강남구", " 서울 강남구 테헤란로 123 ", " 2층 ", " 06234 ");

        assertThat(address.sido()).isEqualTo("서울");
        assertThat(address.roadAddress()).isEqualTo("서울 강남구 테헤란로 123");
        assertThat(address.toSingleLine()).isEqualTo("서울 강남구 테헤란로 123 2층");
    }

    @Test
    @DisplayName("시·도나 도로명이 비면 만들 수 없다")
    void requiresSidoAndRoadAddress() {
        assertThatThrownBy(() -> Address.of(null, "강남구", "서울 강남구 테헤란로 123", null, null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> Address.of("서울", "강남구", "   ", null, null))
                .isInstanceOf(BusinessException.class);
    }
}
