FROM python:3.12-alpine

RUN apk add --no-cache docker-cli docker-cli-compose git curl

WORKDIR /updater
COPY server.py .

ENV UPDATER_PORT=8090
EXPOSE 8090

CMD ["python", "/updater/server.py"]
