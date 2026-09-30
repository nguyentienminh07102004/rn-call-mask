# rn-call-mask planning bundle

This bundle is intended to be copied into the repository root.

Included:

- `docs/REQUIREMENTS.md` — detailed product/engineering requirements and acceptance criteria;
- `docs/ROADMAP.md` — AI-assisted implementation phases and milestones;
- `AGENTS.md` — repository rules for coding agents;
- `.agents/skills/*/SKILL.md` — focused agent playbooks;
- `.github/workflows/ci.yml` — repository + JS/TS validation;
- `.github/workflows/android-ci.yml` — native Android validation once Android source exists;
- `.github/workflows/ios-ci.yml` — native iOS validation once iOS source exists;
- `.github/pull_request_template.md` — lifecycle/device validation checklist;
- `scripts/validate-repo.sh` — repository policy check used by CI.

The repository is currently bootstrap-empty except for `LICENSE`, so workflows intentionally skip language/native jobs until the corresponding scaffold exists. Once `package.json` or native source is added, CI becomes strict and requires the documented scripts.
