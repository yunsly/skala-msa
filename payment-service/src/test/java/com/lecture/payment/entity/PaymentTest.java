package com.lecture.payment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void approvalMovesPendingTicketToCompleted() {
        Payment payment = Payment.builder()
                .enrollmentId(100L)
                .userId(1L)
                .projectId(10L)
                .build();

        payment.approve(2L, "ticket-uuid", "참여 확인");

        assertThat(payment.getStatus()).isEqualTo(Payment.Status.COMPLETED);
        assertThat(payment.getApprovedBy()).isEqualTo(2L);
        assertThat(payment.getTransactionId()).isEqualTo("ticket-uuid");
    }

    @Test
    void completedTicketCannotBeApprovedTwice() {
        Payment payment = Payment.builder()
                .enrollmentId(100L)
                .userId(1L)
                .projectId(10L)
                .build();
        payment.approve(2L, "ticket-uuid", "참여 확인");

        assertThatThrownBy(() -> payment.approve(2L, "other-ticket", "중복 승인"))
                .isInstanceOf(IllegalStateException.class);
    }
}
