# Contributing to FwKnop-ng

Bug reports, feature requests and pull requests are welcome.

## Reporting

- **Bugs and feature requests**: use the [issue templates](https://github.com/gildas-le-drogoff/FwKnop-ng/issues/new/choose).
- **Security vulnerabilities**: never in a public issue, see the [security policy](SECURITY.md).

Before reporting a connection problem, check the `fwknopd` server logs and, when possible, try the same configuration with the official `fwknop` command-line client: this tells whether the issue comes from the app or from the server setup.

## Pull requests

For anything beyond a small fix, open an issue first to agree on the approach.

### Setup

Requirements: JDK 21 and the Android SDK (compile SDK 37). See [Building](../README.md#building).

### Before submitting

```sh
./gradlew lintDebug testDebugUnitTest assembleDebug
```

The same tasks run in CI on every pull request.

- **Dependencies are locked.** After adding or updating a dependency, refresh the lockfiles and commit them:

  ```sh
  ./gradlew dependencies --write-locks
  ```

- **No proprietary dependencies, trackers or analytics**: the app is distributed on F-Droid.
- **No new network requests** without discussing it first: the privacy section of the README lists every request the app makes.
- **Strings**: add new strings in English (`values/strings.xml`) and French (`values-fr/strings.xml`).
- **Room schema**: if you change an entity, bump the database version, add a migration and commit the generated schema in `app/schemas/`.
- Follow the existing architecture (`domain` / `data` / `presentation`) and code style.

### Commits

Use [Conventional Commits](https://www.conventionalcommits.org/): `feat: …`, `fix: …`, `chore(scope): …`, `docs: …`.

## License

By contributing, you agree that your contributions are licensed under the [GNU General Public License v2.0 or later](../COPYING).
