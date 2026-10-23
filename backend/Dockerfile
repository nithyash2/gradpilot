# ---- Build ------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Download dependencies first so they are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Run --------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --create-home gradpilot
USER gradpilot

COPY --from=build /app/target/gradpilot-backend-*.jar app.jar

# Fit comfortably in a 512 MB free-tier container
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
