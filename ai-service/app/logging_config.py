from __future__ import annotations

import contextvars
import json
import logging
from typing import Any

REQUEST_ID_CTX_VAR: contextvars.ContextVar[str] = contextvars.ContextVar(
    "request_id", default="-"
)

# Standard LogRecord attributes: everything else attached to a record
# (via `extra={...}`) is treated as a structured field and merged into
# the JSON payload as-is.
_RESERVED_RECORD_KEYS = {
    "name", "msg", "args", "levelname", "levelno", "pathname", "filename",
    "module", "exc_info", "exc_text", "stack_info", "lineno", "funcName",
    "created", "msecs", "relativeCreated", "thread", "threadName",
    "processName", "process", "message", "taskName", "request_id",
}


class RequestIdFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        record.request_id = REQUEST_ID_CTX_VAR.get()
        return True


class JsonFormatter(logging.Formatter):
    def format(self, record: logging.LogRecord) -> str:
        payload: dict[str, Any] = {
            "timestamp": self.formatTime(record, "%Y-%m-%dT%H:%M:%S%z"),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
            "request_id": getattr(record, "request_id", "-"),
        }

        if record.exc_info:
            payload["exception"] = self.formatException(record.exc_info)

        for key, value in record.__dict__.items():
            if key in _RESERVED_RECORD_KEYS:
                continue

            payload[key] = value

        return json.dumps(payload, default=str)


def configure_logging(level: str = "INFO") -> None:
    """
    Route every logger (ours and uvicorn's) through a single JSON handler
    tagged with the current request id, so `docker logs` output can be
    filtered/parsed by field instead of grepped by string.
    """

    handler = logging.StreamHandler()
    handler.setFormatter(JsonFormatter())
    handler.addFilter(RequestIdFilter())

    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(level)

    for logger_name in ("uvicorn", "uvicorn.error"):
        uv_logger = logging.getLogger(logger_name)
        uv_logger.handlers = [handler]
        uv_logger.propagate = False

    # uvicorn's own access line duplicates the one our middleware emits.
    # uvicorn decides whether to log it via `logger.hasHandlers()`
    # (checked at request time, not just the --access-log CLI flag), so it
    # must have zero handlers *and* not propagate to be silenced.
    access_logger = logging.getLogger("uvicorn.access")
    access_logger.handlers = []
    access_logger.propagate = False
