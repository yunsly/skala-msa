package com.lecture.course.repository;

import com.lecture.course.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByName(String name);

    List<Project> findByStatus(Project.Status status);

    List<Project> findByOwnerIdAndStatus(Long ownerId, Project.Status status);
}
