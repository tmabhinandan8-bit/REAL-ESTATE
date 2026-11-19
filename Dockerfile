# Multi-stage Docker build for Real Estate Management System (Java 11 + Supabase)
FROM eclipse-temurin:11-jdk-alpine AS build
WORKDIR /app
COPY RealEstateBackend.java .
RUN javac -d /app/out RealEstateBackend.java

FROM eclipse-temurin:11-jre-alpine
WORKDIR /app
COPY --from=build /app/out /app/out
COPY index.html /app/index.html
COPY src/main/webapp /app/src/main/webapp

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-cp", "/app/out", "RealEstateBackend"]
