# SPIKE-NaverAPI - Search/Geocode 의사결정 기록

## 1) 목적
- Phase 0에서 장소 검색/지오코딩 경로를 확정한다.
- Issue #3 완료조건 5개 항목의 증빙 문서로 사용한다.
- BE-Core(#5), FE-02(#15), OPS-Reliability(#16)가 바로 구현 가능한 계약을 제공한다.

## 2) API 비교

### 2-1. Search vs Geocode (Naver 내부 비교)
| 항목 | Naver Search API (지역검색) | Naver Maps Geocoding API |
| --- | --- | --- |
| 주 용도 | 키워드 기반 장소 후보 탐색 | 주소 -> 좌표 변환, 좌표 -> 주소(Reverse) |
| 입력 | 검색어(query), 정렬/페이지 | 주소(query) 또는 좌표 |
| 출력 | 장소명/카테고리/주소/링크 중심 | 좌표(x,y), 도로명/지번 주소 중심 |
| 인증 | `X-Naver-Client-Id`, `X-Naver-Client-Secret` | `x-ncp-apigw-api-key-id`, `x-ncp-apigw-api-key` |
| 쿼터 | 일 25,000 호출(검색 API 공통) | Maps 공통 쿼터/속도 제한 정책 적용(429 Quota/Throttle/Rate 코드) |
| 비용 | 검색 API 범위 내 무상 사용(상세 상업 조건은 별도) | NCP Maps 과금 체계(환경/요금제 기준, 콘솔/요금계산기 확인 필요) |
| MVP 역할 | 리뷰 작성 시 장소 후보 제공의 기본 경로 | 수동 입력 보정/주소 정규화 보조 경로 |

### 2-2. 기본안 vs 대체안
| 항목 | 기본안: Naver Search + Geocode | 대체안: Kakao Local |
| --- | --- | --- |
| 일 쿼터 레퍼런스 | Search 25,000/day, Maps는 앱 쿼터 정책 기반 | Local API 100,000/day (키워드/지오코딩 계열) |
| 장애 시 대응 | 수동 장소 입력 fallback + 재시도 정책 | Naver 한도/장애 장기화 시 2차 공급자 후보 |
| 운영 복잡도 | 현재 스택과 정합, 즉시 적용 가능 | 앱 설정/운영 체크리스트 추가 필요 |
| 결론 | **MVP 기본 공급자 채택** | Phase 2 이후 확장 카드로 유지 |

## 3) 실패/레이트리밋/백오프 정책

### 3-1. 정책 테이블
| 시나리오 | 감지 신호 | 재시도 | 백오프 | 중단/전환 조건 | 표준 오류 코드 |
| --- | --- | --- | --- | --- | --- |
| Timeout | 소켓/읽기 타임아웃 | 예 | 1s -> 2s -> 4s (최대 3회) | 3회 실패 시 수동 입력 유도 | `PLACE_API_TIMEOUT` |
| Upstream 5xx | HTTP 500/503/504 | 예 | 1s -> 2s -> 4s (최대 3회) | 3회 실패 시 수동 입력 유도 | `PLACE_API_UPSTREAM_ERROR` |
| Quota/Rate Limit | HTTP 429 | 부분(1회) | 2s 1회 후 종료 | 동일 요청 2회 연속 429면 즉시 수동 입력 | `PLACE_API_RATE_LIMIT` |
| Client 4xx(입력오류) | HTTP 400/401/403/404 | 아니오 | 없음 | 즉시 종료, 입력 수정 유도 | `PLACE_API_BAD_REQUEST` |

### 3-2. 정책 근거
- 사용자 대기시간 상한: 재시도 총 대기시간을 7초(1+2+4)로 제한한다.
- 공급자 보호: 429에는 공격적 재시도를 피하고 1회만 허용한다.
- 서비스 연속성: 실패 시 반드시 수동 장소 입력 경로를 노출한다.
- 관측성: `place_api_requests_total{provider,status}`와 `place_api_fallback_total{reason}` 지표를 기록한다.

## 4) PlaceApiClient 계약/DTO/오류 분류

### 4-1. 계약 요약
- 코드: `server/src/main/java/com/eatchive/server/infra/place/PlaceApiClient.java`
- `searchPlaces(query)`: 장소 후보 조회
- `geocode(query)`: 주소/좌표 정규화
- DTO 초안: `SearchPlacesQuery`, `PlaceSearchResult`, `PlaceSummary`, `GeocodeQuery`, `GeocodeResult`

### 4-2. 오류 분류 표준
- 공급자별 코드(`NAVER_*`)를 직접 노출하지 않는다.
- 서비스 외부(응답/프론트 계약)에는 아래 표준 코드만 사용한다.
  - `PLACE_API_TIMEOUT`
  - `PLACE_API_UPSTREAM_ERROR`
  - `PLACE_API_RATE_LIMIT`
  - `PLACE_API_BAD_REQUEST`

## 5) dependency-management 버전 기록 (start.spring.io)
- start.spring.io 기준(Boot 3.5.13, Java 17)에서 `io.spring.dependency-management`는 `1.1.7`.
- 현재 서버 설정과 일치: `server/build.gradle`의 `id 'io.spring.dependency-management' version '1.1.7'`.

## 6) 의존 이슈 공유 항목 (PM 전파용)
- BE-Core(#5): 표준 오류 코드 4종 + retry/fallback 정책을 서비스/예외 처리 계층에 반영.
- FE-02(#15): `PLACE_API_*` 코드 기준 사용자 메시지/수동입력 전환 UX 고정.
- OPS-Reliability(#16): 429/timeout 비율과 fallback 지표를 운영 경보 기준으로 포함.

## 7) 출처 (조회일: 2026-04-15)
1. Naver 검색 API 지역검색(일 25,000 호출)
- https://developers.naver.com/docs/serviceapi/search/local/local.md
2. Naver Maps 개요(429 Quota/Throttle/Rate, 인증 헤더)
- https://api.ncloud-docs.com/docs/en/ainaverapi-maps-overview
3. Kakao Local 개요(로컬 기능/사전 설정)
- https://developers.kakao.com/docs/latest/en/local/common
4. Kakao 쿼터/유료 API 단가(Local 100,000/day 포함)
- https://developers.kakao.com/docs/latest/en/getting-started/quota
5. NAVER Cloud 요금 계산/과금 안내(Maps 과금 확인 경로)
- https://www.ncloud.com/charge/calc
