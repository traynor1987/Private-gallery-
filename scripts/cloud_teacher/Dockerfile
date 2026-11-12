FROM python:3.13-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
RUN install -d -o 10001 -g 10001 -m 0700 /state
COPY --chown=10001:10001 server.py budget.py ./
USER 10001:10001
EXPOSE 8787
CMD ["python", "server.py"]
