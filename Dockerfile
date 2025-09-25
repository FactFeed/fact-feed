# --- 1. Build frontend ---
FROM node:20-bullseye-slim AS frontend
WORKDIR /frontend

# Install dependencies first (cached if package*.json unchanged)
COPY frontend/package*.json ./
RUN npm ci

# Copy source and build
COPY frontend/ ./
ENV NODE_ENV=production
RUN npm run build


# --- 2. Build backend ---
FROM gradle:8.14.3-jdk21-ubi-minimal AS backend
WORKDIR /backend

# Copy Gradle wrapper and settings (cache layer)
COPY backend/gradle ./gradle
COPY backend/gradlew .
COPY backend/build.gradle.kts backend/settings.gradle.kts ./

# Download dependencies (cached if no changes)
RUN ./gradlew dependencies --no-daemon || return 0

# Copy source
COPY backend/src ./src

# Copy frontend build into Spring Boot static resources
COPY --from=frontend /frontend/dist ./src/main/resources/static

# Build Spring Boot jar
RUN ./gradlew bootJar --no-daemon


# --- 3. Runtime image ---
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Install Chromium and Chromedriver
USER root
RUN apk add --no-cache \
    chromium \
    chromium-chromedriver \
    nss \
    freetype \
    harfbuzz \
    ttf-freefont \
    udev \
    wget \
    bash

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy built jar from backend stage
COPY --from=backend /backend/build/libs/*.jar app.jar

# Expose Render port
ENV PORT=8080
EXPOSE 8080

# Set environment vars so Selenium finds Chrome + Chromedriver
ENV CHROME_BIN=/usr/bin/chromium-browser
ENV CHROMEDRIVER_PATH=/usr/bin/chromedriver


# JVM options for container
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Dserver.address=0.0.0.0 -Dserver.port=${PORT}"

ENTRYPOINT ["java", "-jar", "app.jar"]

