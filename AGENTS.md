DO NOT delete or modify files in the cache/ directory

## Code rules

- **One class per file**: at most one top-level class, interface or object per file; nested classes and companion objects are fine.
- **Constructor fields**: every constructor field gets its own line, even when there is only one.
- **Braces**: always use curly braces for `if`, `else`, `for` and `while` bodies, even single statements.
- **No trailing commas**, neither in declarations nor in calls.
- **Methods over extension functions**: put behaviour into the class it belongs to. Use an extension only where a method is impossible or clearly worse (a type you don't own, a Ktor `Application.module()`), and mark it `@Suppress("ExtensionFunction")`.
- **TDD**: write a failing test first, then the code that makes it pass. New features come with tests.
- **No identifiable strings** in any committed file: no real series or episode names, tokens, usernames or local paths. Use dummy names in tests and examples (`Test Series`, `Dummy Episode`). If in doubt, ask.

## Enforcement

- `./gradlew build` runs everything below; CI runs it on pushes to `main` and on pull requests.
- **detekt** (`config/detekt/detekt.yml`) enforces braces, trailing commas, one constructor field per line and wrapping. Lint with `./gradlew detekt`, autofix with `./gradlew detekt --auto-correct` (missing braces on a single-line `if` stay manual). Single-line braceless `for`/`while` loops are not caught.
- **Konsist** (`CodeRulesTest.kt`) enforces one top-level declaration per file and the `@Suppress("ExtensionFunction")` opt-in.
- **Identifiable strings**: `scripts/check_identifiable.py` runs in the pre-commit hook (staged files) and in `./gradlew checkIdentifiable` (all files that would be committed). Its denylist is built only from local, ignored data: `cache/`, the ignored `*.conf` files, the folders below `baseDir`, the TMDB token, the OS username and an optional `identifiable.local.txt`. Without that data (CI, fresh clones) it only checks secret and path patterns. A generic word that is also a series name goes into `config/identifiable-allowlist.txt`; never add a real title there.
- **Pre-commit hook** (`.githooks/pre-commit`, activated by the first `./gradlew` build via `core.hooksPath`) blocks identifiable strings and warns when new code in `src/main` comes without a staged test.
