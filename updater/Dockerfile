FROM python:3.12-alpine

RUN apk add --no-cache docker-cli docker-cli-compose git ca-certificates     && addgroup -S updater     && adduser -S -G updater -h /tmp updater

WORKDIR /updater
COPY --chown=updater:updater server.py .

USER updater

ENV UPDATER_PORT=8090
EXPOSE 8090

CMD ["python", "/updater/server.py"]
