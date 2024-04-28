PRELUDE_PATH := $(shell realpath ../vendor/prelude/js/src/prelude.clj)

run: hook
	@ wrangler dev --test-scheduled

e2e_test: test
	@ echo '{"type": "module", "devDependencies": {"wrangler": "^3.38.0"}}' > bin/package.json
	@ cd bin && yarn
	@ clear && clj2js js ../test/main_test.clj $(PRELUDE_PATH) > bin/test/main_test.js
	@ clear && clj2js js ../test/e2e.test.clj $(PRELUDE_PATH) > bin/test/e2e.test.js
	@ node --env-file=.dev.vars bin/test/e2e.test.js

test: build
	@ clear && clj2js js ../test/test.clj $(PRELUDE_PATH) > bin/test/test.js
	@ node --env-file=.dev.vars bin/test/test.js

build:
	@ mkdir -p bin/src && mkdir -p bin/test && mkdir -p bin/vendor/packages/effects && mkdir -p bin/vendor/packages/cf-xmlparser
	@ echo '{"type": "module"}' > bin/package.json
	@ clear && clj2js js ../vendor/packages/effects/effects.2.clj $(PRELUDE_PATH) > bin/vendor/packages/effects/effects.2.js
	@ clear && clj2js js ../vendor/packages/cf-xmlparser/xml_parser.clj $(PRELUDE_PATH) > bin/vendor/packages/cf-xmlparser/xml_parser.js
	@ clear && clj2js js ../src/core.clj $(PRELUDE_PATH) > bin/src/core.js
	@ clear && clj2js js ../src/main.clj $(PRELUDE_PATH) > bin/src/main.js

clean:
	@ rm -rf bin

migrate:
	@ wrangler d1 execute DECLARATIVE_NOTIFY_BOT_DB --local --file=schema.sql

hook:
	@NGROK_API="http://localhost:4040/api/tunnels" ; \
	NGROK_URL=$$(curl -s $$NGROK_API | grep -o '"public_url":"[^"]*' | grep -o 'http[^"]*') ; \
	source .dev.vars ; \
	curl "https://api.telegram.org/bot$$TG_TOKEN/setWebhook?max_connections=1&drop_pending_updates=true&secret_token=$$TG_SECRET_TOKEN&url=$$NGROK_URL"

.PHONY: run test_run test build clean migrate hook
