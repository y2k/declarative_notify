run: hook
	wrangler dev --test-scheduled

schedule:
	clear && curl "http://localhost:8787/__scheduled?cron=*+*+*+*+*"

test: build
	clear && clj2js ../test/test.clj > bin/test.js && \
	node --env-file=.dev.vars bin/test.js

build:
	clear && clj2js prelude > bin/prelude.js && \
	clear && clj2js ../src/main.clj > bin/main.js && \
	clear && clj2js prelude > bin/vendor/prelude.js && \
	clear && clj2js ../src/vendor/effects.clj > bin/vendor/effects.js

migrate:
	wrangler d1 execute DECLARATIVE_NOTIFY_BOT_DB --local --file=schema.sql

hook:
	@NGROK_API="http://localhost:4040/api/tunnels" ; \
	NGROK_URL=$$(curl -s $$NGROK_API | grep -o '"public_url":"[^"]*' | grep -o 'http[^"]*') ; \
	source .dev.vars ; \
	curl "https://api.telegram.org/bot$$TG_TOKEN/setWebhook?max_connections=1&drop_pending_updates=true&secret_token=$$TG_SECRET_TOKEN&url=$$NGROK_URL"

.PHONY: run test build migrate hook
