from dataclasses import dataclass


@dataclass(frozen=True)
class RiskPolicy:
    """Versioned thresholds and scores for rule-based risk analysis."""

    version: str = "v1"

    expiration_urgent_days: int = 7
    expiration_warning_days: int = 30
    expiration_urgent_score: int = 40
    expiration_warning_score: int = 20
    expired_score: int = 100

    rotation_urgent_days: int = 180
    rotation_warning_days: int = 90
    rotation_urgent_score: int = 25
    rotation_warning_score: int = 15

    active_member_threshold: int = 5
    active_member_score: int = 15

    denied_access_threshold_30d: int = 3
    denied_access_score: int = 20

    renewal_urgent_days: int = 7
    renewal_warning_days: int = 30
    renewal_urgent_score: int = 40
    renewal_warning_score: int = 20

    maximum_score: int = 100
    medium_score: int = 30
    high_score: int = 60
    critical_score: int = 80


DEFAULT_RISK_POLICY = RiskPolicy()
