FROM eclipse-temurin:25.0.4_7-jre

WORKDIR /app

# Copy the pre-built JAR from your CI workspace
COPY build/libs/certalert*.jar certalert.jar

# Create runtime dirs (for mounted secrets, configs, etc.)
RUN addgroup --system --gid 10001 certalert \
 && adduser --system --uid 10001 --ingroup certalert certalert \
 && mkdir -p /config /passwords /certs \
 && chown certalert:certalert /config /passwords /certs

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java","-jar","certalert.jar"]
