package com.lecture.enrollment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

    @Test
    void cancelledMembershipCanReapply() {
        Enrollment enrollment = Enrollment.builder()
                .userId(1L)
                .projectId(10L)
                .status(Enrollment.Status.CANCELLED)
                .reason("기존 요청")
                .build();

        enrollment.reapply("재참여");

        assertThat(enrollment.getStatus()).isEqualTo(Enrollment.Status.PENDING);
        assertThat(enrollment.getReason()).isEqualTo("재참여");
    }

    @Test
    void activeMembershipCannotReapply() {
        Enrollment enrollment = Enrollment.builder()
                .userId(1L)
                .projectId(10L)
                .status(Enrollment.Status.ACTIVE)
                .build();

        assertThatThrownBy(() -> enrollment.reapply("중복 신청"))
                .isInstanceOf(IllegalStateException.class);
    }
}
