package com.pairing.account.domain.model;

import com.pairing.account.exception.AccountErrorCode;
import com.pairing.global.exception.BusinessException;

import java.util.stream.Stream;

/**
 * 주소. 시·도 / 시·군·구 / 도로명 / 상세주소 / 우편번호로 나눠 들고 있다.
 *
 * <p><b>왜 나누는가.</b> 한 문자열로 합쳐 저장하면 수정 화면에서 다시 나눌 수 없다.
 * ({@code resume} 테이블이 같은 이유로 이미 세 칸으로 나눠 저장하고 있다)
 *
 * <p><b>값은 주소검색 위젯(다음·카카오 우편번호)이 준 조각을 그대로 담는다.</b> 서버는 시·군·구 코드표를
 * 들고 있지 않다. 행정구역은 개편되는데 표를 직접 관리하면 그때마다 마이그레이션이 필요하고, 도로명은
 * 어차피 자유 입력이라 절반만 정규화된다. 서버는 형식(길이·필수)만 본다.
 *
 * <p><b>{@code roadAddress} 에는 시·도·시·군·구가 포함된 전체 도로명주소가 들어온다.</b>
 * ("서울 강남구 테헤란로 152") 위젯의 {@code roadAddress} 가 원래 그런 값이고, 거기서 앞부분만
 * 잘라낸 값을 주는 필드가 없기 때문이다. ({@code roadname} 은 "테헤란로" 로 건물번호가 빠진다)
 * 프론트가 문자열을 잘라 보내게 하면 세종시처럼 시·군·구가 빈 경우에 규칙이 지저분해진다.
 *
 * <p>그래서 {@code sido}/{@code sigungu} 는 <b>지역 단위로 묶어 보기 위한 값</b>이고, 화면에 찍는
 * 주소는 {@code roadAddress} 가 담당한다. 셋을 이어붙이면 시·도·시·군·구가 두 번 나온다.
 * ({@link #toSingleLine()} 참고)
 *
 * <p><b>{@code sigungu} 는 선택이다.</b> 세종특별자치시는 시·군·구가 없어 위젯이 빈 값을 준다.
 * 필수로 두면 세종시 사용자가 가입할 수 없다.
 */
public record Address(
        String sido,
        String sigungu,
        String roadAddress,
        String addressDetail,
        String zipCode
) {

    /**
     * 빈 문자열은 null 로 눕혀서 만든다. 위젯이 값 없는 칸을 {@code ""} 로 주는 경우가 있어
     * 그대로 담으면 {@code isBlank()} 검사와 {@code null} 검사가 갈린다.
     *
     * @throws BusinessException 시·도나 도로명이 비었으면 {@code AC_001}
     */
    public static Address of(String sido, String sigungu, String roadAddress, String addressDetail,
                             String zipCode) {
        String normalizedSido = blankToNull(sido);
        String normalizedRoad = blankToNull(roadAddress);

        if (normalizedSido == null || normalizedRoad == null) {
            throw new BusinessException(AccountErrorCode.INVALID_ACCOUNT_FIELD);
        }
        return new Address(normalizedSido, blankToNull(sigungu), normalizedRoad,
                blankToNull(addressDetail), blankToNull(zipCode));
    }

    /**
     * 한 줄로 합친 주소. 계약서 갑 표시, 프로젝트 근무지처럼 <b>한 줄만 필요한 소비처</b>가 쓴다.
     *
     * <p><b>{@code sido}/{@code sigungu} 는 넣지 않는다.</b> {@code roadAddress} 에 이미 들어 있어서
     * 함께 이으면 "서울 강남구 서울 강남구 테헤란로 152" 가 된다. 그 값이 그대로 계약서 갑 주소로 찍힌다.
     *
     * <p>이 값은 {@code client_profile.address} / {@code freelancer_profile.address} 컬럼에 그대로
     * 저장한다. 파생값을 저장하는 이유는 두 가지다 — 이 기능 이전에 저장된 행에 이미 그 컬럼만 있어서
     * 읽는 쪽을 안 고쳐도 되고, 조회할 때마다 합치지 않아도 된다. 항상 도메인이 함께 갱신하므로
     * 두 값이 어긋나지 않는다.
     */
    public String toSingleLine() {
        return Stream.of(roadAddress, addressDetail)
                .filter(part -> part != null && !part.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
