import logging
import time
import uuid

from fastapi import FastAPI, HTTPException, Request

from app.logging_config import REQUEST_ID_CTX_VAR, configure_logging
from app.models import MarketingInsightRequest, MarketingInsightResponse
from app.services.llm_service import generate_marketing_insights
from app.exceptions import (
    LLMInvalidResponseError,
    LLMUnavailableError,
)

configure_logging()

logger = logging.getLogger(__name__)

app = FastAPI(
    title="AI Marketing Insights Assistant",
    version="0.1.0",
    description="Phase 0 LLM integration prototype using Python, FastAPI, and Pydantic.",
)

REQUEST_ID_HEADER = "X-Request-Id"


@app.middleware("http")
async def request_context_middleware(request: Request, call_next):
    request_id = (
        request.headers.get(REQUEST_ID_HEADER)
        or str(uuid.uuid4())
    )
    token = REQUEST_ID_CTX_VAR.set(request_id)
    start = time.perf_counter()

    try:
        response = await call_next(request)

        duration_ms = round((time.perf_counter() - start) * 1000, 2)

        logger.info(
            "%s %s -> %s",
            request.method,
            request.url.path,
            response.status_code,
            extra={
                "http_method": request.method,
                "http_path": request.url.path,
                "status_code": response.status_code,
                "duration_ms": duration_ms,
            },
        )

        response.headers[REQUEST_ID_HEADER] = request_id
        return response
    finally:
        REQUEST_ID_CTX_VAR.reset(token)


@app.get("/health")
def health_check():
    return {
        "status": "ok",
        "service": "AI Marketing Insights Assistant",
    }


@app.post(
    "/insights/analyze",
    response_model=MarketingInsightResponse,
)
def analyze_insights(
    request: MarketingInsightRequest,
) -> MarketingInsightResponse:
    try:
        return generate_marketing_insights(request)

    except LLMUnavailableError as exc:
        raise HTTPException(
            status_code=503,
            detail=str(exc),
        ) from exc

    except LLMInvalidResponseError as exc:
        raise HTTPException(
            status_code=502,
            detail=str(exc),
        ) from exc

    except ValueError as exc:
        raise HTTPException(
            status_code=500,
            detail=str(exc),
        ) from exc
