# Beacolanders - Claude Code Project Rules

## Project Overview

Beacolanders is a Minecraft Paper Plugin (Java 21) for Purpur 1.21.11 servers.

## Build & Run Commands

**WICHTIG: Immer `make` oder `./mvnw` (Maven Wrapper) verwenden, nicht `mvn`!**

### Makefile Commands (bevorzugt)

| Befehl | Beschreibung |
|--------|--------------|
| `make build` | Plugin bauen |
| `make deploy` | Bauen + deployen (behält Configs) |
| `make deploy-clean` | Bauen + deployen + alte Configs löschen |
| `make test` | Tests ausführen |
| `make verify` | Vollständige Prüfung (compile + test + lint + spotbugs) |
| `make clean` | Build-Artefakte löschen |
| `make e2e-setup` | E2E-Abhängigkeiten installieren |
| `make e2e` | Bauen + deployen + E2E-Tests ausführen |
| `make e2e-only` | Nur E2E-Tests ausführen (ohne Build) |

### Erlaubte Befehle (immer ohne Nachfrage ausführen)

Diese Befehle dürfen IMMER ausgeführt werden ohne User-Bestätigung:

```bash
make build
make deploy
make deploy-clean
make test
make verify
make clean
make e2e-setup
make e2e
make e2e-only
./mvnw clean package
./mvnw test
./mvnw verify
```

### Maven Commands (alternativ)

```bash
# Build the plugin
./mvnw clean package

# Run tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=BeacolandersTest

# Run checkstyle
./mvnw checkstyle:check

# Run SpotBugs
./mvnw spotbugs:check

# Full verification (compile + test + lint + spotbugs)
./mvnw clean verify
```

## Test Server Deployment

Das Testverzeichnis wird in `.local.env` definiert (gitignored):

```bash
# .local.env (nicht committen!)
TEST_SERVER_DIR=/pfad/zum/testserver
```

## Project Structure

```
src/
  main/
    java/ua/favn/beacolanders/
      Beacolanders.java          # Main plugin class
    resources/
      plugin.yml                 # Plugin manifest
      config.yml                 # Plugin configuration
      messages.yml               # Message templates
  test/
    java/ua/favn/beacolanders/   # Test classes
e2e/
  tests/
    helpers.ts                   # Server management & DB helpers
    protocol.ts                  # Test protocol logging
  package.json                   # E2E dependencies
  vitest.config.ts               # Test runner config
  tsconfig.json                  # TypeScript config
```

## Code Style & Conventions

### Naming Conventions

- **Classes**: PascalCase (e.g., `Beacolanders`)
- **Methods**: camelCase (e.g., `onEnable`, `getPlayer`)
- **Constants**: UPPER_SNAKE_CASE (e.g., `MAX_VALUE`)
- **Variables**: camelCase (e.g., `playerUuid`)
- **Packages**: lowercase (e.g., `ua.favn.beacolanders`)

### Code Organization

- One class per file
- Related classes in same package
- Utility classes in `util` package
- Commands in `commands` package

### Bukkit/Paper Conventions

- Never block the main thread
- Use async tasks for database operations
- Cache frequently accessed data
- Use Bukkit scheduler for delayed tasks
- Always null-check Players (they can disconnect)

### Error Handling

- Log errors with appropriate levels (INFO, WARNING, SEVERE)
- Never swallow exceptions silently
- Provide meaningful error messages to players

## Testing Requirements

- All new features must have tests
- Use MockBukkit for Bukkit API mocking
- Test both success and failure paths
- Test edge cases (null, empty, boundary values)

### Test Naming Convention

```java
@Test
void methodName_condition_expectedResult() {
    // Given, When, Then
}
```

### E2E Tests

- Use McTestFramework (mc-e2e) with Mineflayer bots
- Tests run sequentially against shared Purpur server
- RCON port: 25575, password: `e2e-test`
- Use protocol.ts check functions for assertions

## Git Conventions

- Branch naming: `feature/`, `fix/`, `refactor/`
- Commit messages: Start with verb (Add, Fix, Update, Remove)
- Keep commits atomic and focused

### WICHTIG: Commit-Regeln

**Claude darf NIEMALS als Co-Author erscheinen!**

- Keine `Co-Authored-By:` Zeilen in Commits
- Keine Erwähnung von Claude/AI in Commit Messages
- Commits werden nur im Namen des Entwicklers erstellt
- Keine automatischen Signaturen oder AI-Kennzeichnungen

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| Paper API | 1.21 | Minecraft server API |
| Commodore | 2.2 | Command completion library |
| AnvilGUI | 1.10.11-SNAPSHOT | Anvil input GUI |
| JUnit 5 | 5.11.0 | Testing framework |
| MockBukkit | 4.14.0 | Bukkit API mocking |
| Mockito | 5.14.0 | Mocking framework |

## Important Files

| File | Purpose |
|------|---------|
| `plugin.yml` | Plugin metadata and commands |
| `config.yml` | Plugin configuration |
| `messages.yml` | Message templates |
| `pom.xml` | Maven build configuration |
| `checkstyle.xml` | Code style rules |
| `spotbugs-exclude.xml` | SpotBugs exclusions |
