# ============================================
# Stage 1: Build React Frontend
# ============================================
FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm ci --production=false
COPY frontend/ ./
RUN npm run build

# ============================================
# Stage 2: Build Spring Boot Backend
# ============================================
FROM eclipse-temurin:21-jdk-alpine AS backend-build
WORKDIR /app/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/mvnw.cmd backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY backend/src ./src
# Copy frontend build output into Spring Boot's static resources
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN ./mvnw package -DskipTests -B

# ============================================
# Stage 3: Production Runtime
# ============================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create directories for data and file storage
RUN mkdir -p /app/data /app/storage

COPY --from=backend-build /app/backend/target/*.jar app.jar

EXPOSE 8080

# JVM optimized for containers with limited memory
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
