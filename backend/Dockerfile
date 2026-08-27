FROM python:3.12-slim

WORKDIR /app

COPY pyproject.toml ./
COPY app ./app
COPY scripts ./scripts

RUN pip install --no-cache-dir .

ENV PYTHONUNBUFFERED=1

CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT:-8080}"]
