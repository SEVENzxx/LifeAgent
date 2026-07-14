FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app

RUN groupadd --system lifeagent && useradd --system --gid lifeagent lifeagent
COPY pyproject.toml ./
COPY lifeagent_ai ./lifeagent_ai
RUN --mount=type=cache,target=/root/.cache/pip pip install .

USER lifeagent
EXPOSE 8000
CMD ["uvicorn", "lifeagent_ai.main:app", "--host", "0.0.0.0", "--port", "8000"]
