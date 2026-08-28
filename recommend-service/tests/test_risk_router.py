import unittest
from datetime import datetime, timedelta, timezone

from fastapi import FastAPI
from fastapi.testclient import TestClient

from app.config.security import verify_token
from app.model.risk_schemas import CredentialRiskInput, ProjectRiskContext
from app.provider.fake_risk_context_provider import FakeRiskContextProvider
from app.provider.risk_context_provider import RiskContextProviderError
from app.router.risk_router import get_project_risk_service, router
from app.service.project_risk_service import ProjectRiskService


class CountingRiskContextProvider(FakeRiskContextProvider):
    def __init__(self, contexts=None):
        super().__init__(contexts)
        self.call_count = 0

    async def get_project_context(
        self,
        project_id: int,
        access_token: str | None = None,
    ) -> ProjectRiskContext:
        self.call_count += 1
        return await super().get_project_context(project_id, access_token)


class FailingRiskContextProvider:
    async def get_project_context(
        self,
        project_id: int,
        access_token: str | None = None,
    ) -> ProjectRiskContext:
        raise RiskContextProviderError("upstream unavailable")


class RiskRouterTest(unittest.TestCase):
    NOW = datetime.now(timezone.utc)

    def _context(
        self,
        project_id: int = 1,
        expired: bool = False,
    ) -> ProjectRiskContext:
        expiration = self.NOW + timedelta(days=7)
        if expired:
            expiration = self.NOW - timedelta(days=1)
        return ProjectRiskContext(
            projectId=project_id,
            credentials=[
                CredentialRiskInput(
                    credentialId=10,
                    projectId=project_id,
                    title="GitHub Deploy Token",
                    type="API_KEY",
                    provider="GitHub",
                    status="ACTIVE",
                    expiresAt=expiration,
                    activeMemberCount=0,
                    deniedAccessCount30d=0,
                )
            ],
        )

    def _client(self, service: ProjectRiskService) -> TestClient:
        app = FastAPI()
        app.include_router(router)

        async def authenticated_user():
            return {"sub": "risk-test-user"}

        app.dependency_overrides[verify_token] = authenticated_user
        app.dependency_overrides[get_project_risk_service] = lambda: service
        client = TestClient(app)
        client.headers["Authorization"] = "Bearer risk-test-token"
        return client

    def test_get_risks_returns_current_analysis(self):
        provider = CountingRiskContextProvider([self._context()])
        client = self._client(ProjectRiskService(provider))

        response = client.get("/api/recommend/projects/1/risks")

        self.assertEqual(response.status_code, 200)
        body = response.json()
        self.assertEqual(body["projectId"], 1)
        self.assertFalse(body["cached"])
        self.assertEqual(body["risks"][0]["riskScore"], 40)
        self.assertEqual(body["risks"][0]["riskLevel"], "MEDIUM")
        self.assertEqual(body["recommendations"][0]["priority"], 1)
        self.assertEqual(provider.call_count, 1)

    def test_each_get_recalculates_from_latest_context(self):
        provider = CountingRiskContextProvider([self._context()])
        client = self._client(ProjectRiskService(provider))

        first = client.get("/api/recommend/projects/1/risks")
        provider.set_context(self._context(expired=True))
        second = client.get("/api/recommend/projects/1/risks")

        self.assertEqual(first.json()["risks"][0]["riskScore"], 40)
        self.assertEqual(second.json()["risks"][0]["riskScore"], 100)
        self.assertEqual(provider.call_count, 2)

    def test_empty_project_returns_low_risk(self):
        context = ProjectRiskContext(projectId=1, credentials=[])
        client = self._client(
            ProjectRiskService(FakeRiskContextProvider([context]))
        )

        response = client.get("/api/recommend/projects/1/risks")

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.json()["overallRiskLevel"], "LOW")
        self.assertEqual(response.json()["risks"], [])
        self.assertEqual(response.json()["recommendations"], [])

    def test_missing_project_returns_404(self):
        client = self._client(ProjectRiskService(FakeRiskContextProvider()))

        response = client.get("/api/recommend/projects/999/risks")

        self.assertEqual(response.status_code, 404)

    def test_provider_failure_returns_503(self):
        client = self._client(ProjectRiskService(FailingRiskContextProvider()))

        response = client.get("/api/recommend/projects/1/risks")

        self.assertEqual(response.status_code, 503)

    def test_invalid_project_id_returns_422(self):
        client = self._client(ProjectRiskService(FakeRiskContextProvider()))

        response = client.get("/api/recommend/projects/0/risks")

        self.assertEqual(response.status_code, 422)

    def test_post_analysis_endpoint_is_not_exposed(self):
        provider = FakeRiskContextProvider([self._context()])
        client = self._client(ProjectRiskService(provider))

        response = client.post("/api/recommend/projects/1/risks")

        self.assertEqual(response.status_code, 405)


if __name__ == "__main__":
    unittest.main()
