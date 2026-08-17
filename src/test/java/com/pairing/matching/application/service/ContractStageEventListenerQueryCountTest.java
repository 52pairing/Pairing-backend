package com.pairing.matching.application.service;

import com.pairing.matching.domain.model.MatchingRequest;
import com.pairing.matching.domain.repository.MatchingRequestRepository;
import com.pairing.project.application.event.ProjectCanceledEvent;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * "프로젝트 취소 시 응답 대기 요청 일괄 종결" 루프({@code ContractStageEventListener.onProjectCanceled})가
 * {@code save()}를 건마다 부르는데, 도메인 객체 -&gt; 새 JPA 엔티티 -&gt; merge() 경로라 건마다
 * SELECT 가 하나씩 더 붙는지(merge 가 이미 관리 중인 엔티티를 못 찾아서) 확인한다.
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class ContractStageEventListenerQueryCountTest {

    private static final Long PROJECT_ID = 7_401L;
    private static final Long POSITION_ID = 7_401L;

    @Autowired
    private ContractStageEventListener listener;
    @Autowired
    private MatchingRequestRepository matchingRequestRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM matching_request WHERE project_id = ?", PROJECT_ID);
        for (int i = 0; i < 6; i++) {
            matchingRequestRepository.save(MatchingRequest.create(PROJECT_ID, POSITION_ID,
                    7_401_000L + i, 7_401_000L + i));
        }
    }

    @Test
    @Transactional
    void printsQueryCountForProjectCanceledBulkExpire() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        listener.onProjectCanceled(new ProjectCanceledEvent(PROJECT_ID));

        long queryCount = statistics.getPrepareStatementCount();
        System.out.println("=====CANCEL_QUERY_COUNT===== 요청 6건 일괄 만료 처리 -> "
                + queryCount + " prepared statements =====CANCEL_QUERY_COUNT=====");
    }
}
