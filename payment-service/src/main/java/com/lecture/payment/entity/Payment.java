package com.lecture.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payments",
        indexes = {
                @Index(
                        name = "idx_payments_enrollment_created",
                        columnList = "enrollment_id, created_at"
                ),
                @Index(name = "idx_payments_project_status", columnList = "project_id, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "enrollment_id", nullable = false)
    private Long enrollmentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "approved_by")
    private Long approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "transaction_id", unique = true)
    private String transactionId;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public void approve(Long approverId, String transactionId, String reason) {
        requirePending();
        this.approvedBy = approverId;
        this.transactionId = transactionId;
        this.decisionReason = reason;
        this.status = Status.COMPLETED;
    }

    public void reject(Long approverId, String reason) {
        requirePending();
        this.approvedBy = approverId;
        this.decisionReason = reason;
        this.status = Status.FAILED;
    }

    public void revoke(Long approverId, String reason) {
        if (status != Status.COMPLETED) {
            throw new IllegalStateException("COMPLETED 승인만 회수할 수 있습니다.");
        }
        this.approvedBy = approverId;
        this.decisionReason = reason;
        this.status = Status.CANCELLED;
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("PENDING 승인 티켓만 처리할 수 있습니다.");
        }
    }
}
