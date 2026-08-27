package com.lecture.course.repository;

import com.lecture.course.entity.Course;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CourseRepository extends JpaRepository<Course, Long> {

    List<Course> findByStatus(Course.Status status);

    List<Course> findByProjectIdAndStatus(Long projectId, Course.Status status);

    List<Course> findByCategoryAndStatus(Course.Category category, Course.Status status);

    List<Course> findByProjectIdAndCategoryAndStatus(
            Long projectId,
            Course.Category category,
            Course.Status status
    );

    boolean existsByProjectIdAndTitle(Long projectId, String title);

    boolean existsByProjectIdAndTitleAndIdNot(Long projectId, String title, Long id);
}
