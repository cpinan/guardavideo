.PHONY: install dev-api dev-api-debug dev-web share lint typecheck test test-fast check fmt clean

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

share:  ## Frontend visible en tu red/Tailscale (la API sigue solo en localhost, vía el proxy de Vite)
	@echo "Teléfono en la misma Wi-Fi: http://$$(ipconfig getifaddr en0 2>/dev/null || hostname -I | cut -d' ' -f1):5173"
	@command -v tailscale >/dev/null && echo "Con Tailscale: http://$$(tailscale status --json 2>/dev/null | python3 -c 'import sys,json;print(json.load(sys.stdin)["Self"]["DNSName"].rstrip("."))'):5173" || true
	cd frontend && npm run dev -- --host
