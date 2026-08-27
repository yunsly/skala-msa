package com.lecture.course.entity;

import com.lecture.course.persistence.SecretMetadataConverter;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "courses",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_courses_project_title",
                columnNames = {"project_id", "title"}
        ),
        indexes = {
                @Index(
                        name = "idx_courses_project_category_status",
                        columnList = "project_id, category, status"
                ),
                @Index(name = "idx_courses_expires_status", columnList = "expires_at, status"),
                @Index(name = "idx_courses_renewal_status", columnList = "renewal_at, status")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(name = "plan_name", length = 100)
    private String planName;

    @Column(name = "instructor_id", nullable = false)
    private Long instructorId;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "renewal_at")
    private LocalDateTime renewalAt;

    @Column(name = "last_rotated_at")
    private LocalDateTime lastRotatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    /**
     * 애플리케이션 메모리에서는 Secret 원문, DB에는 ENC:v1 형식의 AES-GCM 암호문으로 저장된다.
     */
    @Convert(converter = SecretMetadataConverter.class)
    @Column(columnDefinition = "LONGTEXT")
    private String metadata;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Category {
        API_KEY,
        SUBSCRIPTION_PLAN
    }

    public enum Status {
        ACTIVE,
        INACTIVE,
        EXPIRED,
        REVOKED
    }

    public void rotateSecret(String secretValue, LocalDateTime rotatedAt) {
        if (category != Category.API_KEY) {
            throw new IllegalStateException("API_KEY 유형만 Secret을 회전할 수 있습니다.");
        }
        this.metadata = secretValue;
        this.lastRotatedAt = rotatedAt;
        this.status = Status.ACTIVE;
    }

    public void revoke() {
        this.status = Status.REVOKED;
    }
}
