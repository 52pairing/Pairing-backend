package com.pairing.negotiation.application.usecase;

import com.pairing.negotiation.application.result.admin.AdminDetail;
import com.pairing.negotiation.application.result.admin.AdminListItem;
import com.pairing.negotiation.application.result.admin.AdminRawLog;
import com.pairing.negotiation.application.result.admin.AdminSummary;
import com.pairing.negotiation.domain.model.ConditionType;
import com.pairing.negotiation.domain.model.NegotiationMessage;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

/**
 * 관리자 협상 조회 인바운드 포트. 경로 {@code /api/v1/negotiations/admin/**} 이라
 * 시큐리티에서 ROLE_ADMIN 으로 막힌다(별도 권한 검사 불필요). 당사자(뷰어) 개념이 없다.
 */
public interface NegotiationAdminQueryUseCase {

    /** 상태별 집계 + 평균 라운드/소요일. */
    AdminSummary getSummary();

    /** 전체 협상 검색(프로젝트명·클라이언트·프리랜서 키워드 + 상태 필터, DB 페이징). */
    Page<AdminListItem> search(String keyword, NegotiationStatus status, Pageable pageable);

    /** 협상 상세(기본 + 최종 결과 + 라운드별 로그). 없으면 NG_001. */
    AdminDetail getDetail(Long negotiationId);

    /** ai_agent_log 원본(시간순). 없거나 미기록이면 빈 리스트. */
    List<AdminRawLog> getRawLogs(Long negotiationId);

    /** 관리자용 전체 협상 로그(당사자 검증 없이) + 로그의 조건 타입 매핑(표시용). */
    AdminMessages getMessages(Long negotiationId);

    /** 협상 로그 + conditionId→타입 매핑. 로그 응답의 conditionType 을 채우는 데 쓴다. */
    record AdminMessages(List<NegotiationMessage> messages, Map<Long, ConditionType> conditionTypes) {
    }
}
