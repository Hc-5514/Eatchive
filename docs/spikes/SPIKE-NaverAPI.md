# SPIKE-NaverAPI – Naver vs Kakao Local Search Decision

## Goals
- Pick the primary place search/geocoding provider for MVP Phase 0.
- Document quotas, authentication, fallbacks, and monitoring hooks.
- Define `PlaceApiClient` contract + error catalog alignment.

## Summary of Findings
| Topic | Naver Search API (Local) | Kakao Local API |
| --- | --- | --- |
| Daily quota | 25,000 requests/day per app for Search API (covers local search & manual fallback). [citation] | 100,000 requests/day per keyword search/geocoding endpoint under free tier. Paid upgrade available via Kakao Paid API. [citation] |
| Authentication | Client ID/Secret headers; per-app quotas; simpler onboarding but lower cap. | REST API key; free quota generous but requires Kakao Map activation + Paid API enablement for scaling, more onboarding steps. [citation] |
| Latency/coverage | Native fit for Korea (Naver Places). Manual place entry still mandatory for outages. | Also Korea-focused; offers category search; redundant provider if Naver throttles. |
| Cost | Free within 25k/day; beyond requires commercial arrangement (out of MVP scope). | Free within 100k/day; paid tier billed per call for higher volumes. [citation] |

**Decision**: Keep Naver Search API as primary because 25k/day covers MVP traffic estimates (≤5k/day). Kakao Local remains contingency for Phase 2 if Naver quota becomes a bottleneck. Manual place entry stays as fallback whenever both APIs fail.

## Detailed Analysis
### Quotas & Scaling
- **Naver**: Search API (including local search) limited to 25,000 calls/day per application. Monitor daily usage via planned Prometheus counter; alert at 80% threshold. [citation]
- **Kakao**: Local API endpoints (keyword search, geocode) provide 100,000 free calls/day; exceeding free tier requires enabling Paid API and billing wallet. [citation]
- **Fallback**: Manual place entry path remains mandatory (already scoped in BE-Core/FE-02) to cover quota exhaustion or network failures.

### Authentication & Operational Notes
- **Naver**: Use X-Naver-Client-Id/Secret. Rotate secrets via GitHub Actions secrets -> Docker Compose env.
- **Kakao**: Requires Kakao Dev app with Map APIs enabled and Paid API toggle for higher tiers. Additional compliance overhead; keep as contingency only. [citation]

### Error Classification
| Error Code | Upstream Trigger | Backend Response | Frontend Action |
| --- | --- | --- | --- |
| `[NAVER_API_TIMEOUT]` | HTTP timeout >3s | 502 with retry metadata | Show toast “검색 지연”, surface manual entry |
| `[NAVER_API_SERVER_ERROR]` | 5xx | 502 + fallback to manual entry | Toast w/ retry CTA |
| `[NAVER_API_QUOTA]` | 429 | 502 w/ message “한도 초과” | Force manual input |
| `[NAVER_API_CLIENT_ERROR]` | 4xx except 429 | 502 w/ “요청 오류” | Toast |

Same codes reused for Kakao if/when adopted; prefix will stay `[NAVER_API_*]` until a second provider ships, then expand to provider-agnostic names.

### Gradle Dependency-Management Plugin Alignment
- Generated reference project via `start.spring.io` (Boot 3.5.13, Java 17). The emitted `build.gradle` used `io.spring.dependency-management` **1.1.7**, which matches our current `server/build.gradle`. No change required; recorded here for traceability.

### Next Steps & Follow-ups
1. Implement `PlaceApiClient` contract (this spike delivers interface stub).
2. Add Prometheus counter `naver_search_api_requests_total{status=...}` in BE-Core once API integration begins.
3. If throughput nears 25k/day, open DEV-Infra follow-up to enable Kakao Paid API for redundancy.
