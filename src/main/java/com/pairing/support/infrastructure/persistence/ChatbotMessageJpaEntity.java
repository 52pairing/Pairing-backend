package com.pairing.support.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chatbot_message")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatbotMessageJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "question", nullable = false, length = 500)
    private String question;

    @Column(name = "answer", nullable = false, length = 2000)
    private String answer;

    /**
     * 답변에 딸린 이동 버튼의 화면 코드.
     *
     * <p>{@code @Enumerated} 대신 <b>문자열로</b> 둔다. enum 으로 매핑하면 Hibernate 가 CHECK 제약을
     * 만드는데, 그 제약은 {@code ddl-auto: update} 로 갱신되지 않는다. 나중에 화면을 하나 추가하는
     * 순간 운영에서 INSERT 가 막힌다. 읽을 때 {@code ChatbotIntent.from} 이 모르는 값을 NONE 으로 떨어뜨린다.
     */
    @Column(name = "intent", length = 30)
    private String intent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ChatbotMessageJpaEntity(Long id, Long sessionId, String question, String answer, String intent,
                                   LocalDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.question = question;
        this.answer = answer;
        this.intent = intent;
        this.createdAt = createdAt;
    }
}
