# AGENTS.md

This file provides implementation guidance for coding agents in this repository.

## Project Overview

AppFork is a CLI repository-updater for software manifests.

- Stack: Java 21, Spring Boot 4.0.2, Maven.
- Runtime type: non-web (`spring.main.web-application-type: none`).
- Main job: scan `plate/manifests`, execute Groovy update scripts, and rewrite JSON/YAML manifests when versions/URLs change.
- Concurrency model: virtual threads + bounded concurrency (semaphore) in updater execution.

## Important Paths

- `src/main/java/plus/junlong/appfork/` - application and updater logic.
- `src/main/java/plus/junlong/appfork/script/` - script API + shared script variables.
- `src/test/java/plus/junlong/appfork/AppForkTests.java` - all current tests/utilities.
- `src/main/resources/application.yml` - default profile + repo path.
- `src/main/resources/application-test.yml` - test logging config.
- `src/main/resources/application-prod.yml` - prod logging config.
- `plate/manifests/` - active app manifests (JSON/YAML, grouped by first letter/`#`).
- `plate/manifests-test/` - test manifests used when profile is `test`.
- `plate/scripts/` - Groovy scripts implementing update check logic.
- `.github/workflows/check-update.yml` - scheduled update runner.
- `.github/workflows/sync-to-gitee.yml` - mirror workflow.

## Build, Run, and Test Commands

Prerequisites:

- JDK 21
- Maven 3.9+
- No Maven wrapper in repo

Use these commands from repo root:

```bash
# compile + test + package jar
mvn package

# package only (CI style)
mvn -B -DskipTests=true package --file pom.xml

# run all tests
mvn test

# run a single test class
mvn test -Dtest=AppForkTests

# run a single test method
mvn test -Dtest=AppForkTests#convertJsonOrYaml

# run the manifest verification method only
mvn test -Dtest=AppForkTests#verifyManifests

# clean artifacts
mvn clean

# run built jar (default profile = test)
java -jar ./target/appfork-repo-updater.jar

# run jar with prod profile
java -jar ./target/appfork-repo-updater.jar --spring.profiles.active=prod
```

Lint/format status:

- No dedicated lint command configured.
- No Checkstyle/Spotless/PMD/formatter plugin configured in `pom.xml`.

## Architecture Notes

- `AppForkApplication` sets timezone to `Asia/Shanghai` in static initializer and boots Spring.
- `Updater` (`@Component`, `CommandLineRunner`) is the core orchestrator.
- `ScriptUpdater` is the Groovy script contract: `Object checkUpdate(JSONObject manifest, JSONObject args)`.
- `ScriptVars` exposes shared constants (`USER_AGENT`, `HTTP_CLIENT`, `newHttpClientBuilder()`).

Updater flow (high level):

1. Resolve manifest directory (`manifests` or `manifests-test` by profile).
2. Scan `.json`/`.yaml` files with Hutool `FileUtil.loopFiles`.
3. Resolve script from `script` field or fallback to manifest filename.
4. Parse/load Groovy class with cache and lock protection.
5. Execute `checkUpdate` and validate returned values.
6. Write back updated manifest (JSON pretty print or YAML dump options).

## Java Style Guidelines

Follow existing style in `Updater` and tests:

- Java version features: Java 21 syntax is allowed and used (`switch` expressions, pattern matching for `instanceof`).
- Class shape: prefer `final` for concrete utility/orchestrator classes when suitable.
- Package naming: lower-case package names under `plus.junlong.appfork`.
- Imports: keep grouped ordering as used in source files:
  1) third-party libs
  2) Spring/framework
  3) project-internal
  4) `java.*`
- Stdlib wildcard imports (`java.util.*`, `java.util.concurrent.*`) are acceptable in this codebase.
- Logging: use Lombok `@Slf4j`; use placeholder style (`{}`), avoid string concatenation.
- Log language: Chinese messages are common and acceptable; keep consistency in changed files.
- Constants: project commonly uses `private final` instance constants in `Updater`.
- Null/blank checks: prefer Hutool `StrUtil.isBlank()`.
- Regex checks: prefer Hutool `ReUtil.isMatch()` with precompiled `Pattern` fields.
- File I/O: prefer Hutool `FileUtil` over raw JDK utilities for read/write convenience.
- JSON: use fastjson2 `JSON`, `JSONObject`, and `JSONWriter.Feature.PrettyFormat`.
- YAML: use SnakeYAML and preserve existing `DumperOptions` settings.
- Error handling: in concurrent blocks, catch broad `Exception`, log concise cause; restore interrupt flag when catching `InterruptedException`.
- URL validation: follow existing `Updater.isUrl` regex behavior; do not introduce incompatible URL assumptions.
- Reflection/script loading: keep constructor/interface checks when changing script loading logic.

## Groovy Script Conventions

- File location: `plate/scripts/<script-name>.groovy`.
- Class name: always `UpdateScript`.
- Must implement `ScriptUpdater`.
- Method signature: `Object checkUpdate(JSONObject manifest, JSONObject args)`.
- Return conventions:
  - `null` => no update
  - `[version: 'x.y.z', url: ...]` => update payload
  - `[error: 'message']` => script execution error
- `url` may be `String`, `Map<String, String>`, or `List<String>`.
- Scripts generally avoid explicit `package`; loader injects package automatically when absent.
- Use `ScriptVars.HTTP_CLIENT` and `ScriptVars.USER_AGENT` for network calls when possible.

## Manifest Conventions

- Supported formats: `.json` and `.yaml` (`.yaml` is the top priority).
- Directory pattern: `plate/manifests/<letter-or-#>/<name>.<ext>`.
- Required keys: `name`, `homepage`, `author`, `description`, `category`, `platform`, `version`, `url`.
- Optional keys: `logo`, `script`.
- `script` can be:
  - string script name, or
  - object with `name` and optional `args`.
- JSON formatting in repo typically uses tabs and compact key/value spacing.
- Keep category/platform values aligned with existing allowed sets in `Updater`.

## Testing Guidance

- Framework: JUnit 5 via Spring Boot starter test.
- Existing tests are in a single class: `AppForkTests`.
- `verifyManifests` executes scripts and validates returned URLs with HEAD/GET fallback.
- Network-related timeout/reset errors may be treated as warnings in that test logic.
- When adding tests, keep method-targetable names so `-Dtest=Class#method` remains practical.
- When generating new manifest files, ask the user whether to run the manifest verification test.
  - If the user confirms, run `mvn test -Dtest=AppForkTests#verifyManifests`.
  - Before running, set the test method's `manifestPaths` to include only the newly generated manifest file paths (supports multiple).
  - After the test finishes (success or failure), restore `manifestPaths` to its original value.

## CI/CD Behavior

- `check-update.yml` runs on schedule (`0 3/6 * * *`) and manual dispatch.
- CI builds with `mvn -B -DskipTests=true package --file pom.xml`.
- CI runs jar with `--spring.profiles.active=prod`.
- If file changes exist, workflow auto-commits and pushes to current branch.
- A repository dispatch event then triggers gitee sync workflow.

## External Agent Rule Files

Checked in this repository:

- `.cursor/rules/` - not found
- `.cursorrules` - not found
- `.github/copilot-instructions.md` - not found

If these files are added later, treat them as higher-priority agent instructions and merge their guidance into this document.
