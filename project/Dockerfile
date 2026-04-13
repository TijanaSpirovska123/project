FROM maven:3.9.4-amazoncorretto-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY ./src ./src
RUN mvn clean package -DskipTests -q

FROM amazoncorretto:21
WORKDIR /app
RUN yum install -y which tar xz 2>/dev/null || true && \
    amazon-linux-extras install -y epel 2>/dev/null || true && \
    yum install -y ffmpeg 2>/dev/null || \
    (curl -L https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz | tar -xJ && \
     mv ffmpeg-*-amd64-static/ffmpeg /usr/local/bin/ && \
     mv ffmpeg-*-amd64-static/ffprobe /usr/local/bin/ && \
     rm -rf ffmpeg-*-amd64-static)
RUN addgroup -g 1001 appgroup && adduser -u 1001 -G appgroup -s /bin/sh -D appuser
USER appuser
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "-Xmx512m", "-Xms256m", "app.jar"]
