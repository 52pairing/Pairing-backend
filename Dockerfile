# Java 17 실행 환경을 가져옵니다.
FROM eclipse-temurin:17-jre-alpine

# 컨테이너 내 작업 디렉토리 설정
WORKDIR /app

# CI에서 빌드해둔 app.jar 파일을 컨테이너 안으로 가져옵니다.
COPY app.jar app.jar

# 스프링부트 포트 노출
EXPOSE 8080

# 컨테이너가 켜질 때 스프링부트를 실행하는 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]
