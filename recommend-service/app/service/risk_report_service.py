from app.model.risk_schemas import (
    CredentialRiskResult,
    RiskRecommendation,
    RiskRule,
)


TITLE_BY_RULE: dict[RiskRule, str] = {
    RiskRule.API_KEY_EXPIRED: "만료된 API Key 즉시 교체",
    RiskRule.API_KEY_EXPIRING_WITHIN_7_DAYS: "API Key 긴급 회전 준비",
    RiskRule.API_KEY_EXPIRING_WITHIN_30_DAYS: "API Key 회전 일정 수립",
    RiskRule.API_KEY_NOT_ROTATED_OVER_180_DAYS: "장기 미회전 API Key 교체",
    RiskRule.API_KEY_NOT_ROTATED_OVER_90_DAYS: "API Key 회전 검토",
    RiskRule.ACTIVE_MEMBERS_AT_LEAST_5: "Credential 접근 사용자 검토",
    RiskRule.DENIED_ACCESS_AT_LEAST_3_IN_30_DAYS: "반복 접근 거절 조사",
    RiskRule.SUBSCRIPTION_RENEWING_WITHIN_7_DAYS: "구독 Plan 갱신 여부 즉시 결정",
    RiskRule.SUBSCRIPTION_RENEWING_WITHIN_30_DAYS: "구독 Plan 갱신 검토",
}

ACTION_BY_RULE: dict[RiskRule, tuple[str, ...]] = {
    RiskRule.API_KEY_EXPIRED: (
        "새 API Key를 발급하고 Credential을 회전합니다.",
        "만료된 API Key의 사용처와 접근 권한을 확인합니다.",
    ),
    RiskRule.API_KEY_EXPIRING_WITHIN_7_DAYS: (
        "만료 전에 새 API Key를 발급합니다.",
        "교체 일정과 영향받는 시스템을 확인합니다.",
    ),
    RiskRule.API_KEY_EXPIRING_WITHIN_30_DAYS: (
        "API Key 회전 담당자와 일정을 지정합니다.",
    ),
    RiskRule.API_KEY_NOT_ROTATED_OVER_180_DAYS: (
        "API Key를 즉시 회전하고 기존 Key를 폐기합니다.",
    ),
    RiskRule.API_KEY_NOT_ROTATED_OVER_90_DAYS: (
        "API Key 사용처를 확인하고 회전 일정을 수립합니다.",
    ),
    RiskRule.ACTIVE_MEMBERS_AT_LEAST_5: (
        "현재 활성 멤버의 Credential 접근 필요성을 재검토합니다.",
    ),
    RiskRule.DENIED_ACCESS_AT_LEAST_3_IN_30_DAYS: (
        "최근 접근 거절 감사 로그와 요청 사용자를 확인합니다.",
    ),
    RiskRule.SUBSCRIPTION_RENEWING_WITHIN_7_DAYS: (
        "구독 사용 현황을 확인하고 갱신 또는 해지를 결정합니다.",
    ),
    RiskRule.SUBSCRIPTION_RENEWING_WITHIN_30_DAYS: (
        "구독 Plan의 담당자와 갱신 일정을 확인합니다.",
    ),
}


class RiskReportService:
    def create_recommendations(
        self,
        results: list[CredentialRiskResult],
    ) -> list[RiskRecommendation]:
        risky_results = sorted(
            (result for result in results if result.riskScore > 0),
            key=lambda result: (-result.riskScore, result.credentialId),
        )

        return [
            self._create_recommendation(priority, result)
            for priority, result in enumerate(risky_results, start=1)
        ]

    def _create_recommendation(
        self,
        priority: int,
        result: CredentialRiskResult,
    ) -> RiskRecommendation:
        primary_rule = max(
            result.evidence,
            key=lambda item: item.scoreContribution,
        ).rule
        return RiskRecommendation(
            priority=priority,
            credentialId=result.credentialId,
            severity=result.riskLevel,
            title=f"{result.title}: {TITLE_BY_RULE[primary_rule]}",
            evidence=[item.message for item in result.evidence],
            actions=self._actions_for(result),
        )

    @staticmethod
    def _actions_for(result: CredentialRiskResult) -> list[str]:
        actions: list[str] = []
        for evidence in result.evidence:
            for action in ACTION_BY_RULE[evidence.rule]:
                if action not in actions:
                    actions.append(action)
        return actions


risk_report_service = RiskReportService()
