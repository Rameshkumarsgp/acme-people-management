.DEFAULT_GOAL := help
.PHONY: help setup test test-backend test-integration test-frontend test-e2e check secrets audit

ROOT := $(shell git rev-parse --show-toplevel)
NODE := . $(ROOT)/scripts/use-node.sh

help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-16s\033[0m %s\n", $$1, $$2}'

setup: ## Install git hooks and frontend dependencies (run once after cloning)
	chmod +x git_hooks/* scripts/*.sh
	git config core.hooksPath "$(ROOT)/git_hooks"
	@command -v gitleaks >/dev/null || echo "WARNING: gitleaks not found. Install it: brew install gitleaks"
	$(NODE) && cd frontend && npm ci
	@echo "Git hooks active: pre-commit, commit-msg, pre-push."

test-backend: ## Backend: Checkstyle, SpotBugs, Error Prone and the fast tests (no Docker)
	cd backend && ./gradlew check -x integrationTest --quiet --console=plain --warning-mode=none

test-integration: ## Backend database tests against PostgreSQL in Docker (Docker must be running)
	cd backend && ./gradlew integrationTest --quiet --console=plain --warning-mode=none

test-frontend: ## Frontend: format, lint and unit tests
	$(NODE) && cd frontend && npm run format:check && npm run lint && npm run test

test-e2e: ## Real-browser tests of the screens (installs Chromium once, not part of pre-commit)
	$(NODE) && cd frontend && npx playwright install chromium && npm run e2e

test: test-backend test-frontend ## Fast checks for both apps (what pre-commit runs)

check: test test-integration ## Everything pre-push runs: fast checks, database tests, typecheck, Knip and build
	$(NODE) && cd frontend && npm run typecheck && npm run knip && npm run build

secrets: ## Scan the working tree and history for secrets
	gitleaks dir . --no-banner --redact
	gitleaks git . --no-banner --redact

audit: ## Dependency vulnerability scan (npm audit)
	$(NODE) && cd frontend && npm run security:deps
