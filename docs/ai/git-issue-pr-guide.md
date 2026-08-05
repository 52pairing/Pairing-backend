# Git, Issue, PR Guide

브랜치명, 이슈 제목, 커밋 메시지, PR 본문에 사용하는 문서입니다.

## AI 규칙

- 사용자가 명시적으로 요청하지 않으면 commit, push, 브랜치 생성, PR 생성을 하지 않습니다.
- 커밋이나 PR 작업 전에 의도한 범위를 먼저 요약합니다.
- 커밋 메시지, 이슈 본문, PR 설명에 시크릿이나 비공개 값을 넣지 않습니다.

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

## 프론트엔드 공유 규칙

프론트엔드에 영향을 주는 백엔드 변경은:

- 바뀐 계약이나 위험한 부분만 `.ai/API.md`에 기록합니다.
- PR 본문에 `Frontend Impact` 섹션을 채웁니다.
- PR 링크와 Swagger URL을 공유합니다.
- Swagger/OpenAPI를 전체 API 레퍼런스로 취급하고, `.ai/API.md`는 변경 노트로만 취급합니다.
