from typing import Protocol

from app.model.risk_schemas import ProjectRiskContext


class RiskContextProviderError(RuntimeError):
    """Base error raised while collecting project risk context."""


class RiskContextAccessDeniedError(RiskContextProviderError):
    """Raised when the caller cannot access the requested project."""


class ProjectRiskContextNotFoundError(RiskContextProviderError):
    def __init__(self, project_id: int):
        message = (
            "프로젝트 위험 분석 데이터를 찾을 수 없습니다: "
            f"{project_id}"
        )
        super().__init__(message)
        self.project_id = project_id


class RiskContextProvider(Protocol):
    async def get_project_context(
        self,
        project_id: int,
        access_token: str | None = None,
    ) -> ProjectRiskContext:
        """Return sanitized metadata and statistics for one project."""
        ...
