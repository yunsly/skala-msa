import unittest

from app.model.risk_schemas import CredentialRiskResult, RiskEvidence
from app.service.risk_report_service import RiskReportService


class RiskReportServiceTest(unittest.TestCase):
    def setUp(self):
        self.service = RiskReportService()

    @staticmethod
    def _result(
        credential_id: int,
        score: int,
        level: str,
        rule: str | None = None,
    ) -> CredentialRiskResult:
        evidence = []
        if rule is not None:
            evidence.append(
                RiskEvidence(
                    rule=rule,
                    scoreContribution=score,
                    message=f"credential-{credential_id} evidence",
                )
            )
        return CredentialRiskResult(
            credentialId=credential_id,
            title=f"credential-{credential_id}",
            type="API_KEY",
            provider="GitHub",
            status="ACTIVE",
            riskScore=score,
            riskLevel=level,
            evidence=evidence,
        )

    def test_sorts_by_score_then_credential_id_and_assigns_priority(self):
        results = [
            self._result(20, 40, "MEDIUM", "API_KEY_EXPIRING_WITHIN_7_DAYS"),
            self._result(10, 80, "CRITICAL", "API_KEY_EXPIRED"),
            self._result(5, 40, "MEDIUM", "API_KEY_EXPIRING_WITHIN_7_DAYS"),
        ]

        recommendations = self.service.create_recommendations(results)

        self.assertEqual(
            [item.credentialId for item in recommendations],
            [10, 5, 20],
        )
        self.assertEqual(
            [item.priority for item in recommendations],
            [1, 2, 3],
        )

    def test_excludes_zero_score_credentials(self):
        recommendations = self.service.create_recommendations(
            [self._result(10, 0, "LOW")]
        )

        self.assertEqual(recommendations, [])

    def test_builds_title_evidence_and_actions_from_rules(self):
        result = self._result(
            10,
            100,
            "CRITICAL",
            "API_KEY_EXPIRED",
        )

        recommendation = self.service.create_recommendations([result])[0]

        self.assertIn("만료된 API Key", recommendation.title)
        self.assertEqual(recommendation.evidence, ["credential-10 evidence"])
        self.assertGreaterEqual(len(recommendation.actions), 1)


if __name__ == "__main__":
    unittest.main()
