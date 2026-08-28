import unittest

from pydantic import ValidationError

from app.model.risk_schemas import CredentialRiskInput, ProjectRiskContext


class RiskSchemasTest(unittest.TestCase):
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

    def test_parses_whitelisted_credential_metadata(self):
        credential = self._credential(expiresAt="2026-09-01T00:00:00Z")

        self.assertEqual(credential.credentialId, 10)
        self.assertIsNotNone(credential.expiresAt)

    def test_rejects_secret_or_unknown_fields(self):
        with self.assertRaises(ValidationError):
            self._credential(secretValue="must-not-enter-risk-context")

    def test_rejects_negative_counts(self):
        with self.assertRaises(ValidationError):
            self._credential(deniedAccessCount30d=-1)

    def test_rejects_mismatched_project_ids(self):
        credential = self._credential(projectId=2)

        with self.assertRaises(ValidationError):
            ProjectRiskContext(projectId=1, credentials=[credential])


if __name__ == "__main__":
    unittest.main()
