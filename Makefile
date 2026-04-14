# Beacolanders Makefile
# Load test server directory from .local.env

include .local.env

.PHONY: build deploy deploy-clean clean test verify e2e e2e-setup e2e-only

# Build the plugin
build:
	./mvnw clean package -DskipTests

# Build and deploy to test server (keeps configs)
deploy: build
	@rm -f "$(TEST_SERVER_DIR)/plugins/"beacolanders*.jar
	@rm -rf "$(TEST_SERVER_DIR)/plugins/.paper-remapped/"*[Bb]eacolanders*
	@cp target/beacolanders-*.jar "$(TEST_SERVER_DIR)/plugins/"
	@echo "Deployed to $(TEST_SERVER_DIR)/plugins/"

# Build and deploy with fresh configs (keeps database!)
deploy-clean: build
	@rm -f "$(TEST_SERVER_DIR)/plugins/"beacolanders*.jar
	@rm -rf "$(TEST_SERVER_DIR)/plugins/.paper-remapped/"*[Bb]eacolanders*
	@rm -f "$(TEST_SERVER_DIR)/plugins/Beacolanders/"*.yml
	@cp target/beacolanders-*.jar "$(TEST_SERVER_DIR)/plugins/"
	@echo "Deployed (clean configs) to $(TEST_SERVER_DIR)/plugins/"

# Run tests
test:
	./mvnw test

# Full verification (compile + test + lint + spotbugs)
verify:
	./mvnw clean verify

# Clean build artifacts
clean:
	./mvnw clean

# Install E2E test dependencies
e2e-setup:
	cd e2e && npm install

# Build, deploy, and run E2E tests (full pipeline)
e2e: deploy
	cd e2e && npm test

# Run E2E tests without rebuilding
e2e-only:
	cd e2e && npm test
