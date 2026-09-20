# syntax=docker/dockerfile:1.7
# Ice-Link 백엔드 이미지. GitHub Actions 가 빌드해 Docker Hub 에 올리고, 서버는 pull 만 한다.
#
#   빌드 스테이지: JDK 25 + Gradle Wrapper 로 bootJar (테스트는 워크플로의 test 잡에서 별도 실행)
#   실행 스테이지: JRE 25 만 포함한 가벼운 이미지

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# 의존성 레이어 캐시: 빌드 스크립트만 먼저 복사
COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew \
    && ./gradlew --no-daemon -q dependencies > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon -q bootJar -x test \
    && cp build/libs/*-SNAPSHOT.jar /app/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

# 컨테이너 메모리 한도의 75% 까지 힙 사용. 컨테이너 시간대는 UTC (앱·DB 모두 UTC 기준)
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=UTC" \
    SERVER_PORT=8080

COPY --from=build /app/app.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
