import api from './index.js'
import { courseApi } from './course.js'

// recommend-service는 "AI 추천"이 아니라 명시적 규칙(if-then 가중치 합산) 기반
// 위험도 엔진이다 — 화면 문구에도 "AI"라는 표현을 쓰지 않는다.
export const recommendApi = {
  analyzeProject(projectId) {
    return api.post(`/api/recommend/projects/${projectId}/analyze`)
  },

  // 전사 통합 위험도 리포트.
  //
  // ⚠️ 백엔드에 전사 통합 엔드포인트(예: GET /api/recommend/analyze)가 아직 없다.
  // 그래서 여기서는 프로젝트 목록을 받아온 뒤 프로젝트마다 analyzeProject를
  // 순차/병렬 호출해서 클라이언트에서 합산한다. 프로젝트 수가 많아지면 호출 수가
  // N번으로 늘어나 화면 로딩이 느려지므로 임시방편이다 — 백엔드에 전사 통합
  // 엔드포인트 신설을 요청해야 한다. 엔드포인트가 생기면 이 함수 내부만 교체하면
  // 되도록, 컴포넌트에는 "전사 위험도 데이터를 다오"라는 이 함수 하나만 노출한다.
  async getEnterpriseRiskReport() {
    const projectsRes = await courseApi.getProjects()
    const projects = Array.isArray(projectsRes.data?.data)
      ? projectsRes.data.data
      : Array.isArray(projectsRes.data)
        ? projectsRes.data
        : []

    const perProject = await Promise.all(
      projects.map(async (project) => {
        try {
          const res = await this.analyzeProject(project.id)
          const payload = res.data?.data ?? res.data
          const assets = Array.isArray(payload?.riskyAssets)
            ? payload.riskyAssets
            : Array.isArray(payload?.assets)
              ? payload.assets
              : Array.isArray(payload)
                ? payload
                : []

          return assets.map((a) => ({
            id: a.id ?? a.assetId,
            title: a.title ?? a.name,
            category: a.category,
            riskScore: a.riskScore ?? a.score ?? 0,
            riskLevel: a.riskLevel ?? a.grade ?? a.level,
            expiresAt: a.expiresAt ?? a.expiry ?? null,
            lastRotatedAt: a.lastRotatedAt ?? a.lastRotationAt ?? null,
            needsRotation: a.needsRotation ?? false,
            isExpiringSoon: a.isExpiringSoon ?? false,
            isRenewalDue: a.isRenewalDue ?? a.isSubscriptionRenewalDue ?? false,
            projectId: project.id,
            projectName: project.name
          }))
        } catch (e) {
          console.error(`[recommendApi] 프로젝트 #${project.id} 위험도 분석 실패:`, e)
          return []
        }
      })
    )

    const allAssets = perProject.flat().sort((a, b) => (b.riskScore ?? 0) - (a.riskScore ?? 0))

    return {
      totalProjects: projects.length,
      criticalAssets: allAssets.filter((a) => a.riskLevel === 'CRITICAL'),
      allAssets,
      expiringApiKeys: allAssets.filter((a) => a.category === 'API_KEY' && a.isExpiringSoon),
      rotationNeeded: allAssets.filter((a) => a.needsRotation),
      renewalDueSubscriptions: allAssets.filter((a) => a.category === 'SUBSCRIPTION_PLAN' && a.isRenewalDue)
    }
  }
}
