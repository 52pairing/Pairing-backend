# Git, Issue, PR Guide

브랜치명, 이슈 제목, 커밋 메시지, PR 본문에 사용하는 문서입니다.

## AI 규칙

- 개발을 시작하기 전에 feature 브랜치를 먼저 만듭니다. main/develop에서 직접 작업하지 않습니다.
- commit, push, PR 생성은 사용자가 명시적으로 요청할 때만 합니다.
- 커밋이나 PR 작업 전에 의도한 범위를 먼저 요약합니다.
- 커밋 메시지, 이슈 본문, PR 설명에 시크릿이나 비공개 값을 넣지 않습니다.
- PR을 올리기 전에 반드시 검증을 통과시킵니다 (아래 "PR 전 검증").

## 이슈 제목

접두사를 사용합니다: `[Feat]`, `[Task]`, `[Fix]`, `[Ref]`, `[Docs]`

```text
[Feat] 예시 이미지 업로드 API 추가
```

## 브랜치명

```text
feature/short-task-name
fix/short-bug-name
refactor/short-scope
docs/short-topic
```

## 커밋 메시지

접두사를 사용합니다: `feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`

```text
feat: add example image upload endpoint
```

## PR 제목

접두사를 사용합니다: `[Feat]`, `[Update]`, `[Fix]`, `[Ref]`, `[Docs]`

## PR 본문 템플릿

```md
## Summary

-

## Changes

-

## Frontend Impact

- API 변경: yes/no
- Endpoint:
- Request 변경:
- Response 변경:
- ErrorCode 변경:
- Breaking change: yes/no
- 프론트엔드 작업 필요:

## Verification

-

## Notes

-
```

## PR 전 검증 (프론트 연동 안전)

프론트엔드가 한 번에 연동되고 오류가 나지 않도록, PR 전에 아래를 통과시킵니다.

- `./gradlew build` (테스트 포함)가 통과해야 합니다.
- API를 추가/변경했으면 서버를 띄워 Swagger UI에서 **실제로 호출**해 응답이 `.ai/API.md` 계약과 일치하는지 확인합니다. (상세: `docs/ai/testing-guide.md`)
- 확인한 내용을 PR 본문 `Verification` 섹션에 적습니다.
- 프론트에 영향이 있으면 `Frontend Impact` 섹션을 채우고 Swagger URL을 공유합니다.

## 프론트엔드 공유 규칙

프론트엔드에 영향을 주는 백엔드 변경은:

- 바뀐 계약이나 위험한 부분만 `.ai/API.md`에 기록합니다.
- PR 본문에 `Frontend Impact` 섹션을 채웁니다.
- PR 링크와 Swagger URL을 공유합니다.
- Swagger/OpenAPI를 전체 API 레퍼런스로 취급하고, `.ai/API.md`는 변경 노트로만 취급합니다.
