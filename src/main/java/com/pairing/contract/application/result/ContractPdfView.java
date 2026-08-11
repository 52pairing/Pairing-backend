package com.pairing.contract.application.result;

import com.pairing.contract.domain.model.ContractClause;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PDF 템플릿에 넣을 값. 템플릿이 도메인 객체를 직접 뒤지지 않도록 한 번 펴서 넘긴다.
 *
 * <p>템플릿에서 {@code contract.getSignatures()[0].getPartyRole().getLabel()} 처럼 파고들면
 * 도메인이 바뀔 때 화면이 조용히 깨진다. 컴파일이 잡아 주지 않기 때문이다.
 */
public record ContractPdfView(
        String contractNo,
        String createdAt,
        Party client,
        Party freelancer,
        String jobRoleLabel,
        String settlementAccount,
        List<ContractClause> clauses,
        List<Signature> signatures
) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    /** 당사자 표시 한 칸. 값이 없으면 템플릿이 '-' 로 대체한다. */
    public record Party(String companyName, String businessNo, String representative,
                        String address, String phone, String name) {
    }

    /**
     * 서명란 한 칸.
     *
     * @param imageUrl 서명 그림의 절대 주소. 안 그렸으면 null 이고 그때는 이름과 시각만 찍는다
     * @param signedAt 서명 시각. 아직이면 null 이고 "서명 대기" 로 표시된다
     */
    public record Signature(String roleLabel, String name, String imageUrl, String signedAt) {
    }

    /** Thymeleaf 컨텍스트 변수. 이름은 템플릿의 {@code ${...}} 와 1:1 이다. */
    public Map<String, Object> toModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("contractNo", contractNo);
        model.put("createdAt", createdAt);
        model.put("client", client);
        model.put("freelancer", freelancer);
        model.put("jobRoleLabel", jobRoleLabel);
        model.put("settlementAccount", settlementAccount);
        model.put("clauses", clauses);
        model.put("signatures", signatures);
        return model;
    }

    public static String format(LocalDateTime value) {
        return value == null ? null : value.format(DATE);
    }
}
