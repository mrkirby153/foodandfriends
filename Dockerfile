FROM gradle:8.10-jdk21-alpine as builder

ADD . /build
WORKDIR /build

RUN gradle bootJar

RUN rm -rfv build/libs/*-plain.jar && find build/libs -type f -name 'foodandfriends-?.*\.jar' -exec mv -v '{}' bot.jar ';'

FROM openjdk:23

RUN useradd -u 1000 bot

COPY --from=builder /build/bot.jar /bot/bot.jar
USER bot
WORKDIR /bot
CMD ["java", "-jar", "bot.jar"]