import logging

from fastapi import APIRouter, Depends, HTTPException, Path, status
from fastapi.security import HTTPAuthorizationCredentials

from app.config.security import security, verify_token
from app.model.risk_schemas import ProjectRiskResponse
from app.provider.risk_context_provider import (
    ProjectRiskContextNotFoundError,
    RiskContextAccessDeniedError,
    RiskContextProviderError,
)
from app.service.project_risk_service import (
    ProjectRiskService,
    project_risk_service,
)

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/recommend", tags=["credential-risk"])


def get_project_risk_service() -> ProjectRiskService:
    return project_risk_service


@router.get(
    "/projects/{projectId}/risks",
    response_model=ProjectRiskResponse,
    summary="프로젝트 Credential 위험도 조회",
)
async def get_project_risks(
    projectId: int = Path(gt=0),
    credentials: HTTPAuthorizationCredentials = Depends(security),
    token_payload: dict = Depends(verify_token),
    service: ProjectRiskService = Depends(get_project_risk_service),
) -> ProjectRiskResponse:
    """Recalculate and return the current project Credential risks."""
    logger.info(
        "[RiskRouter] 프로젝트 위험 분석 요청 - projectId: %s, subject: %s",
        projectId,
        token_payload.get("sub"),
    )
    try:
        return await service.get_project_risks(
            projectId,
            access_token=credentials.credentials,
        )
    except ProjectRiskContextNotFoundError as error:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail=str(error),
        ) from error
    except RiskContextAccessDeniedError as error:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=str(error),
        ) from error
    except RiskContextProviderError as error:
        logger.exception(
            "[RiskRouter] 프로젝트 위험 데이터 조회 실패 - projectId: %s",
            projectId,
        )
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="프로젝트 위험 분석 데이터를 조회할 수 없습니다.",
        ) from error
