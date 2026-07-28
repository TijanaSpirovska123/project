from __future__ import annotations

from datetime import date, datetime
from decimal import Decimal
from typing import Any, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    model_validator,
)
from pydantic.alias_generators import to_camel


Provider = Literal[
    "META",
    "TIKTOK",
    "GOOGLE_ADS",
    "LINKEDIN",
    "EMAIL",
    "OTHER",
]

ComparisonDirection = Literal[
    "INCREASED",
    "DECREASED",
    "UNCHANGED",
    "UNAVAILABLE",
    "UNKNOWN",
]


class ApiModel(BaseModel):
    """
    Base model for JSON exchanged with Spring Boot.

    Python uses snake_case internally.
    Spring Boot sends and receives camelCase.
    """

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="forbid",
    )


class Period(ApiModel):
    start: date
    stop: date

    @model_validator(mode="after")
    def validate_period(self) -> "Period":
        if self.start > self.stop:
            raise ValueError(
                "Period start must be before or equal to stop."
            )

        return self


class Coverage(ApiModel):
    sync_complete: bool
    days_with_activity: int = Field(ge=0)


class AnalyticsScope(ApiModel):
    object_type: str

    selected_object_ids: list[str]
    selected_object_count: int = Field(ge=0)

    objects_with_activity: int = Field(ge=0)
    objects_without_activity: int = Field(ge=0)

    single_entity: bool

    @model_validator(mode="after")
    def validate_selected_objects(self) -> "AnalyticsScope":
        if self.selected_object_count != len(
            self.selected_object_ids
        ):
            raise ValueError(
                "selectedObjectCount must match the number of "
                "selectedObjectIds."
            )

        return self


class MetricValue(ApiModel):
    # Null means unavailable.
    # Zero means available and genuinely zero.
    value: Decimal | None = Field(
        default=None,
        ge=0,
    )

    available: bool
    unavailable_reason: str | None = None

    unit: str
    currency: str | None = None

    @model_validator(mode="after")
    def validate_availability(self) -> "MetricValue":
        if self.available and self.value is None:
            raise ValueError(
                "An available metric must contain a value."
            )

        if not self.available and self.value is not None:
            raise ValueError(
                "An unavailable metric must have a null value."
            )

        return self

class InsightWarning(ApiModel):
    code: str
    message: str


class AnalyticsSummary(ApiModel):
    provider: Provider
    ad_account_id: str
    requested_period: Period
    scope: AnalyticsScope

    currency: str | None = None
    metrics: dict[str, MetricValue]

    sync_status: str

    warnings: list[InsightWarning] = Field(
        default_factory=list
    )

class ComparisonMetric(ApiModel):
    metric: str

    current_value: Decimal | None = None
    previous_value: Decimal | None = None

    # These can be negative when performance decreases.
    absolute_change: Decimal | None = None
    percentage_change: Decimal | None = None

    change_reason: str | None = None
    direction: ComparisonDirection

    available: bool
    unavailable_reason: str | None = None


class AnalyticsComparison(ApiModel):
    current_period: Period
    comparison_period: Period

    metrics: list[ComparisonMetric] = Field(
        default_factory=list
    )

    current_coverage: Coverage
    comparison_coverage: Coverage


class TimeSeriesPoint(ApiModel):
    date: date

    metrics: dict[str, MetricValue] = Field(
        default_factory=dict
    )


class AnalyticsTimeSeries(ApiModel):
    provider: Provider
    granularity: str
    period: Period

    series: list[TimeSeriesPoint] = Field(
        default_factory=list
    )


class RankingResult(ApiModel):
    rank: int = Field(ge=1)

    object_type: str
    object_external_id: str
    object_name: str

    value: Decimal
    currency: str | None = None

    activity_period: Period | None = None

    spend: Decimal | None = Field(
        default=None,
        ge=0,
    )
    impressions: int | None = Field(
        default=None,
        ge=0,
    )
    clicks: int | None = Field(
        default=None,
        ge=0,
    )


class AnalyticsRankings(ApiModel):
    metric: str
    results: list[RankingResult] = Field(
        default_factory=list
    )


class BreakdownRow(ApiModel):
    dimension: str
    dimension_value: str

    spend: Decimal | None = Field(
        default=None,
        ge=0,
    )
    impressions: int | None = Field(
        default=None,
        ge=0,
    )
    clicks: int | None = Field(
        default=None,
        ge=0,
    )
    reach: int | None = Field(
        default=None,
        ge=0,
    )

    ctr: Decimal | None = Field(
        default=None,
        ge=0,
    )
    cpc: Decimal | None = Field(
        default=None,
        ge=0,
    )
    cpm: Decimal | None = Field(
        default=None,
        ge=0,
    )

    conversions: Decimal | None = Field(
        default=None,
        ge=0,
    )
    purchases: Decimal | None = Field(
        default=None,
        ge=0,
    )
    purchase_value: Decimal | None = Field(
        default=None,
        ge=0,
    )
    roas: Decimal | None = Field(
        default=None,
        ge=0,
    )

    conversion_data_available: bool

    share: Decimal | None = Field(
        default=None,
        ge=0,
    )
    share_metric: str

    warnings: list[str] = Field(default_factory=list)


class AnalyticsBreakdown(ApiModel):
    dimension: str
    share_metric: str

    rows: list[BreakdownRow] = Field(
        default_factory=list
    )


class FindingEvidence(ApiModel):
    metric: str

    current_value: Decimal | None = None
    previous_value: Decimal | None = None
    percentage_change: Decimal | None = None


class DeterministicFinding(ApiModel):
    code: str
    severity: str

    scope: dict[str, Any] | None = None

    title: str
    message: str

    evidence: list[FindingEvidence] = Field(
        default_factory=list
    )

    confidence: str


class DataQualityIssue(ApiModel):
    """
    Data-quality issue generated by the Spring analytics layer.
    """

    code: str
    severity: str

    scope: dict[str, Any] | None = None

    message: str

    affected_metrics: list[str] = Field(
        default_factory=list
    )


class ProviderCapabilities(ApiModel):
    supported_metrics: list[str]
    supported_breakdowns: list[str]

    conversion_metrics_available: bool
    supports_daily_time_series: bool
    supports_comparison: bool
    supports_exact_aggregate_reach: bool


class AnalysisContext(ApiModel):
    schema_version: str
    provider: Provider

    ad_account_id: str
    currency: str | None = None
    timezone: str
    generated_at: datetime

    scope: AnalyticsScope

    current_period: Period
    comparison_period: Period | None = None

    coverage: Coverage
    summary: AnalyticsSummary

    # Null when comparison was not requested.
    comparison: AnalyticsComparison | None = None

    # Null when time series was not requested.
    time_series: AnalyticsTimeSeries | None = None

    # Null when rankings were not requested.
    rankings: AnalyticsRankings | None = None

    # Null when no breakdown section was requested.
    breakdowns: list[AnalyticsBreakdown] | None = None

    # Null when includeFindings is false.
    findings: list[DeterministicFinding] | None = None

    # Null when includeDataQuality is false.
    data_quality_issues: list[DataQualityIssue] | None = None

    capabilities: ProviderCapabilities