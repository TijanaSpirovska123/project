from __future__ import annotations

import logging
import os
import time
from functools import lru_cache
from pathlib import Path
from typing import Any

from dotenv import load_dotenv
from openai import (
    APIConnectionError,
    APIStatusError,
    APITimeoutError,
    AuthenticationError,
    BadRequestError,
    OpenAI,
    RateLimitError,
)
from pydantic import ValidationError

from app.analysis_context_models import AnalysisContext
from app.exceptions import (
    LLMInvalidResponseError,
    LLMUnavailableError,
)
from app.models import (
    InsightItem,
    MarketingInsightRequest,
    MarketingInsightResponse,
    RecommendationItem,
)
from app.prompts import SYSTEM_PROMPT, build_analysis_prompt


BASE_DIR = Path(__file__).resolve().parents[2]
load_dotenv(BASE_DIR / ".env")

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Mock provider
# ---------------------------------------------------------------------------

def generate_mock_insights(
    request: MarketingInsightRequest,
) -> MarketingInsightResponse:
    context = request.analysis_context

    metric_values = {
        metric_name: metric.value
        for metric_name, metric in context.summary.metrics.items()
        if metric.available and metric.value is not None
    }

    ctr = metric_values.get("ctr")
    conversion_rate = metric_values.get("conversionRate")
    impressions = metric_values.get("impressions")
    clicks = metric_values.get("clicks")
    landing_page_views = metric_values.get("landingPageViews")
    purchases = metric_values.get("purchases")
    roas = metric_values.get("roas")

    key_insights: list[InsightItem] = []
    risks: list[InsightItem] = []
    recommendations: list[RecommendationItem] = []

    # data_quality_issues can be null when the section was not requested.
    limitations = [
        issue.message
        for issue in (context.data_quality_issues or [])
    ]

    if impressions is not None and clicks is not None:
        key_insights.append(
            InsightItem(
                title="Traffic performance available",
                explanation=(
                    f"The campaign generated {clicks} clicks "
                    f"from {impressions} impressions."
                ),
                evidence=[
                    f"Impressions: {impressions}",
                    f"Clicks: {clicks}",
                ],
                severity="Information",
            )
        )

    if ctr is not None:
        key_insights.append(
            InsightItem(
                title="Click-through rate available",
                explanation=(
                    f"The campaign recorded a CTR of {ctr}%."
                ),
                evidence=[
                    f"CTR: {ctr}%",
                ],
                severity="Information",
            )
        )

    if (
        clicks is not None
        and landing_page_views is not None
        and clicks > 0
        and landing_page_views < clicks
    ):
        risks.append(
            InsightItem(
                title="Click and landing-page-view difference",
                explanation=(
                    f"The campaign recorded {clicks} clicks but only "
                    f"{landing_page_views} landing-page views."
                ),
                evidence=[
                    f"Clicks: {clicks}",
                    (
                        "Landing-page views: "
                        f"{landing_page_views}"
                    ),
                ],
                severity="Warning",
            )
        )

        recommendations.append(
            RecommendationItem(
                action="Review landing-page loading and tracking",
                reason=(
                    "A difference between clicks and landing-page "
                    "views may indicate tracking problems, slow "
                    "loading, or users leaving before the page "
                    "finishes loading."
                ),
                priority="High",
                suggested_change=None,
                risk=(
                    "The difference does not prove the exact cause "
                    "without additional technical and behavioral data."
                ),
            )
        )

    if conversion_rate is not None and conversion_rate < 2:
        risks.append(
            InsightItem(
                title="Low conversion rate",
                explanation=(
                    f"The conversion rate is {conversion_rate}%."
                ),
                evidence=[
                    f"Conversion rate: {conversion_rate}%",
                ],
                severity="Warning",
            )
        )

    if purchases is None or roas is None:
        purchases_text = (
            str(purchases)
            if purchases is not None
            else "unavailable"
        )

        roas_text = (
            str(roas)
            if roas is not None
            else "unavailable"
        )

        risks.append(
            InsightItem(
                title="Business impact cannot be confirmed",
                explanation=(
                    "Purchases or ROAS are unavailable, so business "
                    "performance cannot be fully evaluated."
                ),
                evidence=[
                    f"Purchases: {purchases_text}",
                    f"ROAS: {roas_text}",
                ],
                severity="Warning",
            )
        )

        limitation = "Purchase and ROAS data are unavailable."

        if limitation not in limitations:
            limitations.append(limitation)

    if not key_insights:
        key_insights.append(
            InsightItem(
                title="Limited measurable signals",
                explanation=(
                    "The supplied metrics are insufficient for a "
                    "detailed campaign analysis."
                ),
                evidence=[
                    (
                        "No usable metric values were found in the "
                        "supplied data."
                    )
                ],
                severity="Information",
            )
        )

    if not risks:
        risks.append(
            InsightItem(
                title="Limited risk visibility",
                explanation=(
                    "No major risk can be established from the "
                    "supplied data."
                ),
                evidence=[
                    (
                        "No metric values indicating a clear risk "
                        "were found."
                    )
                ],
                severity="Information",
            )
        )

    if not recommendations:
        recommendations.append(
            RecommendationItem(
                action="Collect conversion and revenue metrics",
                reason=(
                    "Conversion, purchase-value, and ROAS data are "
                    "required for stronger business recommendations."
                ),
                priority="Medium",
                suggested_change=None,
                risk=None,
            )
        )

    if purchases is not None and roas is not None:
        answer = (
            f"The selected advertising data reports {purchases} "
            f"purchases and a ROAS of {roas}. Comparison data and "
            "deterministic findings should be reviewed before "
            "making budget decisions."
        )

        conclusion = (
            "Purchase and ROAS data are available, allowing business "
            "performance to be evaluated from the supplied context."
        )
    else:
        answer = (
            "Traffic metrics can be evaluated, but business impact "
            "cannot be fully confirmed because purchase or ROAS "
            "data is unavailable."
        )

        conclusion = (
            "Stronger business conclusions require complete "
            "conversion, purchase-value, and ROAS data."
        )

    return MarketingInsightResponse(
        summary=(
            f"The selected {context.scope.object_type.lower()} "
            "scope was analyzed using the supplied normalized metrics."
        ),
        answer=answer,
        key_insights=key_insights,
        risks=risks,
        recommendations=recommendations,
        limitations=limitations,
        conclusion=conclusion,
        confidence_score=0.70,
    )


# ---------------------------------------------------------------------------
# Context compaction
# ---------------------------------------------------------------------------

def _warning_key(
    warning: Any,
) -> tuple[str, str]:
    if isinstance(warning, dict):
        return (
            str(warning.get("code", "")),
            str(warning.get("message", "")),
        )

    return ("UNKNOWN_WARNING", str(warning))


def compact_context_for_llm(
    context: AnalysisContext,
) -> dict[str, Any]:
    """
    Reduce repeated or excessively large sections before sending the
    analytics context to the LLM.

    The original validated AnalysisContext remains unchanged.
    """

    # Keep null values because null means unavailable, not zero.
    payload = context.model_dump(
        by_alias=True,
        mode="json",
        exclude_none=False,
    )

    summary = payload.get("summary")

    if isinstance(summary, dict):
        warnings = summary.get("warnings")

        if isinstance(warnings, list):
            unique_warnings: list[Any] = []
            seen: set[tuple[str, str]] = set()

            for warning in warnings:
                key = _warning_key(warning)

                if key in seen:
                    continue

                seen.add(key)
                unique_warnings.append(warning)

            summary["warnings"] = unique_warnings[:10]

    comparison = payload.get("comparison")

    if isinstance(comparison, dict):
        metrics = comparison.get("metrics")

        if isinstance(metrics, list):
            comparison["metrics"] = metrics[:25]

    time_series = payload.get("timeSeries")

    if isinstance(time_series, dict):
        series = time_series.get("series")

        if isinstance(series, list):
            # Keep the latest 31 points.
            time_series["series"] = series[-31:]

    rankings = payload.get("rankings")

    if isinstance(rankings, dict):
        results = rankings.get("results")

        if isinstance(results, list):
            rankings["results"] = results[:10]

    breakdowns = payload.get("breakdowns")

    if isinstance(breakdowns, list):
        compact_breakdowns: list[Any] = []

        for breakdown in breakdowns[:6]:
            if not isinstance(breakdown, dict):
                compact_breakdowns.append(breakdown)
                continue

            compact_breakdown = dict(breakdown)
            rows = compact_breakdown.get("rows")

            if isinstance(rows, list):
                compact_breakdown["rows"] = rows[:10]

            compact_breakdowns.append(compact_breakdown)

        payload["breakdowns"] = compact_breakdowns

    findings = payload.get("findings")

    if isinstance(findings, list):
        payload["findings"] = findings[:10]

    data_quality_issues = payload.get("dataQualityIssues")

    if isinstance(data_quality_issues, list):
        payload["dataQualityIssues"] = (
            data_quality_issues[:10]
        )

    return payload


def build_compact_request(
    request: MarketingInsightRequest,
) -> MarketingInsightRequest:
    compact_payload = compact_context_for_llm(
        request.analysis_context
    )

    compact_context = AnalysisContext.model_validate(
        compact_payload
    )

    return MarketingInsightRequest(
        analysis_context=compact_context,
        question=request.question,
    )


# ---------------------------------------------------------------------------
# Groq client
# ---------------------------------------------------------------------------

@lru_cache(maxsize=1)
def get_groq_client() -> OpenAI:
    api_key = os.getenv("GROQ_API_KEY")

    if not api_key:
        raise RuntimeError(
            "GROQ_API_KEY is missing. Add it to the .env file."
        )

    return OpenAI(
        api_key=api_key,
        base_url="https://api.groq.com/openai/v1",
        timeout=45.0,

        # The service performs its own retries below.
        max_retries=0,
    )


def _status_error_body(
    exception: APIStatusError,
) -> str:
    try:
        return exception.response.text
    except Exception:
        return str(exception)


def _sleep_before_retry(
    attempt: int,
) -> None:
    delay_seconds = 2 ** (attempt - 1)
    time.sleep(delay_seconds)


def generate_groq_insights(
    request: MarketingInsightRequest,
) -> MarketingInsightResponse:
    client = get_groq_client()

    model = os.getenv(
        "GROQ_MODEL",
        "openai/gpt-oss-20b",
    )

    max_attempts = max(
        1,
        int(os.getenv("GROQ_MAX_ATTEMPTS", "3")),
    )

    max_completion_tokens = max(
        300,
        int(
            os.getenv(
                "GROQ_MAX_COMPLETION_TOKENS",
                "3200",
            )
        ),
    )

    # This is the important change:
    # build the prompt from the compacted request.
    compact_request = build_compact_request(request)
    prompt = build_analysis_prompt(compact_request)

    response_schema = (
        MarketingInsightResponse.model_json_schema(
            by_alias=True
        )
    )

    scope = request.analysis_context.scope

    log_context = {
        "object_type": scope.object_type,
        "object_ids": scope.selected_object_ids,
    }

    logger.info(
        "Sending Groq request. model=%s prompt_characters=%s "
        "max_completion_tokens=%s",
        model,
        len(prompt),
        max_completion_tokens,
        extra=log_context,
    )

    response = None

    for attempt in range(1, max_attempts + 1):
        try:
            response = client.chat.completions.create(
                model=model,
                messages=[
                    {
                        "role": "system",
                        "content": SYSTEM_PROMPT,
                    },
                    {
                        "role": "user",
                        "content": prompt,
                    },
                ],
                response_format={
                    "type": "json_schema",
                    "json_schema": {
                        "name": "marketing_insight_response",
                        "strict": False,
                        "schema": response_schema,
                    },
                },
                temperature=0.1,
                max_completion_tokens=max_completion_tokens,
            )

            break

        except RateLimitError as exc:
            logger.warning(
                "Groq rate limit on attempt %s/%s. error=%s",
                attempt,
                max_attempts,
                str(exc),
                extra=log_context,
            )

            if attempt < max_attempts:
                _sleep_before_retry(attempt)
                continue

            raise LLMUnavailableError(
                "Groq rate limit was reached."
            ) from exc

        except (
            APIConnectionError,
            APITimeoutError,
        ) as exc:
            logger.warning(
                "Groq connection attempt %s/%s failed. "
                "error_type=%s",
                attempt,
                max_attempts,
                type(exc).__name__,
                extra=log_context,
            )

            if attempt < max_attempts:
                _sleep_before_retry(attempt)
                continue

            raise LLMUnavailableError(
                "Groq could not be reached."
            ) from exc

        except AuthenticationError as exc:
            logger.exception(
                "Groq authentication failed.",
                extra=log_context,
            )

            raise LLMUnavailableError(
                "Groq authentication failed."
            ) from exc

        except BadRequestError as exc:
            body = _status_error_body(exc)

            logger.exception(
                "Groq rejected the request. status=%s body=%s",
                exc.status_code,
                body,
                extra=log_context,
            )

            raise LLMUnavailableError(
                "Groq rejected the generated request."
            ) from exc

        except APIStatusError as exc:
            status_code = exc.status_code
            body = _status_error_body(exc)

            logger.error(
                "Groq API error. status=%s body=%s",
                status_code,
                body,
                extra=log_context,
            )

            # A request-size error will not succeed after waiting.
            if status_code == 413:
                raise LLMUnavailableError(
                    "Groq rejected the request because the "
                    "generated prompt is too large."
                ) from exc

            retryable_statuses = {
                500,
                502,
                503,
                504,
            }

            if (
                status_code in retryable_statuses
                and attempt < max_attempts
            ):
                _sleep_before_retry(attempt)
                continue

            raise LLMUnavailableError(
                f"Groq returned HTTP {status_code}."
            ) from exc

        except Exception as exc:
            logger.exception(
                "Unexpected Groq error. error_type=%s error=%s",
                type(exc).__name__,
                str(exc),
                extra=log_context,
            )

            raise LLMUnavailableError(
                "Groq could not complete the request."
            ) from exc

    if response is None:
        raise LLMUnavailableError(
            "Groq did not return a response."
        )

    if not response.choices:
        raise LLMInvalidResponseError(
            "Groq returned no response choices."
        )

    content = response.choices[0].message.content

    if not content or not content.strip():
        raise LLMInvalidResponseError(
            "Groq returned an empty response."
        )

    try:
        return MarketingInsightResponse.model_validate_json(
            content
        )

    except ValidationError as exc:
        logger.error(
            "Groq response validation failed. errors=%s",
            exc.errors(include_url=False),
            extra=log_context,
        )

        raise LLMInvalidResponseError(
            "Groq returned a response that does not match "
            "MarketingInsightResponse."
        ) from exc


# ---------------------------------------------------------------------------
# Provider selection
# ---------------------------------------------------------------------------

def generate_marketing_insights(
    request: MarketingInsightRequest,
) -> MarketingInsightResponse:
    use_mock = (
        os.getenv("USE_MOCK_LLM", "false").lower()
        == "true"
    )

    if use_mock:
        return generate_mock_insights(request)

    provider = os.getenv(
        "LLM_PROVIDER",
        "groq",
    ).lower()

    if provider == "groq":
        return generate_groq_insights(request)

    raise ValueError(
        f"Unsupported LLM provider: {provider}"
    )