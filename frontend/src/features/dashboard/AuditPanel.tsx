import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogContent,
  DialogTitle,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography
} from "@mui/material";
import { useQuery } from "@tanstack/react-query";
import { Filter, RotateCcw, ShieldCheck } from "lucide-react";
import { useState } from "react";
import { api } from "../../api/client";
import type { AuditLog, AuditLogFilters, UserRole } from "../../types/api";
import { useAuth } from "../auth/AuthContext";

const auditRoles: UserRole[] = ["HR_ADMIN", "PAYROLL_ADMIN", "SYSTEM_ADMIN"];

interface AuditFilterDraft {
  from: string;
  to: string;
  actorEmail: string;
  actionType: string;
  entityType: string;
  entityId: string;
  limit: string;
}

const defaultDraft: AuditFilterDraft = {
  from: "",
  to: "",
  actorEmail: "",
  actionType: "",
  entityType: "",
  entityId: "",
  limit: "50"
};

export function AuditPanel() {
  const { user } = useAuth();
  const canViewAudit = Boolean(user?.roles.some((role) => auditRoles.includes(role)));
  const [draft, setDraft] = useState<AuditFilterDraft>(defaultDraft);
  const [filters, setFilters] = useState<AuditLogFilters>({ limit: 50 });
  const [selected, setSelected] = useState<AuditLog | null>(null);

  const auditQuery = useQuery({
    queryKey: ["audit", filters],
    queryFn: () => api.auditLogs(filters),
    enabled: canViewAudit
  });

  if (!canViewAudit) {
    return <Alert severity="info">Audit logs are available to HR, payroll, and system administrators.</Alert>;
  }

  const rows = auditQuery.data ?? [];

  return (
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} alignItems="center">
        <ShieldCheck size={20} />
        <Box>
          <Typography variant="h6">Audit log browser</Typography>
          <Typography variant="body2" color="text.secondary">
            Filter tenant-scoped workflow history and inspect the payload behind each decision.
          </Typography>
        </Box>
      </Stack>

      <Paper className="audit-filter-panel" elevation={0}>
        <Box className="audit-filter-grid">
          <TextField
            label="From"
            type="datetime-local"
            size="small"
            value={draft.from}
            onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="To"
            type="datetime-local"
            size="small"
            value={draft.to}
            onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))}
            InputLabelProps={{ shrink: true }}
          />
          <TextField
            label="Actor email"
            size="small"
            value={draft.actorEmail}
            onChange={(event) => setDraft((current) => ({ ...current, actorEmail: event.target.value }))}
          />
          <TextField
            label="Action type"
            size="small"
            value={draft.actionType}
            onChange={(event) => setDraft((current) => ({ ...current, actionType: event.target.value }))}
          />
          <TextField
            label="Entity type"
            size="small"
            value={draft.entityType}
            onChange={(event) => setDraft((current) => ({ ...current, entityType: event.target.value }))}
          />
          <TextField
            label="Entity id"
            size="small"
            value={draft.entityId}
            onChange={(event) => setDraft((current) => ({ ...current, entityId: event.target.value }))}
          />
          <TextField
            label="Limit"
            type="number"
            size="small"
            value={draft.limit}
            onChange={(event) => setDraft((current) => ({ ...current, limit: event.target.value }))}
            inputProps={{ min: 1, max: 100 }}
          />
        </Box>
        <Stack direction="row" flexWrap="wrap" gap={1} sx={{ mt: 2 }}>
          <Button variant="contained" startIcon={<Filter size={16} />} onClick={() => setFilters(toFilters(draft))}>
            Apply filters
          </Button>
          <Button
            variant="outlined"
            startIcon={<RotateCcw size={16} />}
            onClick={() => {
              setDraft(defaultDraft);
              setFilters({ limit: 50 });
            }}
          >
            Reset
          </Button>
          <Chip size="small" label={`${rows.length} row${rows.length === 1 ? "" : "s"}`} />
        </Stack>
      </Paper>

      {auditQuery.error ? <Alert severity="error">{auditQuery.error.message}</Alert> : null}

      <Table size="small" className="data-table">
        <TableHead>
          <TableRow>
            <TableCell>Timestamp</TableCell>
            <TableCell>Actor</TableCell>
            <TableCell>Action</TableCell>
            <TableCell>Entity</TableCell>
            <TableCell>Entity id</TableCell>
            <TableCell>Details</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {auditQuery.isLoading ? (
            <TableRow>
              <TableCell colSpan={6}>Loading audit activity...</TableCell>
            </TableRow>
          ) : rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6}>No audit records match these filters.</TableCell>
            </TableRow>
          ) : (
            rows.map((row) => (
              <TableRow key={row.id}>
                <TableCell>{formatTimestamp(row.timestamp)}</TableCell>
                <TableCell>{row.actorEmail}</TableCell>
                <TableCell>{row.actionType}</TableCell>
                <TableCell>{row.entityType}</TableCell>
                <TableCell className="mono-cell">{row.entityId ?? "None"}</TableCell>
                <TableCell>
                  <Button size="small" variant="outlined" onClick={() => setSelected(row)}>
                    Inspect
                  </Button>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>

      <Dialog open={Boolean(selected)} onClose={() => setSelected(null)} fullWidth maxWidth="md">
        <DialogTitle>{selected?.actionType ?? "Audit details"}</DialogTitle>
        <DialogContent>
          {selected ? (
            <Stack spacing={2}>
              <Stack direction="row" flexWrap="wrap" gap={1}>
                <Chip label={selected.actorEmail} />
                <Chip label={selected.entityType} />
                <Chip label={formatTimestamp(selected.timestamp)} />
              </Stack>
              <AuditJsonBlock title="Previous value" value={selected.previousValue} />
              <AuditJsonBlock title="New value" value={selected.newValue} />
              <AuditJsonBlock title="Metadata" value={selected.metadata} />
            </Stack>
          ) : null}
        </DialogContent>
      </Dialog>
    </Stack>
  );
}

function AuditJsonBlock({ title, value }: { title: string; value?: string }) {
  return (
    <Box>
      <Typography variant="subtitle2">{title}</Typography>
      <Box component="pre" className="audit-json-block">
        {prettyJson(value)}
      </Box>
    </Box>
  );
}

function toFilters(draft: AuditFilterDraft): AuditLogFilters {
  return {
    from: toIso(draft.from),
    to: toIso(draft.to),
    actorEmail: clean(draft.actorEmail),
    actionType: clean(draft.actionType),
    entityType: clean(draft.entityType),
    entityId: clean(draft.entityId),
    limit: clampLimit(draft.limit)
  };
}

function toIso(value: string) {
  if (!value) {
    return undefined;
  }
  return new Date(value).toISOString();
}

function clean(value: string) {
  return value.trim() || undefined;
}

function clampLimit(value: string) {
  const parsed = Number.parseInt(value, 10);
  if (Number.isNaN(parsed)) {
    return 50;
  }
  return Math.max(1, Math.min(parsed, 100));
}

function formatTimestamp(value: string) {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short"
  }).format(new Date(value));
}

function prettyJson(value?: string) {
  if (!value) {
    return "None";
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
