import unittest
from datetime import datetime, timedelta, timezone

from app.model.risk_schemas import (
    CredentialRiskInput,
    CredentialStatus,
    RiskLevel,
    RiskRule,
)
from app.service.risk_analysis_service import RiskAnalysisService


class RiskAnalysisServiceTest(unittest.TestCase):
    NOW = datetime(2026, 8, 27, 0, 0, tzinfo=timezone.utc)

    def setUp(self):
        self.service = RiskAnalysisService()

    def _credential(self, **overrides) -> CredentialRiskInput:
        values = {
            "credentialId": 10,
            "projectId": 1,
            "title": "GitHub Deploy Token",
            "type": "API_KEY",
            "provider": "GitHub",
            "status": "ACTIVE",
            "activeMemberCount": 0,
            "deniedAccessCount30d": 0,
        }
        values.update(overrides)
        return CredentialRiskInput(**values)

    def test_expiration_score_boundaries(self):
        cases = (
            (timedelta(days=7), 40, RiskLevel.MEDIUM),
            (timedelta(days=30), 20, RiskLevel.LOW),
            (timedelta(days=31), 0, RiskLevel.LOW),
        )

        for remaining, expected_score, expected_level in cases:
            with self.subTest(remaining=remaining):
                result = self.service.analyze_credential(
                    self._credential(expiresAt=self.NOW + remaining),
                    self.NOW,
                )
                self.assertEqual(result.riskScore, expected_score)
                self.assertEqual(result.riskLevel, expected_level)

    def test_expired_key_is_critical(self):
        result = self.service.analyze_credential(
            self._credential(expiresAt=self.NOW - timedelta(seconds=1)),
            self.NOW,
        )

        self.assertEqual(result.riskScore, 100)
        self.assertEqual(result.riskLevel, RiskLevel.CRITICAL)
        self.assertEqual(result.evidence[0].rule, RiskRule.API_KEY_EXPIRED)

    def test_expired_status_is_critical_without_expiration_date(self):
        result = self.service.analyze_credential(
            self._credential(status=CredentialStatus.EXPIRED),
            self.NOW,
        )

        self.assertEqual(result.riskScore, 100)

    def test_rotation_score_boundaries(self):
        cases = (
            (timedelta(days=90), 0),
            (timedelta(days=90, seconds=1), 15),
            (timedelta(days=180), 15),
            (timedelta(days=180, seconds=1), 25),
        )

        for elapsed, expected_score in cases:
            with self.subTest(elapsed=elapsed):
                result = self.service.analyze_credential(
                    self._credential(lastRotatedAt=self.NOW - elapsed),
                    self.NOW,
                )
                self.assertEqual(result.riskScore, expected_score)

    def test_subscription_renewal_rules_do_not_apply_api_key_rules(self):
        result = self.service.analyze_credential(
            self._credential(
                type="SUBSCRIPTION_PLAN",
                renewalAt=self.NOW + timedelta(days=7),
                expiresAt=self.NOW - timedelta(days=1),
                lastRotatedAt=self.NOW - timedelta(days=365),
            ),
            self.NOW,
        )

        self.assertEqual(result.riskScore, 40)
        self.assertEqual(
            result.evidence[0].rule,
            RiskRule.SUBSCRIPTION_RENEWING_WITHIN_7_DAYS,
        )

    def test_combined_score_is_capped_at_100(self):
        result = self.service.analyze_credential(
            self._credential(
                expiresAt=self.NOW - timedelta(days=1),
                lastRotatedAt=self.NOW - timedelta(days=365),
                activeMemberCount=7,
                deniedAccessCount30d=3,
            ),
            self.NOW,
        )

        self.assertEqual(result.riskScore, 100)
        self.assertEqual(result.riskLevel, RiskLevel.CRITICAL)
        self.assertEqual(len(result.evidence), 4)

    def test_inactive_and_revoked_credentials_have_no_risk(self):
        for status in (CredentialStatus.INACTIVE, CredentialStatus.REVOKED):
            with self.subTest(status=status):
                result = self.service.analyze_credential(
                    self._credential(
                        status=status,
                        expiresAt=self.NOW - timedelta(days=1),
                        activeMemberCount=10,
                        deniedAccessCount30d=10,
                    ),
                    self.NOW,
                )
                self.assertEqual(result.riskScore, 0)
                self.assertEqual(result.evidence, [])

    def test_risk_level_uses_only_the_final_score_mapping(self):
        cases = (
            (0, RiskLevel.LOW),
            (29, RiskLevel.LOW),
            (30, RiskLevel.MEDIUM),
            (59, RiskLevel.MEDIUM),
            (60, RiskLevel.HIGH),
            (79, RiskLevel.HIGH),
            (80, RiskLevel.CRITICAL),
            (100, RiskLevel.CRITICAL),
        )

        for score, expected_level in cases:
            with self.subTest(score=score):
                self.assertEqual(
                    self.service.level_for_score(score),
                    expected_level,
                )


if __name__ == "__main__":
    unittest.main()
