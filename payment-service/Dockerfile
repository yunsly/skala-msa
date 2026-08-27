# syntax=docker/dockerfile:1

# 1단계: Gradle로 JAR 빌드
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

# 의존성 캐시 최적화 (소스 변경 시 재다운로드 방지)
COPY gradlew .
COPY gradle gradle
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew
# BuildKit 캐시 마운트로 ~/.gradle을 서비스 빌드 간 공유 — 마운트 없이는 매 빌드마다
# 전체 의존성을 처음부터 새로 받아서, 여러 서비스를 연달아 빌드하면 Maven Central에
# 짧은 시간에 대량 요청이 몰려 429(Too Many Requests)로 막히는 문제가 있었다.
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew dependencies --no-daemon

# 소스 복사 및 빌드
COPY src src
RUN --mount=type=cache,target=/root/.gradle,sharing=locked ./gradlew bootJar -x test --no-daemon

# 2단계: 실행 환경 (JRE만 포함 - 이미지 경량화)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]