package com.lecture.enrollment.repository;

import com.lecture.enrollment.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findByUserId(Long userId);

    List<Enrollment> findByUserIdAndStatus(Long userId, Enrollment.Status status);

    List<Enrollment> findByProjectIdAndStatus(Long projectId, Enrollment.Status status);

    Optional<Enrollment> findByUserIdAndProjectId(Long userId, Long projectId);

    boolean existsByUserIdAndProjectId(Long userId, Long projectId);

    long countByProjectIdAndStatus(Long projectId, Enrollment.Status status);
}
