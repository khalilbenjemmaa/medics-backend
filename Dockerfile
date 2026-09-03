# ---------- build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Dependencies first: this layer is cached unless the pom changes, so
# an ordinary source edit does not re-download the world.
COPY pom.xml .
COPY .mvn/ .mvn/
RUN mvn -B dependency:go-offline

COPY src/ src/
RUN mvn -B -DskipTests package

# ---------- run ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# An unprivileged user: a process that never needs to write to its own
# image should not be able to.
RUN addgroup -S practice && adduser -S practice -G practice
USER practice

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080

# Container-aware heap sizing, and a fast entropy source so SecureRandom
# does not block on a container with a shallow entropy pool.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
