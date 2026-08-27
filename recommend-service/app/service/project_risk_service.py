from datetime import datetime, timezone

from app.model.risk_schemas import ProjectRiskResponse
from app.provider.http_risk_context_provider import http_risk_context_provider
from app.provider.risk_context_provider import RiskContextProvider
from app.service.risk_analysis_service import (
    RiskAnalysisService,
    risk_analysis_service,
)
from app.service.risk_report_service import RiskReportService, risk_report_service


class ProjectRiskService:
    def __init__(
        self,
        context_provider: RiskContextProvider,
        analysis_service: RiskAnalysisService = risk_analysis_service,
        report_service: RiskReportService = risk_report_service,
    ):
        self.context_provider = context_provider
        self.analysis_service = analysis_service
        self.report_service = report_service

    async def get_project_risks(
        self,
        project_id: int,
        analyzed_at: datetime | None = None,
        access_token: str | None = None,
    ) -> ProjectRiskResponse:
        reference_time = analyzed_at or datetime.now(timezone.utc)
        context = await self.context_provider.get_project_context(
            project_id,
            access_token,
        )
        risks = self.analysis_service.analyze_project(context, reference_time)
        recommendations = self.report_service.create_recommendations(risks)
        highest_score = max((risk.riskScore for risk in risks), default=0)

        return ProjectRiskResponse(
            projectId=project_id,
            analyzedAt=reference_time,
            cached=False,
            overallRiskLevel=self.analysis_service.level_for_score(highest_score),
            risks=risks,
            recommendations=recommendations,
        )


project_risk_service = ProjectRiskService(http_risk_context_provider)
