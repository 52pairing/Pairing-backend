package com.pairing.negotiation.application.port.out;

import com.pairing.negotiation.application.result.admin.AdminListItem;
import com.pairing.negotiation.application.result.admin.AdminRawLog;
import com.pairing.negotiation.application.result.admin.AdminSummary;
import com.pairing.negotiation.domain.model.NegotiationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 관리자 협상 조회용 읽기 포트(협상 소유). 집계·검색·원본 로그는 도메인(project·profile·account·
 * ai_agent_log)을 가로질러 조회하므로 읽기 전용으로 직접 조회한다.
 */
public interface NegotiationAdminReaderPort {

    AdminSummary loadSummary();

    Page<AdminListItem> search(String keyword, NegotiationStatus status, Pageable pageable);

    /** ai_agent_log 원본. 테이블 부재/오류 시 빈 리스트(AI 서비스가 기록, 백엔드는 읽기만). */
    List<AdminRawLog> loadRawLogs(Long negotiationId);
}
