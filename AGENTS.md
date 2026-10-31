# Agent instructions

Use the app source and configuration as the authority for behavior. Keep maintained docs consistent with code; see [Architecture](ARCHITECTURE.md), [Privacy](PRIVACY.md), and [Security](SECURITY.md) for their respective boundaries.

- Use [Development](DEVELOPMENT.md) and the [root justfile](justfile) for setup, builds, and checks. Use [Contributing](CONTRIBUTING.md) for review workflow and per-PR AI disclosure.
- Do not read, print, commit, or disclose machine-specific values from `local.properties`, signing properties, keystores, or OAuth tokens. Never add credentials or token examples that could be mistaken for real secrets.
- Keep edits focused. Update user-facing docs when behavior, setup, permissions, or data flows change.
