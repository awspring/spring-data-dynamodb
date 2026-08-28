#
# Convenience targets for Spring Data DynamoDB.
#
# Uses the Maven Daemon (mvnd) when it is on the PATH, falling back to the Maven
# wrapper otherwise, so every target works on a clean checkout.
#
MVN := $(shell command -v mvnd 2>/dev/null || echo ./mvnw)

.DEFAULT_GOAL := help
.PHONY: help build clean format check test doc docs docs-open

help: ## Show the available targets
	@grep -hE '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) \
		| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## Build and install all modules
	$(MVN) install

clean: ## Remove all build output
	$(MVN) clean

format: ## Apply the Spotless code format
	$(MVN) spotless:apply

check: ## Verify formatting without modifying sources
	$(MVN) spotless:check

test: ## Run the full test suite (integration tests need a running Docker daemon)
	$(MVN) test

doc: docs ## Alias for docs

docs: ## Build the reference documentation and the API docs without formatting sources
	$(MVN) package -Pdocs-classic -DskipTests -Dspotless.apply.skip=true
	@echo
	@echo "Reference documentation: docs/target/generated-docs/reference/html/reference.html"
	@echo "API documentation:       target/site/apidocs/index.html"

docs-open: docs ## Build the docs, then open the reference guide
	@open docs/target/generated-docs/reference/html/reference.html 2>/dev/null \
		|| xdg-open docs/target/generated-docs/reference/html/reference.html 2>/dev/null \
		|| echo "Open docs/target/generated-docs/reference/html/reference.html manually"
