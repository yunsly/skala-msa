import asyncio
import logging
from typing import Any

import httpx
from pydantic import ValidationError

from app.config.settings import settings
from app.model.risk_schemas import CredentialRiskInput, ProjectRiskContext
from app.provider.risk_context_provider import (
    ProjectRiskContextNotFoundError,
    RiskContextAccessDeniedError,
    RiskContextProviderError,
)

logger = logging.getLogger(__name__)


class HttpRiskContextProvider:
    """Collect sanitized Credential metadata and audit statistics over REST."""

    def __init__(
        self,
        course_service_url: str,
        payment_service_url: str,
        timeout_seconds: float = 5.0,
        transport: httpx.AsyncBaseTransport | None = None,
    ):
        self.course_service_url = course_service_url.rstrip("/")
        self.payment_service_url = payment_service_url.rstrip("/")
        self.timeout_seconds = timeout_seconds
        self.transport = transport

    async def get_project_context(
        self,
        project_id: int,
        access_token: str | None = None,
    ) -> ProjectRiskContext:
        if not access_token:
            raise RiskContextAccessDeniedError(
                "프로젝트 자산 조회에 필요한 인증 토큰이 없습니다."
            )

        headers = {"Authorization": f"Bearer {access_token}"}
        try:
            async with httpx.AsyncClient(
                timeout=self.timeout_seconds,
                transport=self.transport,
            ) as client:
                credentials = await self._get_credentials(
                    client,
                    project_id,
                    headers,
                )
                denied_counts = await asyncio.gather(
                    *(
                        self._get_denied_access_count(
                            client,
                            credential["id"],
                            headers,
                        )
                        for credential in credentials
                    )
                )
        except (
            ProjectRiskContextNotFoundError,
            RiskContextAccessDeniedError,
            RiskContextProviderError,
        ):
            raise
        except (httpx.HTTPError, ValidationError, KeyError, TypeError, ValueError) as error:
            logger.warning(
                "[RiskContextProvider] 위험 분석 데이터 수집 실패 - projectId: %s, error: %s",
                project_id,
                error,
            )
            raise RiskContextProviderError(
                "프로젝트 위험 분석 데이터 형식이 올바르지 않습니다."
            ) from error

        try:
            inputs = [
                self._to_risk_input(credential, denied_count)
                for credential, denied_count in zip(
                    credentials,
                    denied_counts,
                    strict=True,
                )
            ]
            return ProjectRiskContext(projectId=project_id, credentials=inputs)
        except (ValidationError, KeyError, TypeError, ValueError) as error:
            logger.warning(
                "[RiskContextProvider] 위험 분석 데이터 변환 실패 - projectId: %s, error: %s",
                project_id,
                error,
            )
            raise RiskContextProviderError(
                "프로젝트 위험 분석 데이터 형식이 올바르지 않습니다."
            ) from error

    async def _get_credentials(
        self,
        client: httpx.AsyncClient,
        project_id: int,
        headers: dict[str, str],
    ) -> list[dict[str, Any]]:
        response = await client.get(
            f"{self.course_service_url}/api/courses",
            params={"projectId": project_id},
            headers=headers,
        )
        if response.status_code == 404:
            raise ProjectRiskContextNotFoundError(project_id)
        if response.status_code in (401, 403):
            raise RiskContextAccessDeniedError(
                "프로젝트 위험 분석 데이터를 조회할 권한이 없습니다."
            )
        response.raise_for_status()

        payload = response.json()
        credentials = payload.get("data") if isinstance(payload, dict) else None
        if not isinstance(credentials, list):
            raise RiskContextProviderError(
                "자산 목록 응답에 data 배열이 없습니다."
            )
        return credentials

    async def _get_denied_access_count(
        self,
        client: httpx.AsyncClient,
        credential_id: int,
        headers: dict[str, str],
    ) -> int:
        response = await client.get(
            (
                f"{self.payment_service_url}/api/payments/internal/audit-logs/"
                f"credentials/{credential_id}/denied-count"
            ),
            params={"days": 30},
            headers=headers,
        )
        if response.status_code in (401, 403):
            raise RiskContextAccessDeniedError(
                "접근 거절 통계를 조회할 권한이 없습니다."
            )
        response.raise_for_status()

        payload = response.json()
        data = payload.get("data") if isinstance(payload, dict) else None
        if not isinstance(data, dict):
            raise RiskContextProviderError(
                "접근 거절 통계 응답에 data 객체가 없습니다."
            )
        return int(data["deniedAccessCount"])

    def _to_risk_input(
        self,
        credential: dict[str, Any],
        denied_access_count: int,
    ) -> CredentialRiskInput:
        return CredentialRiskInput(
            credentialId=credential["id"],
            projectId=credential["projectId"],
            title=credential["title"],
            type=credential["category"],
            provider=credential.get("provider"),
            status=credential["status"],
            expiresAt=credential.get("expiresAt"),
            renewalAt=credential.get("renewalAt"),
            lastRotatedAt=credential.get("lastRotatedAt"),
            activeMemberCount=credential.get("activeMemberCount", 0),
            deniedAccessCount30d=denied_access_count,
        )


http_risk_context_provider = HttpRiskContextProvider(
    course_service_url=settings.course_service_url,
    payment_service_url=settings.payment_service_url,
    timeout_seconds=settings.risk_provider_timeout_seconds,
)
