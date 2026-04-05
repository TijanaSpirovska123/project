FROM maven:3.9.4 as builder
WORKDIR /app
COPY ./pom.xml .
COPY ../src ./src

RUN mvn clean install

FROM amazoncorretto:21
WORKDIR /app
EXPOSE 8080
COPY --from=builder /app/target/project-0.0.1-SNAPSHOT.jar project.jar
ENTRYPOINT ["java","-jar","project.jar"]
