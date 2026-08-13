package com.pairing.matching.application.event;

/**
 * 매칭 알림을 보내야 한다는 신호. 실제 발송은 이 이벤트를 받아 <b>커밋 후 별도 트랜잭션</b>에서 한다.
 *
 * <p>왜 이벤트를 쓰는가: 알림 저장을 매칭과 같은 트랜잭션에서 하면 <b>알림 실패가 매칭을 롤백시킨다.</b>
 * {@code NotificationCreateUseCase.create()}는 {@code @Transactional}(REQUIRED)이라 호출한 쪽
 * 트랜잭션에 얹히는데, 그 안에서 예외가 나면 스프링이 공유 트랜잭션을 rollback-only로 표시한다.
 * 이 표시는 <b>예외를 잡기 전에</b> 찍히므로, 호출한 쪽에서 try-catch로 삼켜도 커밋 시점에
 * {@code UnexpectedRollbackException}이 나서 매칭 요청·수락이 통째로 사라진다.
 * (2026-08-13 알림 도메인 담당자 리포트로 확인. 당시 장애는 없었고 실패 경로만 문제였다.)
 *
 * <p>커밋 후로 미루면 되돌릴 것이 없고, 반대 방향도 안전하다 - 매칭이 실패하면 알림도 안 나간다.
 *
 * <p>문구·링크는 여전히 {@code MatchingNotifier} 한곳에서만 정한다. 이 이벤트는 "어떤 상황인지"와
 * 대상 요청 ID만 나른다. 상황별로 레코드를 쪼개지 않은 이유는, 쪼개면 리스너·발행부가 4배로 늘어나는데
 * 정작 분기는 문구를 고르는 한 곳뿐이어서다.
 *
 * @param kind 어떤 상황의 알림인지
 * @param requestId 대상 매칭 요청 ID. 리스너가 커밋 후 다시 조회한다(발행 시점 객체를 실어 보내면
 *                  즉시 타결처럼 뒤이어 상태가 바뀌는 경우 낡은 값으로 문구가 나간다)
 * @param projectTitle {@link Kind#REQUESTED}에만 쓰는 프로젝트명. 나머지 종류에서는 {@code null}이다
 */
public record MatchingNotificationRequested(Kind kind, Long requestId, String projectTitle) {

    public enum Kind {
        /** 클라이언트가 요청을 보냈다 -&gt; 프리랜서에게. */
        REQUESTED,
        /** 프리랜서가 수락했다 -&gt; 클라이언트에게. */
        ACCEPTED,
        /** 프리랜서가 직접 거절했다 -&gt; 클라이언트에게. */
        REJECTED,
        /** 응답 기한이 지나 자동 만료됐다 -&gt; 클라이언트에게. */
        EXPIRED
    }

    public static MatchingNotificationRequested requested(Long requestId, String projectTitle) {
        return new MatchingNotificationRequested(Kind.REQUESTED, requestId, projectTitle);
    }

    public static MatchingNotificationRequested accepted(Long requestId) {
        return new MatchingNotificationRequested(Kind.ACCEPTED, requestId, null);
    }

    public static MatchingNotificationRequested rejected(Long requestId) {
        return new MatchingNotificationRequested(Kind.REJECTED, requestId, null);
    }

    public static MatchingNotificationRequested expired(Long requestId) {
        return new MatchingNotificationRequested(Kind.EXPIRED, requestId, null);
    }
}
