# Start Here

This project is already a runnable full-stack portfolio demo. The easiest path is Docker.

## 1. Install/Open Docker Desktop

Install Docker Desktop for Windows if you do not already have it, then open Docker Desktop and wait until it says it is running.

## 2. Start The App

From this project folder, run:

```bat
start-demo.cmd
```

Or run manually:

```bash
docker compose up --build
```

## 3. Open The Demo

- Frontend: `http://localhost:3000`
- Backend Swagger docs: `http://localhost:8080/swagger-ui.html`
- Backend health: `http://localhost:8080/actuator/health`

## 10-Minute Recruiter Walkthrough

Use any quick-fill button on the login page.

1. Start as manager:

   - Email: `manager@demo.hcm.local`
   - Password: `DemoPass123!`

2. Open Scheduling, fix the draft week's blocking validation issue, optionally assign the open shift, validate, and publish.
3. Open Timesheets, approve a submitted week or decide Maya's change request.
4. Open Leave, approve Jordan's conflict-warning vacation request or Amara's sick request.
5. Switch to `employee@demo.hcm.local`, open Timesheets, clock out from the active punch, and review leave balances/request status.
6. Switch to `payroll@demo.hcm.local`, open Payroll, generate the previous-week gross-pay preview, and review the employee calculation explanations.
7. Open Integrations as payroll/admin to export approved timesheets as CSV/JSON, inspect webhook payloads, and redeliver a failed simulated event.
8. Open Integrations as HR/admin to preview the sample employee CSV, review row errors, download the error report, and commit valid rows.
9. Open Audit as HR/payroll/admin, filter by action or actor, and inspect metadata behind a decision.
10. Optionally log in as `admin@demo.hcm.local` and click `Reset Demo Data` to restore the seeded walkthrough.

All demo accounts use `DemoPass123!`.

Owner-only usage stats are available at `/analytics` on the frontend URL. This route is intentionally not linked in the app and requires the private `APP_ANALYTICS_OWNER_KEY` backend value.

## If Docker Is Not Running

If you see an error about `dockerDesktopLinuxEngine`, open Docker Desktop first, wait for it to finish starting, then run `start-demo.cmd` again.
