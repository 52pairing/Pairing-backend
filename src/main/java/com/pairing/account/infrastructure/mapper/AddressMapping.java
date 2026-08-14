package com.pairing.account.infrastructure.mapper;

import com.pairing.account.domain.model.Address;
import com.pairing.account.infrastructure.persistence.AddressEmbeddable;

/**
 * 도메인 {@link Address} ↔ {@link AddressEmbeddable} 변환. 클라이언트·프리랜서 프로필 매퍼가 함께 쓴다.
 *
 * <p>다섯 칸이 모두 비면 <b>null 로 눕힌다.</b> 주소를 나눠 담기 전에 가입한 행은 컬럼이 전부 NULL 인데,
 * Hibernate 가 그때도 빈 임베더블 객체를 돌려줄 수 있다. 그대로 도메인에 올리면 "주소가 있다"고
 * 잘못 읽혀 수정 폼이 빈 칸 다섯 개를 채워진 값처럼 보여준다.
 */
final class AddressMapping {

    private AddressMapping() {
        throw new IllegalStateException("Utility class");
    }

    static Address toDomain(AddressEmbeddable embeddable) {
        if (embeddable == null || isEmpty(embeddable)) {
            return null;
        }
        return new Address(embeddable.getSido(), embeddable.getSigungu(), embeddable.getRoadAddress(),
                embeddable.getAddressDetail(), embeddable.getZipCode());
    }

    static AddressEmbeddable toEmbeddable(Address address) {
        if (address == null) {
            return null;
        }
        return new AddressEmbeddable(address.sido(), address.sigungu(), address.roadAddress(),
                address.addressDetail(), address.zipCode());
    }

    private static boolean isEmpty(AddressEmbeddable embeddable) {
        return isBlank(embeddable.getSido())
                && isBlank(embeddable.getSigungu())
                && isBlank(embeddable.getRoadAddress())
                && isBlank(embeddable.getAddressDetail())
                && isBlank(embeddable.getZipCode());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
