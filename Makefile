OUT_DIR=.github/bin

.PHONY: test
test: build
	@ clj2js compile -target js -src test/test.clj > $(OUT_DIR)/test/test.js
	@ cd .github && node --env-file=.dev.vars bin/test/test.js

.PHONY: e2e_test
e2e_test: test
	@ echo '{"type": "module", "devDependencies": {"wrangler": "^3.38.0"}}' > $(OUT_DIR)/package.json
	@ cd $(OUT_DIR) && yarn
	@ clj2js compile -target js -src test/main_test.clj > $(OUT_DIR)/test/main_test.js
	@ clj2js compile -target js -src test/e2e.test.clj > $(OUT_DIR)/test/e2e.test.js
	@ cd .github && node --env-file=.dev.vars bin/test/e2e.test.js

.PHONY: run
run: hook
	@ cd .github && wrangler dev --test-scheduled

.PHONY: build
build:
	@ mkdir -p $(OUT_DIR)/src && \
		mkdir -p $(OUT_DIR)/test && \
		mkdir -p $(OUT_DIR)/vendor/effects && \
		mkdir -p $(OUT_DIR)/vendor/cf-xmlparser
	@ echo '{"type": "module"}' > $(OUT_DIR)/package.json
	@ clj2js compile -target js -src vendor/effects/effects.2.clj > $(OUT_DIR)/vendor/effects/effects.2.js
	@ clj2js compile -target js -src vendor/cf-xmlparser/xml_parser.clj > $(OUT_DIR)/vendor/cf-xmlparser/xml_parser.js
	@ clj2js compile -target js -src src/core.clj > $(OUT_DIR)/src/core.js
	@ clj2js compile -target js -src src/main.clj > $(OUT_DIR)/src/main.js

.PHONY: clean
clean:
	@ rm -rf $(OUT_DIR)

.PHONY: migrate
migrate:
	@ cd .github && wrangler d1 execute DECLARATIVE_NOTIFY_BOT_DB --local --file=schema.sql

.PHONY: hook
hook:
	@NGROK_API="http://localhost:4040/api/tunnels" ; \
	NGROK_URL=$$(curl -s $$NGROK_API | grep -o '"public_url":"[^"]*' | grep -o 'http[^"]*') ; \
	source .dev.vars ; \
	curl "https://api.telegram.org/bot$$TG_TOKEN/setWebhook?max_connections=1&drop_pending_updates=true&secret_token=$$TG_SECRET_TOKEN&url=$$NGROK_URL"
