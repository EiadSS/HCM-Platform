# Deployment Notes

This is a portfolio demo, not a production payroll system. The cheapest polished public setup is a Render backend, a Vercel frontend, and a free managed PostgreSQL database such as Neon, with seeded demo mode enabled.

## Required Services

- Backend: Spring Boot container, port `8080`.
- Frontend: static React build on Vercel, or the included Nginx container if using Docker Compose.
- Database: PostgreSQL 16-compatible instance with persistent storage.
- Redis: optional placeholder service for future async workers; the current app does not require it for core workflows.

## Recommended Low-Cost Portfolio Deployment

- GitHub: source code.
- Render Web Service: backend from `backend/Dockerfile`.
- Vercel Project: frontend from `frontend`, using `npm ci && npm run build` and `dist`.
- Neon Postgres: free managed PostgreSQL database.

The Vercel frontend includes `vercel.json` so React Router routes fall back to `index.html`. The login page pings `/actuator/health` on load to wake a free Render backend and displays a clear first-load notice.

## Production Environment Variables

Backend:

- `SPRING_DATASOURCE_URL`: JDBC URL for the hosted PostgreSQL database.
- `SPRING_DATASOURCE_USERNAME`: database user.
- `SPRING_DATASOURCE_PASSWORD`: database password.
- `APP_JWT_SECRET`: long random secret, at least 32 characters.
- `APP_CORS_ALLOWED_ORIGINS`: comma-separated frontend origins, for example `https://hcm-demo.example.com`.
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS`: optional comma-separated origin patterns. For Vercel demos, `https://*.vercel.app` prevents CORS breakage when the deployment URL changes.
- `APP_SEED_DEMO`: keep `true` for a public recruiter demo; set `false` only for non-demo data.
- `APP_DEMO_RESET_SECRET`: long random reset guard for API reset calls.
- `APP_ANALYTICS_OWNER_KEY`: private code for the hidden `/analytics` owner page. Do not put this in Vercel/frontend env vars.
- `APP_ANALYTICS_ENABLED`: keep `true` to collect lightweight first-party demo usage stats.

Frontend build arg:

- `VITE_API_BASE_URL`: public browser-facing API URL, for example `https://hcm-api.example.com/api/v1`.

Vercel frontend environment:

- `VITE_API_BASE_URL`: Render backend URL with `/api/v1`, for example `https://your-backend.onrender.com/api/v1`.

Render backend environment:

- Set `APP_CORS_ALLOWED_ORIGINS` to the exact Vercel frontend origin, for example `https://your-project.vercel.app`.

## Public Demo Checklist

1. Use HTTPS for frontend and backend URLs.
2. Set `APP_CORS_ALLOWED_ORIGINS` to the exact frontend origin, or set `APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://*.vercel.app` for Vercel-hosted demos.
3. Use a persistent database volume or managed PostgreSQL instance.
4. Keep demo mode enabled only for the seeded portfolio tenant.
5. Rotate `APP_JWT_SECRET` and `APP_DEMO_RESET_SECRET` before publishing the URL.
6. Set `APP_ANALYTICS_OWNER_KEY` to a private value, then open `/analytics` on the frontend and confirm the code gate works.
7. Confirm `/actuator/health` returns `UP`.
8. Run `docker compose config` and the verification commands from the README before sharing.
9. Open the deployed frontend after 15+ minutes of inactivity and confirm the backend wake-up notice appears before login.

## Tradeoffs

- The app intentionally includes quick-fill demo accounts for recruiter review.
- Free Render backend instances can sleep after inactivity. The frontend wakes the backend on the login page, but the first sign-in can still take about a minute.
- The hidden analytics page uses anonymous browser IDs and successful-login events. It is meant for portfolio traffic visibility, not production analytics/compliance.
- Payroll is a gross-pay preview only. It does not calculate taxes, deductions, filing, or compliance outcomes.
- Webhooks are simulated event history and delivery attempts; no real outbound delivery is attempted.
