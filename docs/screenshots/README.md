# Screenshot Capture Guide

This folder intentionally contains instructions instead of committed screenshots. Capture fresh images from the current build before publishing the repository or portfolio page.

## Setup

1. Start the app with `docker compose up --build`.
2. Open `http://localhost:3000`.
3. Use a desktop viewport around `1440x1000`.
4. Capture PNGs with clear filenames and avoid browser chrome when possible.

## Recommended Screenshots

| File name | Account | Screen |
|---|---|---|
| `01-login.png` | Any | Login page with role quick-fill buttons |
| `02-manager-command-center.png` | `manager@demo.hcm.local` | Command Center priority work and metrics |
| `03-scheduling-week.png` | `manager@demo.hcm.local` | Scheduling tab with draft week, violations, and shift grid |
| `04-timesheets-manager.png` | `manager@demo.hcm.local` | Timesheets pending queue and detail panel |
| `05-leave-approvals.png` | `manager@demo.hcm.local` | Leave tab with pending requests and conflict chip |
| `06-payroll-preview.png` | `payroll@demo.hcm.local` | Payroll preview detail with employee explanation text |
| `07-integrations.png` | `admin@demo.hcm.local` | Integration Center import/export/webhook sections |
| `08-audit-filters.png` | `admin@demo.hcm.local` | Audit tab with filters applied and metadata dialog open |

## Capture Tips

- Prefer seeded records that show explanation text, validation warnings, and audit metadata.
- Keep screenshots readable at GitHub README width.
- Do not capture real credentials beyond the seeded demo account emails.
- If screenshots are added later, reference them from the README with relative paths such as `docs/screenshots/06-payroll-preview.png`.
