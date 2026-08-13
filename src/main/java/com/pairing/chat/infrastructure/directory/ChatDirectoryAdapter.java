package com.pairing.chat.infrastructure.directory;

import com.pairing.chat.application.port.out.ChatDirectoryPort;
import com.pairing.file.application.usecase.FileQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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

    /** 파일이 없거나 지워졌으면 null. 사진은 선택이라 흔한 경우이므로 예외로 다루지 않는다. */
    private String imageKey(Long fileId) {
        return fileQueryUseCase.findObjectKey(fileId).orElse(null);
    }
}
