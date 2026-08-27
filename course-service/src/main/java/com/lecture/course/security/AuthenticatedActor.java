package com.lecture.course.security;

public record AuthenticatedActor(Long userId, Role role) {

    public enum Role {
        ADMIN,
        LEADER,
        MEMBER
    }
}
