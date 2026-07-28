import json
from pathlib import Path

from fastapi.testclient import TestClient

from app.main import app


client = TestClient(app)

FIXTURE_PATH = (
    Path(__file__).parent
    / "fixtures"
    / "analysis_context_meta.json"
)


def load_analysis_context() -> dict:
    return json.loads(
        FIXTURE_PATH.read_text(encoding="utf-8")
    )


def test_health_check() -> None:
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_invalid_request_missing_required_fields() -> None:
    response = client.post(
        "/insights/analyze",
        json={},
    )

    assert response.status_code == 422


def test_analyze_rejects_negative_metric(
    monkeypatch,
) -> None:
    monkeypatch.setenv("USE_MOCK_LLM", "true")

    context = load_analysis_context()

    context["summary"]["metrics"]["clicks"]["value"] = -10

    response = client.post(
        "/insights/analyze",
        json={
            "analysisContext": context,
            "question": "Analyze this campaign.",
        },
    )

    assert response.status_code == 422


def test_analyze_rejects_available_metric_without_value(
    monkeypatch,
) -> None:
    monkeypatch.setenv("USE_MOCK_LLM", "true")

    context = load_analysis_context()

    context["summary"]["metrics"]["clicks"] = {
        "value": None,
        "available": True,
        "unavailableReason": None,
        "unit": "count",
        "currency": None,
    }

    response = client.post(
        "/insights/analyze",
        json={
            "analysisContext": context,
            "question": "Analyze this campaign.",
        },
    )

    assert response.status_code == 422


def test_analyze_rejects_unavailable_metric_with_value(
    monkeypatch,
) -> None:
    monkeypatch.setenv("USE_MOCK_LLM", "true")

    context = load_analysis_context()

    context["summary"]["metrics"]["reach"] = {
        "value": 0,
        "available": False,
        "unavailableReason": (
            "NOT_RETURNED_BY_PROVIDER"
        ),
        "unit": "count",
        "currency": None,
    }

    response = client.post(
        "/insights/analyze",
        json={
            "analysisContext": context,
            "question": "Analyze this campaign.",
        },
    )

    assert response.status_code == 422


def test_analyze_accepts_null_breakdowns(
    monkeypatch,
) -> None:
    monkeypatch.setenv("USE_MOCK_LLM", "true")

    context = load_analysis_context()
    context["breakdowns"] = None

    response = client.post(
        "/insights/analyze",
        json={
            "analysisContext": context,
            "question": "Analyze this campaign.",
        },
    )

    assert response.status_code == 200, response.text


def test_analyze_endpoint_with_mock_provider(
    monkeypatch,
) -> None:
    monkeypatch.setenv("USE_MOCK_LLM", "true")

    request_body = {
        "analysisContext": load_analysis_context(),
        "question": (
            "Explain the campaign performance and recommend "
            "the next actions."
        ),
    }

    response = client.post(
        "/insights/analyze",
        json=request_body,
    )

    assert response.status_code == 200, response.text

    data = response.json()

    assert "summary" in data
    assert "answer" in data
    assert "keyInsights" in data
    assert "risks" in data
    assert "recommendations" in data
    assert "limitations" in data
    assert "conclusion" in data
    assert "confidenceScore" in data