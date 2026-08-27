package com.lecture.payment.repository;

import com.lecture.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserId(Long userId);

    List<Payment> findByEnrollmentIdOrderByCreatedAtDesc(Long enrollmentId);

    List<Payment> findByProjectIdAndStatus(Long projectId, Payment.Status status);

    Optional<Payment> findByTransactionId(String transactionId);
}
