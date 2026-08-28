#!/usr/bin/env bash
# KeyNexus 마이크로서비스 이미지 재빌드.
#
# docker-compose.yml 은 프리빌트 이미지(image:)를 참조하므로, 소스를 바꾸면
# 이 스크립트로 이미지를 다시 만들어야 한다.
#
# 방식: 호스트에서 Gradle bootJar → docker/Dockerfile.jvm-runtime 으로 jar 만 패키징.
#       (컨테이너 안 Gradle 빌드가 Maven Central 429 에 걸리는 것을 피한다.)
#
# 요구: JDK 21, Docker.  사용:  ./scripts/build-images.sh [service ...]
set -euo pipefail
cd "$(dirname "$0")/.."

JVM_SERVICES=(course-service enrollment-service payment-service)   # Spring Boot
PY_SERVICES=(recommend-service)                                    # FastAPI (자체 Dockerfile)

targets=("$@")
[ ${#targets[@]} -eq 0 ] && targets=("${JVM_SERVICES[@]}" "${PY_SERVICES[@]}")

for svc in "${targets[@]}"; do
  img="msa-lecture-${svc}:latest"
  if printf '%s\n' "${PY_SERVICES[@]}" | grep -qx "$svc"; then
    echo "== $svc (Python) =="
    docker build -t "$img" "./$svc"
  else
    echo "== $svc (bootJar on host) =="
    ( cd "$svc" && sh ./gradlew bootJar -x test --console=plain -q )
    docker build -f docker/Dockerfile.jvm-runtime -t "$img" "./$svc"
  fi
done

echo
echo "완료. 반영:  docker compose up -d ${targets[*]}"
