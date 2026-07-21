# =============================================================
# STAGE 1: BUILD ENVIRONMENT
# -------------------------------------------------------------
# Purpose:
# - Spring Boot application build karna (Maven)
# - C++ database engine compile karna (G++)
# =============================================================

FROM maven:3.9.6-eclipse-temurin-17 AS build

# Container ke andar working directory
WORKDIR /workspace

# -------------------------------------------------------------
# Install C++ compiler and build tools
# -------------------------------------------------------------
RUN apt-get update && \
    apt-get install -y g++ build-essential && \
    rm -rf /var/lib/apt/lists/*

# -------------------------------------------------------------
# Copy entire project into container
# -------------------------------------------------------------
COPY . .

# -------------------------------------------------------------
# JAVA BUILD (Spring Boot)
# pom.xml 'app' folder ke andar hai
# Tests skip kiye gaye hain faster build ke liye
# -------------------------------------------------------------
RUN mvn -f app/pom.xml clean package -DskipTests

# -------------------------------------------------------------
# C++ DATABASE ENGINE BUILD
# - All .cpp files compile honge
# - Output binary: core/4mulaQuery
# - C++17 standard use kiya gaya hai
# -------------------------------------------------------------
RUN g++ -O3 -std=c++17 -static-libgcc -static-libstdc++ core/*.cpp -o core/4mulaQuery


# =============================================================
# STAGE 2: RUNTIME ENVIRONMENT
# -------------------------------------------------------------
# Purpose:
# - Lightweight environment jahan sirf application run hogi
# - Java runtime + compiled C++ engine
# =============================================================

FROM eclipse-temurin:17-jre-jammy

# Application directory
WORKDIR /app

# -------------------------------------------------------------
# Install runtime dependency for C++ binary
# -------------------------------------------------------------
RUN apt-get update && \
    apt-get install -y libstdc++6 && \
    rm -rf /var/lib/apt/lists/*

# -------------------------------------------------------------
# Copy built artifacts from build stage
# -------------------------------------------------------------

# Spring Boot jar file
COPY --from=build /workspace/app/target/*.jar app.jar

# C++ database engine binary
COPY --from=build /workspace/core/4mulaQuery ./core/4mulaQuery


# -------------------------------------------------------------
# FILE PERMISSIONS & DATABASE SETUP
# -------------------------------------------------------------
# - C++ engine executable banaya
# - data folder create kiya
# - database file create ki
# - full read/write permission diya
# -------------------------------------------------------------
RUN mkdir -p data && \
    chmod +x ./core/4mulaQuery && \
    touch data/4mulaQuery.db && \
    chmod -R 777 data


# -------------------------------------------------------------
# Network configuration
# -------------------------------------------------------------
EXPOSE 8080
ENV PORT=8080


# -------------------------------------------------------------
# Application startup command
# -------------------------------------------------------------
# Spring Boot jar run karega
# egd option faster startup ke liye
# -------------------------------------------------------------
ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT} -jar app.jar"]