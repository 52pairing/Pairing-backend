package com.pairing.contract.infrastructure.persistence;

import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.contract.domain.model.SignatureStatus;

/**
 * (계약 상태, 내 서명 상태) 조합별 건수. 탭 배지를 한 번의 쿼리로 만들기 위한 중간 형태다.
 *
 * <p>탭마다 세면 탭 수만큼 쿼리가 나간다. 탭이 7개라 배지 하나 그리는 데 7번이다.
 * 두 축으로 묶어서 한 번에 받고, 접는 것은 애플리케이션이 한다.
 *
 * <p>두 축인 이유는 "서명 대기"와 "상대방 서명 대기"가 <b>계약 상태가 둘 다
 * {@code SIGN_PENDING}</b> 이라서다. 계약 상태만으로 세면 두 탭이 같은 숫자가 된다.
 */
public record ContractStatusCountRow(ContractStatus status,
                                     SignatureStatus mySignatureStatus,
                                     Long count) {
}
