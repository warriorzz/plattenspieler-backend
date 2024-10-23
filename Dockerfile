FROM eclipse-temurin:21 AS builder

WORKDIR /usr/app
COPY . .

RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21

WORKDIR /usr/app

EXPOSE 8080

COPY --from=builder /usr/app/build/install/plattenspieler ./

ENTRYPOINT ["/usr/app/bin/plattenspieler"]