# Deployment Notes

This is a portfolio demo, not a production payroll system. The recommended public setup is a Railway backend, a Vercel frontend, and a managed PostgreSQL database such as Neon, with seeded demo mode enabled.

## Required Services

- Backend: Spring Boot container. The app reads Railway's `PORT` variable through `server.port=${PORT:8080}`.
- Frontend: static React build on Vercel, or the included Nginx container if using Docker Compose.
- Database: PostgreSQL-compatible instance with persistent storage.
- Redis: optional placeholder service for future async workers; the current app does not require it for core workflows.

## Recommended Portfolio Deployment

- GitHub: source code.
- Railway Web Service: backend from `backend/Dockerfile`, with root directory set to `backend`/`/backend`.
- Vercel Project: frontend from `frontend`, using `npm ci && npm run build` and `dist`.
- Neon Postgres: managed PostgreSQL database.

The Vercel frontend includes `vercel.json` so React Router routes fall back to `index.html`. The login page checks `/actuator/health` before sign-in and shows a clear backend-health notice.

## Railway Backend Setup

1. In Railway, create a new project and choose **Deploy from GitHub repo**.
2. Select this repository.
3. In the backend service settings, set **Root Directory** to `backend` or `/backend` depending on how Railway displays repo-relative paths. Railway's monorepo docs recommend setting a service root directory for isolated apps in a monorepo.
4. Confirm Railway is using the `backend/Dockerfile`. Railway detects a `Dockerfile` at the service source root; alternatively set `RAILWAY_DOCKERFILE_PATH=Dockerfile`.
5. Open **Settings > Networking** and generate a public Railway domain, usually `https://your-service.up.railway.app`.
6. Keep Railway **Serverless/App Sleep disabled** if you do not want cold starts. Railway's Serverless feature intentionally sleeps inactive services after inactivity.
7. Add the backend environment variables below and deploy.
8. Set the Railway healthcheck path to `/actuator/health`, then confirm `https://your-service.up.railway.app/actuator/health` returns `UP`.

## Production Environment Variables

Backend:

- `SPRING_DATASOURCE_URL`: JDBC URL for the hosted PostgreSQL database.
- `SPRING_DATASOURCE_USERNAME`: database user.
- `SPRING_DATASOURCE_PASSWORD`: database password.
- `APP_JWT_SECRET`: long random secret, at least 32 characters.
- `APP_CORS_ALLOWED_ORIGINS`: comma-separated frontend origins, for example `https://your-project.vercel.app`.
- `APP_CORS_ALLOWED_ORIGIN_PATTERNS`: optional comma-separated origin patterns. For Vercel demos, `https://*.vercel.app` prevents CORS breakage when the deployment URL changes.
- `APP_SEED_DEMO`: keep `true` for a public portfolio demo; set `false` only for non-demo data.
- `APP_DEMO_RESET_SECRET`: long random reset guard for API reset calls.
- `APP_ANALYTICS_OWNER_KEY`: private code for the hidden `/analytics` owner page. Do not put this in Vercel/frontend env vars.
- `APP_ANALYTICS_ENABLED`: keep `true` to collect lightweight first-party demo usage stats.

Vercel frontend environment:

- `VITE_API_BASE_URL`: Railway backend URL with `/api/v1`, for example `https://your-service.up.railway.app/api/v1`.

## Render Shutdown Checklist

Use this after the Railway backend is live and Vercel points at Railway.

1. In Vercel, update `VITE_API_BASE_URL` to the Railway backend URL and redeploy the frontend.
2. Confirm login, dashboard load, `Reset Demo Data`, and `/analytics` work from the Vercel site.
3. In Render, open the old backend service.
4. Disable automatic deploys from the service settings so new pushes do not restart it.
5. If you want a reversible pause, suspend the Render service from the dashboard.
6. If you want to fully stop using Render, delete the Render web service from its settings/danger area after Railway is verified.
7. Remove old Render-specific URLs from Vercel env vars, bookmarks, and README snippets.
8. Check Render billing/usage after deletion to make sure no old services remain.

## Public Demo Checklist

1. Use HTTPS for frontend and backend URLs.
2. Set `APP_CORS_ALLOWED_ORIGINS` to the exact frontend origin, or set `APP_CORS_ALLOWED_ORIGIN_PATTERNS=https://*.vercel.app` for Vercel-hosted demos.
3. Use a persistent managed PostgreSQL instance.
4. Keep demo mode enabled only for the seeded portfolio tenant.
5. Rotate `APP_JWT_SECRET` and `APP_DEMO_RESET_SECRET` before publishing the URL.
6. Set `APP_ANALYTICS_OWNER_KEY` to a private value, then open `/analytics` on the frontend and confirm the code gate works.
7. Confirm `/actuator/health` returns `UP`.
8. Run `docker compose config` and the verification commands from the README before sharing.
9. Confirm Railway Serverless/App Sleep is disabled if you want the backend to stay warm.

## Tradeoffs

- The app intentionally includes quick-fill demo accounts for public review.
- Railway can sleep services only when Serverless/App Sleep is enabled; leave it disabled to avoid the Render-style cold-start experience.
- The hidden analytics page uses anonymous browser IDs and successful-login events. It is meant for portfolio traffic visibility, not production analytics/compliance.
- Payroll is a gross-pay preview only. It does not calculate taxes, deductions, filing, or compliance outcomes.
- Webhooks are simulated event history and delivery attempts; no real outbound delivery is attempted.
