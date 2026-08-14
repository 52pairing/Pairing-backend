package com.pairing.account.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주소 5칸의 컬럼 매핑. {@code client_profile} 과 {@code freelancer_profile} 이 같은 모양을 쓴다.
 *
 * <p>도메인의 {@code Address} 와 짝이다. 도메인에는 JPA 애노테이션을 두지 않는 규칙이라 따로 있다.
 *
 * <p>한 줄로 합친 주소는 여기 없다. 그 값은 각 엔티티의 기존 {@code address} 컬럼에 남는다 —
 * 계약서·프로젝트가 이미 그 컬럼을 읽고 있어서 옮기면 그쪽까지 고쳐야 한다.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AddressEmbeddable {

    @Column(name = "sido", length = 20)
    private String sido;

    /** 세종특별자치시는 시·군·구가 없어 비어 있을 수 있다. */
    @Column(name = "sigungu", length = 40)
    private String sigungu;

    @Column(name = "road_address", length = 255)
    private String roadAddress;

    @Column(name = "address_detail", length = 255)
    private String addressDetail;

    @Column(name = "zip_code", length = 10)
    private String zipCode;

    public AddressEmbeddable(String sido, String sigungu, String roadAddress, String addressDetail,
                             String zipCode) {
        this.sido = sido;
        this.sigungu = sigungu;
        this.roadAddress = roadAddress;
        this.addressDetail = addressDetail;
        this.zipCode = zipCode;
    }
}
