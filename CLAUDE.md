# 셜록(Sherlock) 보드게임 동아리 시스템

## 프로젝트 개요
보드게임 동아리 '셜록'의 운영 시스템. 실제 도메인에 배포해 회원들이 사용한다.
1. 보드게임 대여 시스템 (현재 진행)
2. 동아리 홍보 페이지
3. 신입생 보드게임 추천 + AI 룰 설명

## 기술 스택
- Backend: Java 21, Spring Boot 4.x, Gradle(Kotlin DSL), Spring Data JPA, Spring Security, Bean Validation
- DB: PostgreSQL (운영), H2 (테스트)
- Frontend: React + TypeScript + Vite
- Test: JUnit 5, Spring Boot Test, MockMvc (백엔드) / Vitest + Testing Library (프론트)

## 폴더 구조
- backend/  : Spring Boot 프로젝트 (패키지 루트 com.sherlock)
  - 계층: controller / service / repository / domain / dto
- frontend/ : React 프로젝트

## 도메인 규칙 (대여 시스템)
- 회원은 사이트에서 대여 가능한 보드게임 목록을 조회하고 직접 대여한다.
- 대여 기간은 7일. 반납 예정일 = 대여일 + 7일, 화면에 표시한다.
- 보유 수량만큼 동시 대여 가능하다. 대여 가능 수량 = 전체 수량 − 미반납 대여 수.
- 반납은 회원이 직접 처리하며, 반납 시 분실/파손을 신고할 수 있다.
- 게임 분류는 보드게임(BOARD_GAME) / 크라임씬(CRIME_SCENE) 두 가지다.
- 관리자(임원진)만 보드게임 추가/수정/삭제 가능 (연 1회 전수조사 대응).
- 보드게임마다 특이사항(부속품 분실 등)을 기록·조회할 수 있다.

## 작업 규칙
- Issue 하나 = 브랜치 하나 = PR 하나. 이슈 범위 밖의 수정은 하지 않는다.
- 브랜치 이름: feat/<이슈번호>-<짧은설명>, fix/<이슈번호>-<짧은설명>
- 커밋 메시지: Conventional Commits (feat:, fix:, test:, chore:, docs:)
- PR 본문에는 변경 요약, 테스트 방법, `Closes #이슈번호`를 포함한다.
- 새 기능에는 반드시 테스트를 함께 작성하고, 로컬에서 테스트 통과를 확인한다.
- PR 설명, 코드 주석, 커밋 설명은 한국어로 작성한다.
- 비밀값(API 키, DB 비밀번호)은 절대 커밋하지 않는다. 환경변수로만 사용한다.
- main에 직접 push하거나 PR을 머지하지 않는다. 머지는 호스트만 한다.

## 리뷰 반영 규칙
- Gemini 리뷰 코멘트를 반영할 때, 동의하지 않는 지적은 이유를 코멘트로 남긴다.
- 같은 PR에서 리뷰 반영이 3회를 넘거나 판단이 애매하면 작업을 멈추고
  `needs-human` 라벨을 붙인 뒤 호스트에게 질문을 남긴다.
