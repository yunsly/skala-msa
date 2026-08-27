from collections.abc import Iterable
from datetime import datetime, timedelta, timezone

from app.model.risk_schemas import CredentialRiskInput, ProjectRiskContext
from app.provider.risk_context_provider import ProjectRiskContextNotFoundError


class FakeRiskContextProvider:
    """In-memory provider used until the inter-service contract is available."""

    def __init__(
        self,
        contexts: Iterable[ProjectRiskContext] | None = None,
    ):
        self._contexts = {
            context.projectId: context.model_copy(deep=True)
            for context in (contexts or [])
        }

    async def get_project_context(
        self,
        project_id: int,
        access_token: str | None = None,
    ) -> ProjectRiskContext:
        context = self._contexts.get(project_id)
        if context is None:
            raise ProjectRiskContextNotFoundError(project_id)
        return context.model_copy(deep=True)

    def set_context(self, context: ProjectRiskContext) -> None:
        self._contexts[context.projectId] = context.model_copy(deep=True)


def create_demo_risk_context() -> ProjectRiskContext:
    """Return non-sensitive sample metadata for local API verification."""
    now = datetime.now(timezone.utc)
    return ProjectRiskContext(
        projectId=1,
        credentials=[
            CredentialRiskInput(
                credentialId=10,
                projectId=1,
                title="GitHub Deploy Token",
                type="API_KEY",
                provider="GitHub",
                status="ACTIVE",
                expiresAt=now + timedelta(days=5),
                lastRotatedAt=now - timedelta(days=185),
                activeMemberCount=7,
                deniedAccessCount30d=3,
            ),
            CredentialRiskInput(
                credentialId=20,
                projectId=1,
                title="Datadog Pro Plan",
                type="SUBSCRIPTION_PLAN",
                provider="Datadog",
                status="ACTIVE",
                renewalAt=now + timedelta(days=20),
                activeMemberCount=4,
                deniedAccessCount30d=0,
            ),
        ],
    )


fake_risk_context_provider = FakeRiskContextProvider([create_demo_risk_context()])
