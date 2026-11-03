# Contributing

## Releases

Every push to `main` may release. semantic-release reads the commit messages since the last tag, so they must be
[Conventional Commits](https://www.conventionalcommits.org/) (the `commitlint` check on every PR enforces it):

| Commits since the last tag | Release |
|---|---|
| a `!` or a `BREAKING CHANGE:` footer | major |
| a `feat` | minor |
| `fix`, `perf`, `chore`, `refactor`, `style`, `test`, `build`, `ci` | patch |
| only `docs` | none |

The `release` workflow then:

1. commits `CHANGELOG.md`, tags `vX.Y.Z` and creates the GitHub release;
2. stamps that version into the poms, builds, tests, signs and publishes to Maven Central, and waits until the
   artifacts resolve.

If the Maven Central job fails, re-run that job alone: it skips a version that is already on Central. The poms keep
`0.0.0-SNAPSHOT`; never edit the version by hand.

Publishing uses the organization secrets `GPG_PRIVATE_KEY`, `GPG_PASSPHRASE`, `CENTRAL_USERNAME` and
`CENTRAL_PASSWORD`.
