# Contributing

See [Development](DEVELOPMENT.md) for source setup, builds, and checks.

## Workflow

1. Make a focused change on a branch.
2. Run the relevant checks from the repository root; use `just check` for the required unit-test and lint checks.
3. Review the diff for unintended changes, secrets, and updated user-facing documentation.
4. Submit a pull request through the project's normal hosting and review process.

## AI-use disclosures

Include an AI-use disclosure in both the git commit description and pull request description. Name the harness and model, briefly describe what they assisted with, and state “None” if no AI was used. If the model is unavailable, say so rather than guessing.

## Pull requests

Include a concise summary, the checks actually run, any limitations or untested behavior, and whether the change affects release behavior. Update the relevant documentation when behavior or setup changes.

