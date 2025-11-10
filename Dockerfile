FROM eclipse-temurin:25.0.1_8-jre

WORKDIR /app

# Copy the pre-built JAR from your CI workspace
COPY build/libs/certalert*.jar certalert.jar

# Create runtime dirs (for mounted secrets, configs, etc.)
RUN addgroup --system certalert \
 && adduser --system certalert --ingroup certalert \
 && mkdir -p /config /passwords /certs \
 && chown certalert:certalert /config /passwords /certs

USER certalert

EXPOSE 8080

ENTRYPOINT ["java","-jar","certalert.jar"]