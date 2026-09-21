FROM python:3.12-alpine
RUN apk add --no-cache docker-cli docker-cli-compose git curl jq
WORKDIR /app
COPY server.py /app/server.py
EXPOSE 8091
CMD ["python", "/app/server.py"]
