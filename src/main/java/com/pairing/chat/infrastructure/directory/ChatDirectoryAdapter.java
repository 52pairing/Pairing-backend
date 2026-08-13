package com.pairing.chat.infrastructure.directory;

import com.pairing.chat.application.port.out.ChatDirectoryPort;
import com.pairing.contract.domain.model.ContractStatus;
import com.pairing.file.application.usecase.FileQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 채팅에 필요한 타 도메인 값을 읽기 전용으로 조회한다(협상/계정 도메인이 프로필 ID 기준 포트를 주지 않아,
 * 협상의 project·이름 조회와 같은 방식으로 직접 조회).
 *
 * <p><b>프로필 사진만 예외로 {@link FileQueryUseCase} 를 거친다.</b> 나머지처럼 {@code file} 테이블을
 * 직접 조인할 수도 있지만, 오브젝트 키 컬럼명이 스키마 문서와 실제가 다른 이력이 있어
 * (문서 {@code storage_key} / 실제 {@code object_key}) 파일 도메인이 아는 것을 쓰는 편이 안전하다.
 * 다른 도메인({@code ClientQueryService} 등)도 같은 방식으로 프로필 사진을 붙인다.
 */
@Component
@RequiredArgsConstructor
public class ChatDirectoryAdapter implements ChatDirectoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final FileQueryUseCase fileQueryUseCase;

    @Override
    public Optional<NegotiationParties> findPartiesByNegotiationId(Long negotiationId) {
        if (negotiationId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT cp.account_id     AS client_account_id,
                       fp.account_id     AS freelancer_account_id,
                       p.title           AS project_title,
                       cp.company_name   AS client_name,
                       fa.name           AS freelancer_name,
                       cp.logo_file_id   AS client_file_id,
                       fp.profile_file_id AS freelancer_file_id
                FROM negotiation n
                JOIN freelancer_profile fp ON fp.id = n.freelancer_id
                JOIN project p             ON p.id = n.project_id
                JOIN client_profile cp     ON cp.id = p.client_id
                LEFT JOIN account fa       ON fa.id = fp.account_id
                WHERE n.id = ?
                """;
        try {
            NegotiationParties parties = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                            new NegotiationParties(
                                    rs.getObject("client_account_id", Long.class),
                                    rs.getObject("freelancer_account_id", Long.class),
                                    rs.getString("project_title"),
                                    rs.getString("client_name"),
                                    rs.getString("freelancer_name"),
                                    imageKey(rs.getObject("client_file_id", Long.class)),
                                    imageKey(rs.getObject("freelancer_file_id", Long.class))),
                    negotiationId);
            return Optional.ofNullable(parties);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * 계정 하나의 표시명·프로필 사진.
     *
     * <p>한 계정은 클라이언트이거나 프리랜서라 프로필이 한쪽에만 있다. 양쪽을 {@code LEFT JOIN} 해
     * {@code COALESCE} 로 있는 쪽을 고른다 — 역할을 먼저 판정하려면 조회가 한 번 더 필요하다.
     */
    @Override
    public Optional<DisplayProfile> findDisplayProfile(Long accountId) {
        if (accountId == null) {
            return Optional.empty();
        }
        String sql = """
                SELECT a.name AS name,
                       COALESCE(cp.logo_file_id, fp.profile_file_id) AS image_file_id
                FROM account a
                LEFT JOIN client_profile cp     ON cp.account_id = a.id
                LEFT JOIN freelancer_profile fp ON fp.account_id = a.id
                WHERE a.id = ?
                """;
        try {
            DisplayProfile profile = jdbcTemplate.queryForObject(sql, (rs, rowNum) ->
                            new DisplayProfile(
                                    rs.getString("name"),
                                    imageKey(rs.getObject("image_file_id", Long.class))),
                    accountId);
            return Optional.ofNullable(profile);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * 체결된 계약인데 방이 없는 협상. 조회 시점 복구용이라 <b>내 협상만</b> 본다.
     *
     * <p>당사자 판정은 {@code findPartiesByNegotiationId} 와 같은 경로다
     * (협상 → 프리랜서 프로필 / 프로젝트 → 클라이언트 프로필 → 계정). {@code contract} 의
     * {@code client_id}·{@code freelancer_id} 를 직접 쓰지 않는 이유는, 그 컬럼이 프로필 ID 인지
     * 계정 ID 인지 스키마 문서와 실제가 다른 이력이 있어서다 — 이미 검증된 조인을 재사용한다.
     */
    @Override
    public List<Long> findNegotiationIdsMissingRoom(Long accountId) {
        if (accountId == null) {
            return List.of();
        }
        String sql = """
                SELECT DISTINCT c.negotiation_id
                FROM contract c
                JOIN negotiation n          ON n.id = c.negotiation_id
                JOIN project p              ON p.id = n.project_id
                JOIN client_profile cp      ON cp.id = p.client_id
                JOIN freelancer_profile fp  ON fp.id = n.freelancer_id
                LEFT JOIN chat_room r       ON r.negotiation_id = c.negotiation_id
                WHERE r.id IS NULL
                  AND c.status IN (%s)
                  AND (cp.account_id = ? OR fp.account_id = ?)
                """.formatted(placeholders(CONCLUDED_STATUSES.size()));

        Object[] args = new Object[CONCLUDED_STATUSES.size() + 2];
        CONCLUDED_STATUSES.toArray(args);
        args[args.length - 2] = accountId;
        args[args.length - 1] = accountId;
        return jdbcTemplate.queryForList(sql, Long.class, args);
    }

    @Override
    public boolean isContractConcluded(Long negotiationId) {
        if (negotiationId == null) {
            return false;
        }
        String sql = """
                SELECT EXISTS (SELECT 1 FROM contract
                               WHERE negotiation_id = ? AND status IN (%s))
                """.formatted(placeholders(CONCLUDED_STATUSES.size()));

        Object[] args = new Object[CONCLUDED_STATUSES.size() + 1];
        args[0] = negotiationId;
        for (int i = 0; i < CONCLUDED_STATUSES.size(); i++) {
            args[i + 1] = CONCLUDED_STATUSES.get(i);
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, args));
    }

    /**
     * "체결됨"의 정의는 계약 도메인이 갖는다({@link ContractStatus#isConcluded()}).
     *
     * <p>상태 문자열을 여기 하드코딩하면 계약 쪽에 상태가 하나 늘어날 때 <b>조용히 어긋난다</b> —
     * 방이 안 열리는데 아무 로그도 남지 않는 형태로 드러나서 찾기가 어렵다.
     */
    private static final List<String> CONCLUDED_STATUSES = Arrays.stream(ContractStatus.values())
            .filter(ContractStatus::isConcluded)
            .map(Enum::name)
            .toList();

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    /** 파일이 없거나 지워졌으면 null. 사진은 선택이라 흔한 경우이므로 예외로 다루지 않는다. */
    private String imageKey(Long fileId) {
        return fileQueryUseCase.findObjectKey(fileId).orElse(null);
    }
}
