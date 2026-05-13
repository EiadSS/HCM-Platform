import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Container,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import { BarChart3, Lock, RefreshCw } from "lucide-react";
import { api } from "../../api/client";
import type { AnalyticsEvent, AnalyticsSummary } from "../../types/api";

const OWNER_CODE_SESSION_KEY = "hcm_demo_analytics_owner_code";

export function AnalyticsPage() {
  const [codeInput, setCodeInput] = useState("");
  const [ownerCode, setOwnerCode] = useState<string | null>(() => sessionStorage.getItem(OWNER_CODE_SESSION_KEY));
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [events, setEvents] = useState<AnalyticsEvent[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function loadAnalytics(code: string) {
    setLoading(true);
    setError(null);
    try {
      const filters = {
        from: from ? `${from}T00:00:00Z` : undefined,
        to: to ? `${to}T23:59:59Z` : undefined
      };
      const [nextSummary, nextEvents] = await Promise.all([
        api.analyticsSummary(code, filters),
        api.analyticsEvents(code, 50)
      ]);
      sessionStorage.setItem(OWNER_CODE_SESSION_KEY, code);
      setOwnerCode(code);
      setSummary(nextSummary);
      setEvents(nextEvents);
    } catch (err) {
      sessionStorage.removeItem(OWNER_CODE_SESSION_KEY);
      setOwnerCode(null);
      setSummary(null);
      setEvents([]);
      setError(err instanceof Error ? err.message : "Unable to load analytics");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    const savedCode = sessionStorage.getItem(OWNER_CODE_SESSION_KEY);
    if (savedCode) {
      void loadAnalytics(savedCode);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const authorized = Boolean(ownerCode && summary);

  return (
    <Box className="analytics-shell">
      <Container maxWidth="xl">
        <Stack spacing={3}>
          <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" alignItems={{ xs: "flex-start", md: "center" }} spacing={2}>
            <Box>
              <Chip icon={<Lock size={16} />} label="Private owner view" variant="outlined" sx={{ mb: 1 }} />
              <Typography variant="h3" fontWeight={900}>
                Portfolio Analytics
              </Typography>
              <Typography color="text.secondary">
                First-party demo usage stats. This page is hidden from the public app and protected by your owner code.
              </Typography>
            </Box>
            {authorized ? (
              <Button
                variant="outlined"
                onClick={() => {
                  sessionStorage.removeItem(OWNER_CODE_SESSION_KEY);
                  setOwnerCode(null);
                  setSummary(null);
                  setEvents([]);
                  setCodeInput("");
                }}
              >
                Lock analytics
              </Button>
            ) : null}
          </Stack>

          {!authorized ? (
            <Card className="analytics-code-card">
              <CardContent>
                <Stack spacing={2} component="form" onSubmit={(event) => {
                  event.preventDefault();
                  void loadAnalytics(codeInput);
                }}>
                  <Typography variant="h6">Enter owner code</Typography>
                  <TextField
                    label="Owner analytics code"
                    type="password"
                    value={codeInput}
                    onChange={(event) => setCodeInput(event.target.value)}
                    autoFocus
                    fullWidth
                  />
                  {error ? <Alert severity="error">{error}</Alert> : null}
                  <Button type="submit" variant="contained" disabled={!codeInput || loading} startIcon={loading ? <CircularProgress size={16} /> : <Lock size={16} />}>
                    Unlock analytics
                  </Button>
                </Stack>
              </CardContent>
            </Card>
          ) : null}

          {authorized && summary ? (
            <>
              <Stack direction={{ xs: "column", md: "row" }} spacing={2} className="analytics-filter-panel">
                <TextField label="From" type="date" value={from} onChange={(event) => setFrom(event.target.value)} InputLabelProps={{ shrink: true }} />
                <TextField label="To" type="date" value={to} onChange={(event) => setTo(event.target.value)} InputLabelProps={{ shrink: true }} />
                <Button variant="outlined" startIcon={<RefreshCw size={16} />} disabled={loading || !ownerCode} onClick={() => ownerCode && loadAnalytics(ownerCode)}>
                  Refresh
                </Button>
              </Stack>

              {error ? <Alert severity="error">{error}</Alert> : null}

              <Box className="analytics-metric-grid">
                <AnalyticsCard label="Total visits" value={summary.totalVisits} detail="Tracked page views" />
                <AnalyticsCard label="Unique visitors" value={summary.uniqueVisitors} detail="Anonymous browser IDs" />
                <AnalyticsCard label="Active now" value={summary.activeVisitors} detail="Seen in last 10 minutes" />
                <AnalyticsCard label="Total logins" value={summary.totalLogins} detail="Successful shared-account logins" />
                <AnalyticsCard label="Last used" value={formatDateTime(summary.lastUsedAt)} detail="Most recent analytics event" />
              </Box>

              <Stack direction={{ xs: "column", lg: "row" }} spacing={2}>
                <Card className="analytics-panel">
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Top pages
                    </Typography>
                    <MetricList rows={summary.topPages} emptyText="No page views yet." />
                  </CardContent>
                </Card>
                <Card className="analytics-panel">
                  <CardContent>
                    <Typography variant="h6" gutterBottom>
                      Login roles
                    </Typography>
                    <MetricList rows={summary.loginRoles} emptyText="No logins tracked yet." />
                  </CardContent>
                </Card>
              </Stack>

              <Card className="analytics-panel">
                <CardContent>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 2 }}>
                    <BarChart3 size={20} />
                    <Typography variant="h6">Recent activity</Typography>
                  </Stack>
                  <Table size="small" className="data-table">
                    <TableHead>
                      <TableRow>
                        <TableCell>Time</TableCell>
                        <TableCell>Event</TableCell>
                        <TableCell>Path</TableCell>
                        <TableCell>Account</TableCell>
                        <TableCell>Role</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {events.map((event) => (
                        <TableRow key={event.id}>
                          <TableCell>{formatDateTime(event.occurredAt)}</TableCell>
                          <TableCell>{event.eventType}</TableCell>
                          <TableCell>{event.path ?? "-"}</TableCell>
                          <TableCell>{event.accountEmail ?? "-"}</TableCell>
                          <TableCell>{event.accountRole ?? "-"}</TableCell>
                        </TableRow>
                      ))}
                      {events.length === 0 ? (
                        <TableRow>
                          <TableCell colSpan={5}>No analytics activity recorded yet.</TableCell>
                        </TableRow>
                      ) : null}
                    </TableBody>
                  </Table>
                </CardContent>
              </Card>
            </>
          ) : null}
        </Stack>
      </Container>
    </Box>
  );
}

function AnalyticsCard({ label, value, detail }: { label: string; value: number | string; detail: string }) {
  return (
    <Card className="analytics-metric-card">
      <CardContent>
        <Typography variant="overline" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h5" fontWeight={900}>
          {value}
        </Typography>
        <Typography variant="body2" color="text.secondary">
          {detail}
        </Typography>
      </CardContent>
    </Card>
  );
}

function MetricList({ rows, emptyText }: { rows: { label: string; value: number }[]; emptyText: string }) {
  if (rows.length === 0) {
    return <Typography color="text.secondary">{emptyText}</Typography>;
  }
  return (
    <Stack spacing={1}>
      {rows.map((row) => (
        <Stack key={row.label} direction="row" justifyContent="space-between" gap={2}>
          <Typography>{row.label}</Typography>
          <Chip label={row.value} size="small" />
        </Stack>
      ))}
    </Stack>
  );
}

function formatDateTime(value?: string) {
  if (!value) {
    return "-";
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}
