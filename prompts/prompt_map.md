# Prompt Map — Native Android App

File này lưu mapping giữa prompt/user request và commit message để truy vết.

## Format

```md
## YYYY-MM-DD HH:mm — <short title>

- Prompt summary:
- Ticket/Issue ID:
- Scope:
- Main files changed:
- Tests run:
- Commit message: `<type>(<scope>): <summary>`
- Notes/Risks:
```

---

## Initial — Create native Android app documentation pack

- Prompt summary: Create `/app` folder documentation and implementation prompts for native Android PDA scanner app parity with scanner PWA.
- Ticket/Issue ID: APP-INIT
- Scope: docs/prompts/specs/checklists only.
- Main files changed: `app/**`
- Tests run: N/A
- Commit message: `docs(app): add native android pda scanner implementation pack`
- Notes/Risks: Requires agent to audit actual scanner PWA endpoints before coding.
