run: hook
	wrangler dev --test-scheduled

e2e_test: test
	@ echo '{"type": "module", "devDependencies": {"wrangler": "^3.38.0"}}' > bin/package.json
	@ cd bin && yarn
	@ clear && clj2js js ../test/e2e.test.clj > bin/e2e.test.js
	@ node --env-file=.dev.vars bin/e2e.test.js

test: build
	clear && clj2js js ../test/test.clj > bin/test.js && \
	node --env-file=.dev.vars bin/test.js

build:
	@ mkdir -p bin/vendor
	@ echo '{"type": "module"}' > bin/package.json
	@ clear && clj2js js prelude > bin/prelude.js
	@ clear && clj2js js ../src/xml_parser.clj > bin/xml_parser.js
	@ clear && clj2js js ../src/main.clj > bin/main.js
	@ clear && clj2js js prelude > bin/vendor/prelude.js
	@ clear && clj2js js ../vendor/effects/src/effects.clj > bin/vendor/effects.js

migrate:
	wrangler d1 execute DECLARATIVE_NOTIFY_BOT_DB --local --file=schema.sql

hook:
	@NGROK_API="http://localhost:4040/api/tunnels" ; \
	NGROK_URL=$$(curl -s $$NGROK_API | grep -o '"public_url":"[^"]*' | grep -o 'http[^"]*') ; \
	source .dev.vars ; \
	curl "https://api.telegram.org/bot$$TG_TOKEN/setWebhook?max_connections=1&drop_pending_updates=true&secret_token=$$TG_SECRET_TOKEN&url=$$NGROK_URL"

.PHONY: run test_run test build migrate hook
