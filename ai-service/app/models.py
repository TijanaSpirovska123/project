from datetime import date
from typing import List, Optional, Literal

from pydantic import BaseModel, Field, model_validator

from app.analysis_context_models import AnalysisContext, ApiModel

Provider = Literal[
    "META",
    "TIKTOK",
    "GOOGLE_ADS",
    "LINKEDIN",
    "EMAIL",
    "OTHER",
]

MetricUnit = Literal[
    "count",
    "percent",
    "currency",
    "ratio",
    "none",
]


class MarketingMetric(BaseModel):
    name: str = Field(
        min_length=1,
        description="Normalized metric name, for example spend, impressions, ctr or roas.",
    )

    # null means unavailable.
    # 0 means available and genuinely zero.
    value: Optional[float] = Field(
        default=None,
        ge=0,
    )

    unit: MetricUnit = "none"

    # Used only for monetary metrics.
    currency: Optional[str] = Field(
        default=None,
        min_length=3,
        max_length=3,
        examples=["USD", "EUR"],
    )

    available: bool = True

    @model_validator(mode="after")
    def validate_metric(self):
        if self.available and self.value is None:
            raise ValueError(
                "value is required when available is true"
            )

        if not self.available and self.value is not None:
            raise ValueError(
                "value must be null when available is false"
            )

        if (
            self.available
            and self.unit == "currency"
            and self.currency is None
        ):
            raise ValueError(
                "currency is required for available currency metrics"
            )

        return self


class MarketingInsightRequest(ApiModel):
    analysis_context: AnalysisContext

    question: str | None = Field(
        default=None,
        min_length=3,
        max_length=1000,
    )


class InsightItem(ApiModel):
    title: str = Field(min_length=3)
    explanation: str = Field(min_length=10)

    evidence: List[str] = Field(
        min_length=1,
        max_length=5,
    )

    severity: Literal[
        "Positive",
        "Information",
        "Warning",
        "Critical",
    ] = "Information"


class RecommendationItem(ApiModel):
    action: str = Field(min_length=3)
    reason: str = Field(min_length=10)

    priority: Literal[
        "Low",
        "Medium",
        "High",
    ]

    suggested_change: Optional[str] = None
    risk: Optional[str] = None


class MarketingInsightResponse(ApiModel):
    summary: str = Field(min_length=10)
    answer: str = Field(min_length=10)

    key_insights: list[InsightItem] = Field(
        min_length=1,
        max_length=5,
    )

    risks: list[InsightItem] = Field(
        default_factory=list,
        max_length=5,
    )

    recommendations: list[RecommendationItem] = Field(
        min_length=1,
        max_length=5,
    )

    limitations: list[str] = Field(
        default_factory=list
    )

    conclusion: str = Field(min_length=10)

    confidence_score: float = Field(
        ge=0,
        le=1,
    )