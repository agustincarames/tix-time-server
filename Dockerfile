FROM gradle:jdk8-alpine

# Install app dependencies
COPY settings.gradle .
COPY build.gradle .
RUN gradle getDeps

# Build app
COPY src ./src
RUN gradle fatJar

# Bundle compiled app into target image
FROM openjdk:8-jre-alpine
RUN apk add bash
WORKDIR /root/tix-time-server
COPY wait-for-it.sh .
COPY run.sh .
COPY --from=0 /home/gradle/build/libs/tix-time-server-all.jar tix-time-server.jar

EXPOSE 4500/udp 8080/tcp
CMD ["./run.sh"]
