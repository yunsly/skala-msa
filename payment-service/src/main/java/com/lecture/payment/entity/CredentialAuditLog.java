package com.lecture.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "credential_audit_logs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_audit_event_id",
                columnNames = "event_id"
        ),
        indexes = {
                @Index(name = "idx_audit_project_created", columnList = "project_id, created_at"),
                @Index(name = "idx_audit_course_created", columnList = "course_id, created_at"),
                @Index(name = "idx_audit_user_created", columnList = "user_id, created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class CredentialAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Result result;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(columnDefinition = "TEXT")
    private String detail;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Action {
        API_KEY_CREATED,
        API_KEY_VIEWED,
        API_KEY_UPDATED,
        API_KEY_ROTATED,
        API_KEY_REVOKED,
        CREDENTIAL_VIEWED,
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_UPDATED,
        PROJECT_ACCESS_REQUESTED,
        PROJECT_ACCESS_ACTIVATED,
        PROJECT_ACCESS_CANCELLED,
        PROJECT_ACCESS_REAPPLIED,
        PROJECT_ACCESS_APPROVED,
        PROJECT_ACCESS_REJECTED,
        PROJECT_ACCESS_REVOKED
    }

    public enum Result {
        SUCCESS,
        FAILURE,
        DENIED
    }
}
