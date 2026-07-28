import json
from decimal import Decimal
from pathlib import Path

from app.analysis_context_models import AnalysisContext
from app.models import MarketingInsightRequest
from app.prompts import build_analysis_prompt


FIXTURE_PATH = (
    Path(__file__).parent
    / "fixtures"
    / "analysis_context_meta.json"
)


def load_fixture() -> dict:
    return json.loads(
        FIXTURE_PATH.read_text(encoding="utf-8")
    )


def test_real_spring_analysis_context_is_valid() -> None:
    payload = load_fixture()

    context = AnalysisContext.model_validate(payload)

    assert context.schema_version == "1.0"
    assert context.provider == "META"

    assert (
        context.ad_account_id
        == "act_481686937778670"
    )

    assert context.timezone == "America/New_York"

    assert (
        context.current_period.start.isoformat()
        == "2026-07-14"
    )
    assert (
        context.current_period.stop.isoformat()
        == "2026-07-21"
    )

    assert context.generated_at.tzinfo is not None

    assert context.scope.selected_object_ids == [
        "120244089191690082"
    ]
    assert context.scope.selected_object_count == 1

    assert (
        context.summary.metrics["spend"].value
        == Decimal("50.50")
    )
    assert (
        context.summary.metrics["spend"].available
        is True
    )

    assert (
        context.summary.metrics["reach"].value
        is None
    )
    assert (
        context.summary.metrics["reach"].available
        is False
    )

    assert (
        context.summary.metrics["outboundClicks"].value
        is None
    )

    assert context.comparison is not None
    assert context.time_series is not None
    assert context.rankings is not None
    assert context.breakdowns is not None
    assert context.findings is not None

    assert len(context.time_series.series) == 2
    assert len(context.rankings.results) == 1
    assert len(context.breakdowns) == 2
    assert len(context.findings) == 5


def test_context_can_be_serialized_back_to_spring_format() -> None:
    context = AnalysisContext.model_validate(
        load_fixture()
    )

    result = context.model_dump(
        by_alias=True,
        mode="json",
    )

    assert result["schemaVersion"] == "1.0"

    assert (
        result["adAccountId"]
        == "act_481686937778670"
    )

    assert (
        result["currentPeriod"]["start"]
        == "2026-07-14"
    )

    assert result["scope"]["selectedObjectIds"] == [
        "120244089191690082"
    ]


def test_optional_sections_can_be_null() -> None:
    payload = load_fixture()

    payload["comparisonPeriod"] = None
    payload["comparison"] = None
    payload["timeSeries"] = None
    payload["rankings"] = None
    payload["breakdowns"] = None
    payload["findings"] = None
    payload["dataQualityIssues"] = None

    context = AnalysisContext.model_validate(payload)

    assert context.comparison_period is None
    assert context.comparison is None
    assert context.time_series is None
    assert context.rankings is None
    assert context.breakdowns is None
    assert context.findings is None
    assert context.data_quality_issues is None


def test_unavailable_metric_is_not_changed_to_zero() -> None:
    context = AnalysisContext.model_validate(
        load_fixture()
    )

    reach = context.summary.metrics["reach"]

    assert reach.available is False
    assert reach.value is None


def test_real_zero_is_allowed() -> None:
    payload = load_fixture()

    payload["summary"]["metrics"]["clicks"] = {
        "value": 0,
        "available": True,
        "unavailableReason": None,
        "unit": "count",
        "currency": None,
    }

    context = AnalysisContext.model_validate(payload)

    clicks = context.summary.metrics["clicks"]

    assert clicks.value == Decimal("0")
    assert clicks.available is True


def test_prompt_contains_question_and_preserves_nulls() -> None:
    request = MarketingInsightRequest.model_validate(
        {
            "analysisContext": load_fixture(),
            "question": (
                "Explain the campaign performance."
            ),
        }
    )

    prompt = build_analysis_prompt(request)

    assert (
        "Explain the campaign performance."
        in prompt
    )
    assert '"available": false' in prompt
    assert '"value": null' in prompt
    assert '"outboundClicks"' in prompt

def test_summary_accepts_structured_warnings() -> None:
    payload = load_fixture()

    payload["summary"]["warnings"] = [
        {
            "code": "INSIGHT_NO_ACTIVITY_IN_PERIOD",
            "message": (
                "The request was fully processed, but the "
                "provider reported no delivery for this period."
            ),
        }
    ]

    context = AnalysisContext.model_validate(payload)

    assert len(context.summary.warnings) == 1
    assert (
        context.summary.warnings[0].code
        == "INSIGHT_NO_ACTIVITY_IN_PERIOD"
    )