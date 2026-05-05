# Demo Reset Safety

The seeded tenant is designed for public recruiter demos. Reset restores Northstar Retail Group to the walkthrough state without touching non-demo tenants.

## UI Reset

- Visible only to `SYSTEM_ADMIN` users.
- Available from the dashboard header as `Reset Demo Data`.
- The frontend asks for confirmation before calling the reset endpoint.
- The backend checks that the authenticated tenant is marked `demoMode=true`.

## API Guardrails

Endpoint:

```text
POST /api/v1/demo/reset
```

Rules:

- Requires authentication.
- Requires `SYSTEM_ADMIN`.
- Refuses non-demo tenants.
- Accepts optional `X-Demo-Reset-Secret`; if supplied, it must match `APP_DEMO_RESET_SECRET`.
- Writes fresh seeded rows for the demo tenant only.

## Public Demo Recommendation

For a hosted demo, keep reset available to the seeded system admin account but use a long random `APP_DEMO_RESET_SECRET` and avoid sharing direct reset API instructions. The UI flow is enough for recruiters and preserves the story that reset is deliberate, scoped, and auditable.
