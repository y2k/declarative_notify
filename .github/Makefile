run: hook
	wrangler dev

build:
	clj2js ../src/main.clj > _build_/main.js && clear && \
	clj2js ../src/vendor/effects.clj > _build_/vendor/effects.js && clear

test:
	clj2js ../src/main.clj > _build_/main.js && clear && \
	clj2js ../src/vendor/effects.clj > _build_/vendor/effects.js && clear && \
	clj2js ../test/test.clj > _build_/test.js && clear && \
	node --env-file=.dev.vars _build_/test.js

migrate:
	wrangler d1 execute DECLARATIVE_NOTIFY_BOT_DB --local --file=schema.sql

hook:
	@NGROK_API="http://localhost:4040/api/tunnels" ; \
	NGROK_URL=$$(curl -s $$NGROK_API | grep -o '"public_url":"[^"]*' | grep -o 'http[^"]*') ; \
	source .dev.vars ; \
	curl "https://api.telegram.org/bot$$TG_TOKEN/setWebhook?max_connections=1&drop_pending_updates=true&secret_token=$$TG_SECRET_TOKEN&url=$$NGROK_URL"

.PHONY: run build test migrate hook
