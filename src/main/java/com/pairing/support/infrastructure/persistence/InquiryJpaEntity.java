package com.pairing.support.infrastructure.persistence;

import com.pairing.account.domain.model.Role;
import com.pairing.support.domain.model.InquiryStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "writer_account_id", nullable = false)
    private Long writerAccountId;

    /** 작성 시점 계정 정보 스냅샷. 이름·역할·이메일은 가입 후 바뀌지 않아 항상 최신값과 같다. */
    @Column(name = "writer_name", nullable = false, length = 100)
    private String writerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "writer_role", nullable = false, length = 20)
    private Role writerRole;

    @Column(name = "writer_email", length = 100)
    private String writerEmail;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, length = 2000)
    private String content;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "inquiry_file", joinColumns = @JoinColumn(name = "inquiry_id"))
    @Column(name = "file_id")
    @OrderColumn(name = "sort_order")
    private List<Long> fileIds = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private InquiryStatus status;

    @Column(name = "answer", length = 2000)
    private String answer;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public InquiryJpaEntity(Long id, Long writerAccountId, String writerName, Role writerRole, String writerEmail,
                            String title, String content, List<Long> fileIds, InquiryStatus status, String answer,
                            LocalDateTime answeredAt, LocalDateTime createdAt) {
        this.id = id;
        this.writerAccountId = writerAccountId;
        this.writerName = writerName;
        this.writerRole = writerRole;
        this.writerEmail = writerEmail;
        this.title = title;
        this.content = content;
        this.fileIds = fileIds == null ? new ArrayList<>() : new ArrayList<>(fileIds);
        this.status = status;
        this.answer = answer;
        this.answeredAt = answeredAt;
        this.createdAt = createdAt;
    }
}
