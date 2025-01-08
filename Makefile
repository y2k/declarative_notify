.PHONY: test
test: build
	@ clear && clj2js js ../test/test.clj > bin/test/test.js
	@ node --env-file=.dev.vars bin/test/test.js

.PHONY: run
run: hook
	@ wrangler dev --test-scheduled

.PHONY: e2e_test
e2e_test: test
	@ echo '{"type": "module", "devDependencies": {"wrangler": "^3.38.0"}}' > bin/package.json
	@ cd bin && yarn
	@ clear && clj2js js ../test/main_test.clj > bin/test/main_test.js
	@ clear && clj2js js ../test/e2e.test.clj > bin/test/e2e.test.js
	@ node --env-file=.dev.vars bin/test/e2e.test.js

.PHONY: build
build:
	@ mkdir -p bin/src && mkdir -p bin/test && mkdir -p bin/vendor/packages/effects && mkdir -p bin/vendor/packages/cf-xmlparser
	@ echo '{"type": "module"}' > bin/package.json
	@ clear && clj2js js ../vendor/packages/effects/effects.2.clj > bin/vendor/packages/effects/effects.2.js
	@ clear && clj2js js ../vendor/packages/cf-xmlparser/xml_parser.clj > bin/vendor/packages/cf-xmlparser/xml_parser.js
	@ clear && clj2js js ../src/core.clj > bin/src/core.js
	@ clear && clj2js js ../src/main.clj > bin/src/main.js

.PHONY: clean
clean:
	@ rm -rf bin

.PHONY: migrate
migrate:
	@ wrangler d1 execute DECLARATIVE_NOTIFY_BOT_DB --local --file=schema.sql

.PHONY: hook
hook:
	@NGROK_API="http://localhost:4040/api/tunnels" ; \
	NGROK_URL=$$(curl -s $$NGROK_API | grep -o '"public_url":"[^"]*' | grep -o 'http[^"]*') ; \
	source .dev.vars ; \
	curl "https://api.telegram.org/bot$$TG_TOKEN/setWebhook?max_connections=1&drop_pending_updates=true&secret_token=$$TG_SECRET_TOKEN&url=$$NGROK_URL"
