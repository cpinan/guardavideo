.PHONY: install dev-api dev-api-debug dev-web share serve tailscale-serve ui-audit android-check android-install android-release ios-build lint typecheck test test-fast check fmt clean

install:  ## Instala dependencias de backend y frontend
	cd backend && pip install -e ".[dev]"
	cd frontend && npm install

dev-api:  ## API en http://localhost:8000 (docs en /docs)
	cd backend && uvicorn app.main:app --reload

dev-web:  ## Frontend en http://localhost:5173
	cd frontend && npm run dev

fmt:  ## Formatea el backend
	cd backend && ruff check . --fix && ruff format .

lint:
	cd backend && ruff check . && ruff format --check .
	cd frontend && npm run lint

typecheck:
	cd backend && mypy app tests
	cd frontend && npx tsc --noEmit

test-fast:  ## Backend sin pruebas de integración (no requiere ffmpeg)
	cd backend && pytest -q -m "not integration"

test:
	cd backend && pytest -q
	cd frontend && npm run test

check: lint typecheck test  ## Todo lo que corre el CI
	cd frontend && npm run build

clean:
	rm -rf backend/downloads backend/.pytest_cache backend/.mypy_cache backend/.ruff_cache frontend/dist

dev-api-debug:  ## API con escenarios simulados (http://debug.test/<escenario>) y URLs locales
	cd backend && HLSWEB_DEBUG=true HLSWEB_ALLOW_PRIVATE_URLS=true uvicorn app.main:app --reload

serve:  ## Producción local para compartir: frontend compilado + API en 127.0.0.1:8080, sin modo debug ni URLs privadas
	cd frontend && npm run build
	cd backend && HLSWEB_DEBUG=false HLSWEB_ALLOW_PRIVATE_URLS=false HLSWEB_STATIC_DIR=../frontend/dist \
		uvicorn app.main:app --host 127.0.0.1 --port 8080

tailscale-serve:  ## HTTPS solo dentro de tu tailnet hacia :8080 (Tailscale con sesión iniciada y HTTPS activado)
	tailscale serve --bg 8080
	tailscale serve status

android-check:  ## App Android: tests del módulo compartido (KMP) + tests de la app + APK debug
	cd android && ./gradlew --console=plain -q :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug

android-install:  ## Compila e instala la app Android en el emulador/dispositivo ($$ANDROID_SERIAL)
	android/tools/dev.sh install

android-release:  ## App Android optimizada (R8, ~60 MB) instalada sobre la de depuración sin perder la biblioteca
	android/tools/dev.sh release

ios-build:  ## App iOS: genera el proyecto (XcodeGen) y compila para el simulador (incluye el framework KMP)
	cd ios && xcodegen -q && xcodebuild -project Guardavideo.xcodeproj -scheme Guardavideo -sdk iphonesimulator \
		-destination 'generic/platform=iOS Simulator' -derivedDataPath build/dd CODE_SIGNING_ALLOWED=NO -quiet build

ui-audit:  ## Capturas + revisión de desborde/objetivos táctiles en Chrome headless (requiere dev-api y dev-web)
	node tools/ui-audit.mjs http://localhost:5173/ ui-audit-out

share:  ## Frontend visible en tu red/Tailscale (la API sigue solo en localhost, vía el proxy de Vite)
	@echo "Teléfono en la misma Wi-Fi: http://$$(ipconfig getifaddr en0 2>/dev/null || hostname -I | cut -d' ' -f1):5173"
	@command -v tailscale >/dev/null && echo "Con Tailscale: http://$$(tailscale status --json 2>/dev/null | python3 -c 'import sys,json;print(json.load(sys.stdin)["Self"]["DNSName"].rstrip("."))'):5173" || true
	cd frontend && npm run dev -- --host
