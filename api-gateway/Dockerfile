# Stage 1: Builder
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy only the pom.xml first to take advantage of Docker cache
COPY pom.xml ./

# Fetch dependencies and cache them
RUN mvn dependency:go-offline -B

# Now copy the source code and build the project
COPY src ./src

# Build the project
RUN mvn clean package -DskipTests

# Stage 2: Runner (final image)
FROM eclipse-temurin:21-jre AS runner

WORKDIR /app

# Copy the built JAR file from the builder stage to the runner stage
COPY --from=builder /app/target/api-gateway-0.0.1-SNAPSHOT.jar ./app.jar

# Expose the application port
EXPOSE 4004

# Run the JAR file
ENTRYPOINT ["java", "-jar", "app.jar"]
