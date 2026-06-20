# EnterprisePOS Contributing Guide

## Welcome

Thank you for contributing to EnterprisePOS. This document outlines the process for submitting changes, coding standards, and review expectations.

## Getting Started

### Prerequisites

- Android Studio Hedgehog or later
- JDK 17
- Git
- Read [SETUP.md](SETUP.md) for environment configuration

### Development Workflow

1. **Fork** the repository (external contributors) or **clone** (internal)
2. **Create branch** from `develop`: `git checkout -b feature/your-feature-name`
3. **Make changes** following coding standards
4. **Write tests** for all new code
5. **Run tests**: `./gradlew test connectedCheck`
6. **Submit pull request** to `develop` branch

## Branch Naming

| Type | Pattern | Example |
|------|---------|---------|
| Feature | `feature/description` | `feature/split-tender-ui` |
| Bugfix | `fix/description` | `fix/tax-calculation` |
| Hotfix | `hotfix/description` | `hotfix/payment-crash` |
| Release | `release/vX.Y.Z` | `release/v1.2.0` |

## Commit Messages

Use conventional commits format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation only
- `style`: Formatting, no logic change
- `refactor`: Code restructuring
- `test`: Adding or updating tests
- `chore`: Build, dependencies, tooling

### Examples

```
feat(payment): add offline retry for card refunds

Implements exponential backoff retry for offline refunds
with maximum 24-hour queue retention.

Closes #123
```

```
fix(tax): correct compound tax calculation for order-level discounts

Previously applied discount after tax; now applies before
per tax regulation requirements.

Fixes #456
```

## Coding Standards

### Kotlin Style

Follow the [official Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html):

- 4-space indentation
- 120-character line limit
- Trailing commas in multi-line declarations
- Explicit `public` visibility omitted
- `Unit` return type omitted

### Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| Class | PascalCase | `OrderRepositoryImpl` |
| Interface | PascalCase, no prefix | `OrderRepository` |
| Function | camelCase | `calculateTotal` |
| Variable | camelCase | `orderTotal` |
| Constant | UPPER_SNAKE_CASE | `MAX_SYNC_INTERVAL` |
| Boolean | is/has/should prefix | `isAvailable`, `hasPermission` |

### Architecture Rules

- Domain module must not depend on Android framework
- Data module must not depend on app module
- Feature modules communicate via domain interfaces only
- No circular dependencies between modules
- Use dependency injection (Hilt) for all service dependencies

### Testing Requirements

All submissions must include:

- Unit tests for new business logic
- Integration tests for database changes
- UI tests for new screens or significant UI changes
- All tests must pass before merge

### Documentation

Update documentation for user-facing changes:

- `docs/API.md` for API changes
- `docs/HARDWARE.md` for hardware integration changes
- `docs/SECURITY.md` for security-related changes
- `CHANGELOG.md` for release notes

## Pull Request Process

### Before Submitting

- [ ] Branch is up to date with `develop`
- [ ] All tests pass (`./gradlew test`)
- [ ] No lint errors (`./gradlew detekt`)
- [ ] No secrets in code (run `./gradlew checkSecrets`)
- [ ] CHANGELOG.md updated for user-facing changes
- [ ] Documentation updated for API or architecture changes

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] UI tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex logic
- [ ] Documentation updated

## Related Issues
Fixes #123
```

### Review Process

1. **Automated checks**: CI runs tests, lint, and security scan
2. **Code review**: Minimum one approval from code owner
3. **QA validation**: For feature changes, QA validates on staging
4. **Merge**: Squash merge to `develop` with PR title as commit

### Review Criteria

Reviewers check for:

- Correctness: Does it solve the stated problem?
- Testing: Are there adequate tests?
- Performance: Any obvious performance issues?
- Security: Any security concerns?
- Maintainability: Is code readable and well-documented?
- Consistency: Does it follow existing patterns?

## Security

### Secrets

- Never commit API keys, passwords, or tokens
- Use `local.properties` for local secrets
- Use environment variables in CI
- Run `./gradlew checkSecrets` before commit

### Dependencies

- Update dependencies quarterly
- Run vulnerability scan: `./gradlew dependencyCheckAnalyze`
- Prefer well-maintained libraries with active community
- Document why a dependency is added in PR description

## Release Process

1. Feature freeze: No new features merged to `develop`
2. Release branch: `git checkout -b release/v1.2.0 develop`
3. Version bump: Update `versionName` and `versionCode`
4. QA validation: Full regression test on release branch
5. Merge to main: `git checkout main && git merge --no-ff release/v1.2.0`
6. Tag release: `git tag -a v1.2.0 -m "Release 1.2.0"`
7. Merge back: `git checkout develop && git merge --no-ff main`
8. Deploy: Follow [DEPLOYMENT.md](DEPLOYMENT.md)

## Issue Reporting

### Bug Reports

Include:
- App version
- Android version and device model
- Steps to reproduce
- Expected behavior
- Actual behavior
- Screenshots or logs if applicable

### Feature Requests

Include:
- Use case description
- Proposed solution
- Alternatives considered
- Impact on existing functionality

## Code of Conduct

- Be respectful and constructive in all interactions
- Focus on technical merit in reviews
- Welcome newcomers and help them learn
- Respect differing viewpoints and experiences
- Prioritize customer impact in decision making

## Questions?

- **Technical**: Ask in pull request or GitHub discussion
- **Process**: Email dev-team@enterprisepos.com
- **Urgent**: Slack #pos-dev channel (internal)

## License

By contributing, you agree that your contributions will be licensed under the project's proprietary license. All contributions require a signed Contributor License Agreement (CLA) for external contributors.
