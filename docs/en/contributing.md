# Contributing Guide

Thank you for your interest in contributing to RulEuler.

## Development Environment

Follow [Local Development](getting-started/local-dev.md) to set up your local environment.

## Branching Strategy

- `main` — Stable release branch
- `dev` — Development branch
- Create feature branches from `dev` and open a Pull Request back to `dev` when ready

## Commit Message Convention

```
feat: new feature
fix: bug fix
docs: documentation changes
refactor: code refactoring
test: adding or updating tests
chore: build scripts or tooling
```

Example: `feat: support function calls in REA expressions`

## Project Conventions

- Avoid modifying `ruleuler-core` unless strictly necessary
- Use unified `db` storage mode within a project
- Do not add features to legacy `/urule/` endpoints; the goal is to progressively replace them
- Avoid duplicate network requests in the frontend under React `StrictMode`

## Running Tests

Ensure all tests pass before submitting a PR:

```bash
# Frontend
cd ruleuler-admin && pnpm test

# Backend
mvn test -pl ruleuler-server
```

## Reporting Issues

Please submit issues via GitHub Issues and include:

- Steps to reproduce
- Expected behavior vs. actual behavior
- Environment details (OS, Java version, browser)

