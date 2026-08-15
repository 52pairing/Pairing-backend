package com.pairing.project.application.service;

import com.pairing.notification.application.command.CreateNotificationCommand;
import com.pairing.notification.application.usecase.NotificationCreateUseCase;
import com.pairing.notification.domain.model.NotificationType;
import com.pairing.project.application.event.ProjectCanceledEvent;
import com.pairing.project.application.port.ClientProfileReaderPort;
import com.pairing.project.domain.model.Project;
import com.pairing.project.domain.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 프로젝트가 취소되면 클라이언트에게 통지한다. (모집 기간 만료 P46)
 *
 * <p><b>여기서는 클라이언트만 맡는다.</b> 프로젝트 도메인이 아는 대상은 자기 발주자뿐이다
 * ({@code project.client_id}). 취소로 요청·협상이 끊긴 <b>프리랜서</b> 통지는 그들을 아는 도메인이
 * 각자 보낸다 — 협상은 결렬 알림으로, 매칭은 만료 처리에서. 프로젝트가 매칭 데이터를 되읽어
 * 프리랜서를 찾는 순환 조회를 피한다.
 *
 * <p><b>사유를 가리지 않는다.</b> {@link ProjectCanceledEvent} 는 만료 스케줄러와 클라이언트의
 * 모집 종료 양쪽에서 발행되는데, 이벤트에 사유 필드가 없다(수신자가 사유로 분기하지 않도록 한 설계).
 * 그래서 문구를 중립으로 둔다. 클라이언트가 직접 종료한 경우에도 같은 통지가 가지만, "취소됐다"는
 * 사실은 어느 경로든 맞고 확인 알림 역할을 한다.
 *
 * <p><b>독립 트랜잭션 + 실패를 삼킨다.</b> 알림은 부수 효과다. 여기서 터져도 이미 커밋된
 * 프로젝트 취소를 되돌리면 안 된다({@code AFTER_COMMIT + REQUIRES_NEW}). 계약·정산 정리와 같은
 * 이벤트를 받지만 서로 독립이라 순서에 기대지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCancelNotifier {

    private static final String TITLE = "프로젝트가 취소되었습니다";
    // 알림은 발주자(클라이언트)에게 간다. 클라이언트 프로젝트 상세 라우트에 맞춘다
    // (프론트 /client/projects/[projectId]). 프리랜서 경로가 아니다.
    private static final String LINK_PREFIX = "/client/projects/";

    private final ProjectRepository projectRepository;
    private final ClientProfileReaderPort clientProfileReaderPort;
    private final NotificationCreateUseCase notificationCreateUseCase;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void on(ProjectCanceledEvent event) {
        try {
            notifyClient(event.projectId());
        } catch (Exception e) {
            log.warn("프로젝트 취소 알림 실패. 취소 자체는 이미 처리됐다. projectId={}", event.projectId(), e);
        }
    }

    private void notifyClient(Long projectId) {
        Project project = projectRepository.findById(projectId).orElse(null);
        if (project == null) {
            return;
        }

        Long clientAccountId = clientProfileReaderPort.findAccountId(project.getClientId());
        if (clientAccountId == null) {
            log.warn("취소 알림: 클라이언트 계정을 찾지 못했다. projectId={}", projectId);
            return;
        }

        notificationCreateUseCase.create(new CreateNotificationCommand(
                clientAccountId,
                NotificationType.PROJECT_CANCELED,
                TITLE,
                "'%s' 프로젝트가 취소되었습니다. 진행 중이던 계약과 정산은 함께 정리되었습니다."
                        .formatted(project.getTitle()),
                LINK_PREFIX + projectId));
    }
}
