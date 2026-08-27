from datetime import datetime, timezone

from app.model.risk_schemas import (
    CredentialRiskInput,
    CredentialRiskResult,
    CredentialStatus,
    CredentialType,
    ProjectRiskContext,
    RiskEvidence,
    RiskLevel,
    RiskRule,
)
from app.service.risk_policy import DEFAULT_RISK_POLICY, RiskPolicy


SECONDS_PER_DAY = 24 * 60 * 60
TERMINAL_STATUSES = {CredentialStatus.INACTIVE, CredentialStatus.REVOKED}


class RiskAnalysisService:
    def __init__(self, policy: RiskPolicy = DEFAULT_RISK_POLICY):
        self.policy = policy

    def analyze_project(
        self,
        context: ProjectRiskContext,
        analyzed_at: datetime | None = None,
    ) -> list[CredentialRiskResult]:
        reference_time = self._normalize_datetime(
            analyzed_at or datetime.now(timezone.utc)
        )
        return [
            self.analyze_credential(credential, reference_time)
            for credential in context.credentials
        ]

    def analyze_credential(
        self,
        credential: CredentialRiskInput,
        analyzed_at: datetime | None = None,
    ) -> CredentialRiskResult:
        reference_time = self._normalize_datetime(
            analyzed_at or datetime.now(timezone.utc)
        )
        evidence: list[RiskEvidence] = []

        if credential.status not in TERMINAL_STATUSES:
            if credential.type == CredentialType.API_KEY:
                self._evaluate_api_key_dates(credential, reference_time, evidence)
            elif credential.type == CredentialType.SUBSCRIPTION_PLAN:
                self._evaluate_subscription_dates(
                    credential,
                    reference_time,
                    evidence,
                )

            if credential.activeMemberCount >= self.policy.active_member_threshold:
                evidence.append(
                    RiskEvidence(
                        rule=RiskRule.ACTIVE_MEMBERS_AT_LEAST_5,
                        scoreContribution=self.policy.active_member_score,
                        message=(
                            "활성 프로젝트 멤버가 "
                            f"{credential.activeMemberCount}명입니다."
                        ),
                    )
                )

            if (
                credential.deniedAccessCount30d
                >= self.policy.denied_access_threshold_30d
            ):
                evidence.append(
                    RiskEvidence(
                        rule=RiskRule.DENIED_ACCESS_AT_LEAST_3_IN_30_DAYS,
                        scoreContribution=self.policy.denied_access_score,
                        message=(
                            "최근 30일 동안 접근 거절이 "
                            f"{credential.deniedAccessCount30d}회 발생했습니다."
                        ),
                    )
                )

        risk_score = min(
            sum(item.scoreContribution for item in evidence),
            self.policy.maximum_score,
        )
        return CredentialRiskResult(
            credentialId=credential.credentialId,
            title=credential.title,
            type=credential.type,
            provider=credential.provider,
            status=credential.status,
            riskScore=risk_score,
            riskLevel=self.level_for_score(risk_score),
            evidence=evidence,
        )

    def level_for_score(self, score: int) -> RiskLevel:
        if score >= self.policy.critical_score:
            return RiskLevel.CRITICAL
        if score >= self.policy.high_score:
            return RiskLevel.HIGH
        if score >= self.policy.medium_score:
            return RiskLevel.MEDIUM
        return RiskLevel.LOW

    def _evaluate_api_key_dates(
        self,
        credential: CredentialRiskInput,
        analyzed_at: datetime,
        evidence: list[RiskEvidence],
    ) -> None:
        if credential.status == CredentialStatus.EXPIRED:
            self._append_expired_evidence(evidence)
        elif credential.expiresAt is not None:
            seconds_until_expiration = (
                self._normalize_datetime(credential.expiresAt) - analyzed_at
            ).total_seconds()
            if seconds_until_expiration <= 0:
                self._append_expired_evidence(evidence)
            elif seconds_until_expiration <= (
                self.policy.expiration_urgent_days * SECONDS_PER_DAY
            ):
                evidence.append(
                    RiskEvidence(
                        rule=RiskRule.API_KEY_EXPIRING_WITHIN_7_DAYS,
                        scoreContribution=self.policy.expiration_urgent_score,
                        message=(
                            "API Key 만료일까지 "
                            f"{self._remaining_days(seconds_until_expiration)}일 "
                            "남았습니다."
                        ),
                    )
                )
            elif seconds_until_expiration <= (
                self.policy.expiration_warning_days * SECONDS_PER_DAY
            ):
                evidence.append(
                    RiskEvidence(
                        rule=RiskRule.API_KEY_EXPIRING_WITHIN_30_DAYS,
                        scoreContribution=self.policy.expiration_warning_score,
                        message=(
                            "API Key 만료일까지 "
                            f"{self._remaining_days(seconds_until_expiration)}일 "
                            "남았습니다."
                        ),
                    )
                )

        if credential.lastRotatedAt is None:
            return

        seconds_since_rotation = (
            analyzed_at - self._normalize_datetime(credential.lastRotatedAt)
        ).total_seconds()
        if seconds_since_rotation > (
            self.policy.rotation_urgent_days * SECONDS_PER_DAY
        ):
            evidence.append(
                RiskEvidence(
                    rule=RiskRule.API_KEY_NOT_ROTATED_OVER_180_DAYS,
                    scoreContribution=self.policy.rotation_urgent_score,
                    message=(
                        "마지막 API Key 회전 후 "
                        f"{self._elapsed_days(seconds_since_rotation)}일 "
                        "경과했습니다."
                    ),
                )
            )
        elif seconds_since_rotation > (
            self.policy.rotation_warning_days * SECONDS_PER_DAY
        ):
            evidence.append(
                RiskEvidence(
                    rule=RiskRule.API_KEY_NOT_ROTATED_OVER_90_DAYS,
                    scoreContribution=self.policy.rotation_warning_score,
                    message=(
                        "마지막 API Key 회전 후 "
                        f"{self._elapsed_days(seconds_since_rotation)}일 "
                        "경과했습니다."
                    ),
                )
            )

    def _evaluate_subscription_dates(
        self,
        credential: CredentialRiskInput,
        analyzed_at: datetime,
        evidence: list[RiskEvidence],
    ) -> None:
        if credential.renewalAt is None:
            return

        seconds_until_renewal = (
            self._normalize_datetime(credential.renewalAt) - analyzed_at
        ).total_seconds()
        if seconds_until_renewal <= (
            self.policy.renewal_urgent_days * SECONDS_PER_DAY
        ):
            evidence.append(
                RiskEvidence(
                    rule=RiskRule.SUBSCRIPTION_RENEWING_WITHIN_7_DAYS,
                    scoreContribution=self.policy.renewal_urgent_score,
                    message=(
                        "구독 Plan 갱신일까지 "
                        f"{self._remaining_days(seconds_until_renewal)}일 "
                        "남았습니다."
                    ),
                )
            )
        elif seconds_until_renewal <= (
            self.policy.renewal_warning_days * SECONDS_PER_DAY
        ):
            evidence.append(
                RiskEvidence(
                    rule=RiskRule.SUBSCRIPTION_RENEWING_WITHIN_30_DAYS,
                    scoreContribution=self.policy.renewal_warning_score,
                    message=(
                        "구독 Plan 갱신일까지 "
                        f"{self._remaining_days(seconds_until_renewal)}일 "
                        "남았습니다."
                    ),
                )
            )

    def _append_expired_evidence(self, evidence: list[RiskEvidence]) -> None:
        evidence.append(
            RiskEvidence(
                rule=RiskRule.API_KEY_EXPIRED,
                scoreContribution=self.policy.expired_score,
                message="API Key가 이미 만료되었습니다.",
            )
        )

    @staticmethod
    def _normalize_datetime(value: datetime) -> datetime:
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)

    @staticmethod
    def _remaining_days(seconds: float) -> int:
        return max(0, int(seconds // SECONDS_PER_DAY))

    @staticmethod
    def _elapsed_days(seconds: float) -> int:
        return max(0, int(seconds // SECONDS_PER_DAY))


risk_analysis_service = RiskAnalysisService()
