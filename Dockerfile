FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
# Local, memory-only OCR. The engine reads image bytes via stdin and emits text via stdout.
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-deu tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /src/target/ffh-verwaltung-0.0.1-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
