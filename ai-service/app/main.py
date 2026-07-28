from fastapi import FastAPI, HTTPException

from app.models import MarketingInsightRequest, MarketingInsightResponse
from app.services.llm_service import generate_marketing_insights
from app.exceptions import (
    LLMInvalidResponseError,
    LLMUnavailableError,
)

app = FastAPI(
    title="AI Marketing Insights Assistant",
    version="0.1.0",
    description="Phase 0 LLM integration prototype using Python, FastAPI, and Pydantic.",
)


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