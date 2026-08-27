import unittest

import httpx

from app.provider.http_risk_context_provider import HttpRiskContextProvider
from app.provider.risk_context_provider import (
    RiskContextAccessDeniedError,
    RiskContextProviderError,
)


class HttpRiskContextProviderTest(unittest.IsolatedAsyncioTestCase):
    def _provider(self, handler) -> HttpRiskContextProvider:
        return HttpRiskContextProvider(
            course_service_url="http://course-service:8082",
            payment_service_url="http://payment-service:8084",
            transport=httpx.MockTransport(handler),
        )

    async def test_collects_metadata_and_denied_counts_without_secret(self):
        requested_credential_ids: list[int] = []

        async def handler(request: httpx.Request) -> httpx.Response:
            self.assertEqual(
                request.headers["Authorization"],
                "Bearer user-token",
            )
            if request.url.path == "/api/courses":
                self.assertEqual(request.url.params["projectId"], "1")
                return httpx.Response(
                    200,
                    json={
                        "success": True,
                        "data": [
                            {
                                "id": 10,
                                "projectId": 1,
                                "title": "배포용 키",
                                "category": "API_KEY",
                                "provider": "GitHub",
                                "status": "ACTIVE",
                                "expiresAt": "2026-09-01T00:00:00",
                                "renewalAt": None,
                                "lastRotatedAt": "2026-01-01T00:00:00",
                                "activeMemberCount": 7,
                                "secretValue": "응답에 포함되더라도 전달하면 안 됨",
                            },
                            {
                                "id": 20,
                                "projectId": 1,
                                "title": "모니터링 요금제",
                                "category": "SUBSCRIPTION_PLAN",
                                "provider": "Datadog",
                                "status": "ACTIVE",
                                "renewalAt": "2026-09-20T00:00:00",
                                "activeMemberCount": 7,
                            },
                        ],
                    },
                )

            credential_id = int(request.url.path.split("/")[-2])
            requested_credential_ids.append(credential_id)
            self.assertEqual(request.url.params["days"], "30")
            return httpx.Response(
                200,
                json={
                    "success": True,
                    "data": {
                        "credentialId": credential_id,
                        "periodDays": 30,
                        "deniedAccessCount": 3 if credential_id == 10 else 0,
                    },
                },
            )

        context = await self._provider(handler).get_project_context(
            1,
            "user-token",
        )

        self.assertEqual(context.projectId, 1)
        self.assertEqual(len(context.credentials), 2)
        self.assertEqual(context.credentials[0].activeMemberCount, 7)
        self.assertEqual(context.credentials[0].deniedAccessCount30d, 3)
        self.assertFalse(hasattr(context.credentials[0], "secretValue"))
        self.assertCountEqual(requested_credential_ids, [10, 20])

    async def test_rejects_missing_access_token(self):
        async def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json={"data": []})

        with self.assertRaises(RiskContextAccessDeniedError):
            await self._provider(handler).get_project_context(1)

    async def test_maps_course_access_denial(self):
        async def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(403)

        with self.assertRaises(RiskContextAccessDeniedError):
            await self._provider(handler).get_project_context(1, "user-token")

    async def test_rejects_invalid_upstream_payload(self):
        async def handler(request: httpx.Request) -> httpx.Response:
            return httpx.Response(200, json={"success": True})

        with self.assertRaises(RiskContextProviderError):
            await self._provider(handler).get_project_context(1, "user-token")

    async def test_rejects_credential_from_another_project(self):
        async def handler(request: httpx.Request) -> httpx.Response:
            if request.url.path == "/api/courses":
                return httpx.Response(
                    200,
                    json={
                        "data": [
                            {
                                "id": 10,
                                "projectId": 2,
                                "title": "잘못 연결된 키",
                                "category": "API_KEY",
                                "status": "ACTIVE",
                            }
                        ]
                    },
                )
            return httpx.Response(
                200,
                json={"data": {"deniedAccessCount": 0}},
            )

        with self.assertRaises(RiskContextProviderError):
            await self._provider(handler).get_project_context(1, "user-token")


if __name__ == "__main__":
    unittest.main()
