from datetime import datetime
from enum import Enum

from pydantic import BaseModel, ConfigDict, Field, model_validator


class RiskSchema(BaseModel):
    """Base schema for the rule-based Credential risk API."""

    model_config = ConfigDict(extra="forbid")


class CredentialType(str, Enum):
    API_KEY = "API_KEY"
    SUBSCRIPTION_PLAN = "SUBSCRIPTION_PLAN"
    DB_CREDENTIAL = "DB_CREDENTIAL"


class CredentialStatus(str, Enum):
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"
    EXPIRED = "EXPIRED"
    REVOKED = "REVOKED"


class RiskLevel(str, Enum):
    LOW = "LOW"
    MEDIUM = "MEDIUM"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


class RiskRule(str, Enum):
    API_KEY_EXPIRED = "API_KEY_EXPIRED"
    API_KEY_EXPIRING_WITHIN_7_DAYS = "API_KEY_EXPIRING_WITHIN_7_DAYS"
    API_KEY_EXPIRING_WITHIN_30_DAYS = "API_KEY_EXPIRING_WITHIN_30_DAYS"
    API_KEY_NOT_ROTATED_OVER_180_DAYS = "API_KEY_NOT_ROTATED_OVER_180_DAYS"
    API_KEY_NOT_ROTATED_OVER_90_DAYS = "API_KEY_NOT_ROTATED_OVER_90_DAYS"
    ACTIVE_MEMBERS_AT_LEAST_5 = "ACTIVE_MEMBERS_AT_LEAST_5"
    DENIED_ACCESS_AT_LEAST_3_IN_30_DAYS = (
        "DENIED_ACCESS_AT_LEAST_3_IN_30_DAYS"
    )
    SUBSCRIPTION_RENEWING_WITHIN_7_DAYS = (
        "SUBSCRIPTION_RENEWING_WITHIN_7_DAYS"
    )
    SUBSCRIPTION_RENEWING_WITHIN_30_DAYS = (
        "SUBSCRIPTION_RENEWING_WITHIN_30_DAYS"
    )


class CredentialRiskInput(RiskSchema):
    """Whitelisted Credential metadata used by the risk engine."""

    credentialId: int = Field(gt=0)
    projectId: int = Field(gt=0)
    title: str = Field(min_length=1, max_length=255)
    type: CredentialType
    provider: str | None = Field(default=None, max_length=100)
    status: CredentialStatus
    expiresAt: datetime | None = None
    renewalAt: datetime | None = None
    lastRotatedAt: datetime | None = None
    activeMemberCount: int = Field(default=0, ge=0)
    deniedAccessCount30d: int = Field(default=0, ge=0)


class ProjectRiskContext(RiskSchema):
    """Current project data collected for one risk calculation."""

    projectId: int = Field(gt=0)
    credentials: list[CredentialRiskInput] = Field(default_factory=list)

    @model_validator(mode="after")
    def validate_credential_project_ids(self) -> "ProjectRiskContext":
        mismatched_ids = [
            credential.credentialId
            for credential in self.credentials
            if credential.projectId != self.projectId
        ]
        if mismatched_ids:
            raise ValueError(
                "credential projectId must match context projectId: "
                f"{mismatched_ids}"
            )
        return self


class RiskEvidence(RiskSchema):
    """One rule that contributed points to a Credential risk score."""

    rule: RiskRule
    scoreContribution: int = Field(ge=0, le=100)
    message: str = Field(min_length=1)


class CredentialRiskResult(RiskSchema):
    credentialId: int = Field(gt=0)
    title: str = Field(min_length=1, max_length=255)
    type: CredentialType
    provider: str | None = Field(default=None, max_length=100)
    status: CredentialStatus
    riskScore: int = Field(ge=0, le=100)
    riskLevel: RiskLevel
    evidence: list[RiskEvidence] = Field(default_factory=list)


class RiskRecommendation(RiskSchema):
    priority: int = Field(gt=0)
    credentialId: int = Field(gt=0)
    severity: RiskLevel
    title: str = Field(min_length=1)
    evidence: list[str] = Field(default_factory=list)
    actions: list[str] = Field(default_factory=list)


class ProjectRiskResponse(RiskSchema):
    projectId: int = Field(gt=0)
    analyzedAt: datetime
    cached: bool = False
    overallRiskLevel: RiskLevel
    risks: list[CredentialRiskResult] = Field(default_factory=list)
    recommendations: list[RiskRecommendation] = Field(default_factory=list)
