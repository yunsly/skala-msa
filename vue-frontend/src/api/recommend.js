import api from './index.js'
import { courseApi } from './course.js'

// recommend-service는 "AI 추천"이 아니라 명시적 규칙(if-then 가중치 합산) 기반
// 위험도 엔진이다 — 화면 문구에도 "AI"라는 표현을 쓰지 않는다.

// 백엔드가 evidence/recommendedAction을 안 내려줄 수 있어 방어적으로 클라이언트에서
// 근거·우선순위 조치 문구를 만든다. 백엔드 값이 있으면 그걸 우선한다.
function buildEvidence(a) {
  if (Array.isArray(a.evidence) && a.evidence.length > 0) {
    return a.evidence
      .map((item) => typeof item === 'string' ? item : item.message)
      .filter(Boolean)
      .join(' · ')
  }
  if (typeof a.evidence === 'string' && a.evidence) return a.evidence
  if (a.isExpiringSoon && a.expiresAt) return `만료 임박 (${a.expiresAt.slice(0, 10)})`
  if (a.needsRotation && a.lastRotatedAt) return `회전 주기 경과 (마지막 회전 ${a.lastRotatedAt.slice(0, 10)})`
  if (a.needsRotation) return '회전 주기 경과'
  if (a.isRenewalDue) return '구독 갱신 임박'
  return `위험 점수 ${a.riskScore}점`
}

function buildAction(a) {
  if (Array.isArray(a.recommendedAction)) return a.recommendedAction.join(' · ')
  if (a.recommendedAction) return a.recommendedAction
  if (a.riskLevel === 'CRITICAL') return 'Vault 동적 시크릿으로 즉시 마이그레이션 권장'
  if (a.needsRotation) return '지금 회전(rotate)하고 소비자에게 통지'
  if (a.isExpiringSoon) return '만료 전 갱신 또는 대체 자산으로 전환'
  if (a.isRenewalDue) return '구독 갱신 여부를 프로젝트 리더와 확인'
  return '다음 정기 점검 시 검토'
}

export const recommendApi = {
  analyzeProject(projectId) {
    return api.get(`/api/recommend/projects/${projectId}/risks`)
  },

  // 전사 통합 위험도 리포트.
  //
  // ⚠️ 백엔드에 전사 통합 엔드포인트(예: GET /api/recommend/analyze)가 아직 없다.
  // 그래서 여기서는 프로젝트 목록을 받아온 뒤 프로젝트마다 analyzeProject를
  // 순차/병렬 호출해서 클라이언트에서 합산한다. 프로젝트 수가 많아지면 호출 수가
  // N번으로 늘어나 화면 로딩이 느려지므로 임시방편이다 — 백엔드에 전사 통합
  // 엔드포인트 신설을 요청해야 한다. 엔드포인트가 생기면 이 함수 내부만 교체하면
  // 되도록, 컴포넌트에는 "전사 위험도 데이터를 다오"라는 이 함수 하나만 노출한다.
  //
  // recommend-service 장애 대응: 프로젝트별 analyze 호출을 각각 try/catch로 감싸서,
  // 일부(혹은 전부) 실패해도 나머지 결과로 화면이 계속 뜨게 한다(부분 응답 허용) —
  // 이슈 #19 "Recommend 서비스 실패가 핵심 화면을 막지 않아야 한다" 요구사항.
  async getEnterpriseRiskReport() {
    const projectsRes = await courseApi.getProjects()
    const projects = Array.isArray(projectsRes.data?.data)
      ? projectsRes.data.data
      : Array.isArray(projectsRes.data)
        ? projectsRes.data
        : []

    let failedProjectCount = 0
    let inScopeProjectCount = 0 // 내가 접근 가능한(=리포트 대상) 프로젝트 수

    const perProject = await Promise.all(
      projects.map(async (project) => {
        try {
          const res = await this.analyzeProject(project.id)
          inScopeProjectCount += 1
          const payload = res.data?.data ?? res.data
          const assets = Array.isArray(payload?.risks)
            ? payload.risks
            : Array.isArray(payload?.riskyAssets)
              ? payload.riskyAssets
            : Array.isArray(payload?.assets)
              ? payload.assets
              : Array.isArray(payload)
                ? payload
                : []

          const recommendations = new Map(
            (Array.isArray(payload?.recommendations) ? payload.recommendations : [])
              .map((item) => [String(item.credentialId), item])
          )

          return assets.map((a) => {
            const evidenceRules = new Set(
              (Array.isArray(a.evidence) ? a.evidence : [])
                .map((item) => item?.rule)
                .filter(Boolean)
            )
            const recommendation = recommendations.get(String(a.credentialId ?? a.id ?? a.assetId))
            const normalized = {
              id: a.credentialId ?? a.id ?? a.assetId,
              title: a.title ?? a.name,
              category: a.type ?? a.category,
              riskScore: a.riskScore ?? a.score ?? 0,
              riskLevel: a.riskLevel ?? a.grade ?? a.level,
              expiresAt: a.expiresAt ?? a.expiry ?? null,
              lastRotatedAt: a.lastRotatedAt ?? a.lastRotationAt ?? null,
              needsRotation: a.needsRotation ?? (
                evidenceRules.has('API_KEY_NOT_ROTATED_OVER_90_DAYS') ||
                evidenceRules.has('API_KEY_NOT_ROTATED_OVER_180_DAYS')
              ),
              isExpiringSoon: a.isExpiringSoon ?? (
                evidenceRules.has('API_KEY_EXPIRING_WITHIN_7_DAYS') ||
                evidenceRules.has('API_KEY_EXPIRING_WITHIN_30_DAYS')
              ),
              isRenewalDue: a.isRenewalDue ?? a.isSubscriptionRenewalDue ?? (
                evidenceRules.has('SUBSCRIPTION_RENEWING_WITHIN_7_DAYS') ||
                evidenceRules.has('SUBSCRIPTION_RENEWING_WITHIN_30_DAYS')
              ),
              projectId: project.id,
              projectName: project.name
            }
            normalized.evidence = buildEvidence({ ...normalized, evidence: a.evidence })
            normalized.recommendedAction = buildAction({
              ...normalized,
              recommendedAction: recommendation?.actions ?? a.recommendedAction
            })
            return normalized
          })
        } catch (e) {
          // 403/404 = 이 사용자가 접근 권한이 없는(=멤버가 아닌) 프로젝트 → 리포트 대상 아님, 조용히 스킵.
          const status = e.response?.status
          if (status === 403 || status === 404) return []
          inScopeProjectCount += 1
          console.error(`[recommendApi] 프로젝트 #${project.id} 위험도 분석 실패:`, e)
          failedProjectCount += 1
          return []
        }
      })
    )

    const allAssets = perProject.flat().sort((a, b) => (b.riskScore ?? 0) - (a.riskScore ?? 0))

    // 프로젝트별 위험 요약 — 프로젝트당 최고 위험 점수/등급과 CRITICAL·HIGH 자산 수.
    const byProject = new Map()
    for (const a of allAssets) {
      const entry = byProject.get(a.projectId) ?? {
        projectId: a.projectId,
        projectName: a.projectName,
        maxScore: 0,
        maxLevel: 'LOW',
        criticalOrHighCount: 0
      }
      if ((a.riskScore ?? 0) > entry.maxScore) {
        entry.maxScore = a.riskScore
        entry.maxLevel = a.riskLevel
      }
      if (a.riskLevel === 'CRITICAL' || a.riskLevel === 'HIGH') entry.criticalOrHighCount += 1
      byProject.set(a.projectId, entry)
    }
    const projectSummaries = [...byProject.values()].sort((a, b) => b.maxScore - a.maxScore)

    return {
      analyzedAt: new Date().toISOString(),
      totalProjects: inScopeProjectCount,
      failedProjectCount,
      criticalAssets: allAssets.filter((a) => a.riskLevel === 'CRITICAL'),
      highAssets: allAssets.filter((a) => a.riskLevel === 'HIGH'),
      allAssets,
      projectSummaries,
      expiringApiKeys: allAssets.filter((a) => a.category === 'API_KEY' && a.isExpiringSoon),
      rotationNeeded: allAssets.filter((a) => a.needsRotation),
      renewalDueSubscriptions: allAssets.filter((a) => a.category === 'SUBSCRIPTION_PLAN' && a.isRenewalDue)
    }
  }
}
