# Contributing

See [Development](DEVELOPMENT.md) for source setup, builds, and checks.

## Workflow

1. Make a focused change on a branch.
2. Run the relevant checks from the repository root; use `just check` for the required unit-test and lint checks.
3. Review the diff for unintended changes, secrets, and updated user-facing documentation.
4. Submit a pull request through the project's normal hosting and review process.

## Pull requests

Include a concise summary, the checks actually run, any limitations or untested behavior, and whether the change affects release behavior. Update the relevant documentation when behavior or setup changes.

- AI use (required): list the AI tools and models used, what they assisted with, and the reasoning level when available. State “None” if no AI was used.
