import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  MenuItem,
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
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { FileDown, History, PlugZap, RefreshCcw, Send, Upload } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import { api } from "../../api/client";
import type { EmployeeImportPreviewRequest, ImportJob, WebhookEvent } from "../../types/api";
import { useAuth } from "../auth/AuthContext";

const canonicalFields = [
  "employeeNumber",
  "firstName",
  "lastName",
  "workEmail",
  "status",
  "employmentType",
  "department",
  "location",
  "jobTitle",
  "managerEmail",
  "hourlyRate",
  "weeklyHourCap",
  "hireDate"
];

const sampleCsv = `Worker ID,Given,Surname,Email Address,State,Type,Team,Site,Role,Manager,Rate,Cap,Hire
NS-030,Ava,Lopez,ava.lopez@northstar.example,ACTIVE,PART_TIME,Retail,Downtown Store,Retail Associate,manager@demo.hcm.local,23.00,24,2026-06-01
NS-004,Existing,Conflict,employee@demo.hcm.local,ACTIVE,PART_TIME,Retail,Downtown Store,Retail Associate,manager@demo.hcm.local,22.00,24,2026-06-02
NS-031,Bad,Department,bad.department@northstar.example,STARTED,PART_TIME,Merch,Downtown Store,Retail Associate,missing@northstar.example,-1,0,bad-date`;

const defaultMapping: Record<string, string> = {
  employeeNumber: "Worker ID",
  firstName: "Given",
  lastName: "Surname",
  workEmail: "Email Address",
  status: "State",
  employmentType: "Type",
  department: "Team",
  location: "Site",
  jobTitle: "Role",
  managerEmail: "Manager",
  hourlyRate: "Rate",
  weeklyHourCap: "Cap",
  hireDate: "Hire"
};

export function IntegrationPanel() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const canImport = Boolean(user?.roles.some((role) => role === "HR_ADMIN" || role === "SYSTEM_ADMIN"));
  const canExport = Boolean(user?.roles.some((role) => role === "PAYROLL_ADMIN" || role === "SYSTEM_ADMIN"));
  const canWebhooks = Boolean(user?.roles.some((role) => role === "HR_ADMIN" || role === "PAYROLL_ADMIN" || role === "SYSTEM_ADMIN"));
  const [fileName, setFileName] = useState("northstar-phase6-preview.csv");
  const [csvContent, setCsvContent] = useState(sampleCsv);
  const [fieldMapping, setFieldMapping] = useState(defaultMapping);
  const [selectedImportId, setSelectedImportId] = useState<string | null>(null);
  const [selectedWebhookId, setSelectedWebhookId] = useState<string | null>(null);

  const headers = useMemo(() => parseHeaders(csvContent), [csvContent]);
  const imports = useQuery({ queryKey: ["imports"], queryFn: api.imports, enabled: canImport });
  const importDetail = useQuery({
    queryKey: ["import-detail", selectedImportId],
    queryFn: () => api.employeeImportDetail(selectedImportId as string),
    enabled: canImport && Boolean(selectedImportId)
  });
  const webhooks = useQuery({ queryKey: ["webhooks"], queryFn: api.webhookEvents, enabled: canWebhooks });
  const webhookDetail = useQuery({
    queryKey: ["webhook-detail", selectedWebhookId],
    queryFn: () => api.webhookEventDetail(selectedWebhookId as string),
    enabled: canWebhooks && Boolean(selectedWebhookId)
  });

  useEffect(() => {
    if (!selectedImportId && imports.data?.length) {
      setSelectedImportId(imports.data[0].id);
    }
  }, [imports.data, selectedImportId]);

  useEffect(() => {
    if (!selectedWebhookId && webhooks.data?.length) {
      setSelectedWebhookId(webhooks.data[0].id);
    }
  }, [selectedWebhookId, webhooks.data]);

  const invalidateIntegrations = () => {
    void queryClient.invalidateQueries({ queryKey: ["imports"] });
    void queryClient.invalidateQueries({ queryKey: ["import-detail"] });
    void queryClient.invalidateQueries({ queryKey: ["employees"] });
    void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };

  const preview = useMutation({
    mutationFn: (request: EmployeeImportPreviewRequest) => api.previewEmployeeImport(request),
    onSuccess: (detail) => {
      setSelectedImportId(detail.job.id);
      invalidateIntegrations();
    }
  });
  const commit = useMutation({
    mutationFn: api.commitEmployeeImport,
    onSuccess: (detail) => {
      setSelectedImportId(detail.job.id);
      invalidateIntegrations();
    }
  });
  const downloadErrors = useMutation({ mutationFn: api.downloadImportErrors });
  const exportCsv = useMutation({ mutationFn: api.exportTimesheetsCsv });
  const exportJson = useMutation({ mutationFn: api.exportTimesheetsJson });
  const redeliver = useMutation({
    mutationFn: api.redeliverWebhookEvent,
    onSuccess: (detail) => {
      setSelectedWebhookId(detail.event.id);
      void queryClient.invalidateQueries({ queryKey: ["webhooks"] });
      void queryClient.invalidateQueries({ queryKey: ["webhook-detail"] });
    }
  });

  const mutationError = preview.error ?? commit.error ?? downloadErrors.error ?? exportCsv.error ?? exportJson.error ?? redeliver.error;
  const detail = importDetail.data ?? preview.data;

  return (
    <Stack spacing={2.5}>
      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}
      {preview.isSuccess ? <Alert severity="success">Import preview generated with per-row validation results.</Alert> : null}
      {commit.isSuccess ? <Alert severity="success">Valid employee rows committed; invalid rows remain in the report.</Alert> : null}
      {redeliver.isSuccess ? <Alert severity="success">Webhook redelivery attempt recorded.</Alert> : null}

      <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="stretch">
        <Paper className="integration-import-panel" elevation={0}>
          <Stack spacing={1.5}>
            <SectionTitle icon={<Upload size={19} />} title="Employee CSV import" />
            {!canImport ? <Alert severity="info">Employee imports are available to HR and system admins.</Alert> : null}
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
              <TextField label="File name" size="small" value={fileName} onChange={(event) => setFileName(event.target.value)} fullWidth />
              <Button component="label" variant="outlined" startIcon={<Upload size={18} />}>
                Load CSV
                <input hidden type="file" accept=".csv,text/csv" onChange={(event) => loadFile(event.target.files?.[0])} />
              </Button>
            </Stack>
            <TextField
              label="CSV content"
              value={csvContent}
              onChange={(event) => setCsvContent(event.target.value)}
              multiline
              minRows={6}
              fullWidth
            />
            <Box className="integration-mapping-grid">
              {canonicalFields.map((field) => (
                <TextField
                  key={field}
                  label={labelize(field)}
                  select
                  size="small"
                  value={fieldMapping[field] ?? ""}
                  onChange={(event) => setFieldMapping({ ...fieldMapping, [field]: event.target.value })}
                >
                  <MenuItem value="">Unmapped</MenuItem>
                  {headers.map((header) => (
                    <MenuItem key={header} value={header}>
                      {header}
                    </MenuItem>
                  ))}
                </TextField>
              ))}
            </Box>
            <Button variant="contained" startIcon={<Send size={18} />} onClick={() => preview.mutate({ fileName, csvContent, fieldMapping })} disabled={!canImport || preview.isPending}>
              Preview import
            </Button>
          </Stack>
        </Paper>

        <Paper className="integration-history-panel" elevation={0}>
          <SectionTitle icon={<History size={19} />} title="Import history" />
          <Stack spacing={1} sx={{ mt: 1.5 }}>
            {(imports.data ?? []).map((job) => (
              <Button key={job.id} className="integration-list-button" variant={selectedImportId === job.id ? "contained" : "outlined"} onClick={() => setSelectedImportId(job.id)}>
                <span>{job.fileName}</span>
                <small>{job.status} · {job.errorRows} error(s)</small>
              </Button>
            ))}
            {imports.data?.length === 0 ? <Typography color="text.secondary">No employee imports yet.</Typography> : null}
          </Stack>
        </Paper>
      </Stack>

      <Paper className="integration-detail-panel" elevation={0}>
        {detail ? (
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1}>
              <Box>
                <SectionTitle icon={<Upload size={19} />} title="Import detail" />
                <Typography variant="body2" color="text.secondary">{detail.job.summary}</Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={detail.job.status} />
                <Chip label={`${detail.job.successRows} valid`} color="success" />
                <Chip label={`${detail.job.errorRows} error rows`} color={detail.job.errorRows ? "warning" : "default"} />
                <Chip label={`${detail.job.committedRows} committed`} />
              </Stack>
            </Stack>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
              <Button
                variant="contained"
                onClick={() => commit.mutate(detail.job.id)}
                disabled={!canImport || detail.job.status !== "PREVIEW_READY" || detail.job.successRows === 0 || commit.isPending}
              >
                Commit valid rows
              </Button>
              <Button
                variant="outlined"
                startIcon={<FileDown size={18} />}
                onClick={() => downloadErrors.mutate(detail.job.id)}
                disabled={detail.job.errorRows === 0 || downloadErrors.isPending}
              >
                Download error report
              </Button>
            </Stack>
            <Table size="small" className="data-table">
              <TableHead>
                <TableRow>
                  <TableCell>Row</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Employee</TableCell>
                  <TableCell>Department</TableCell>
                  <TableCell>Rate</TableCell>
                  <TableCell>Errors</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {detail.rows.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{row.rowNumber}</TableCell>
                    <TableCell>{row.status}</TableCell>
                    <TableCell>{row.mappedValues.firstName} {row.mappedValues.lastName}<br /><small>{row.mappedValues.employeeNumber}</small></TableCell>
                    <TableCell>{row.mappedValues.department}</TableCell>
                    <TableCell>{row.mappedValues.hourlyRate}</TableCell>
                    <TableCell>{row.errors.length ? row.errors.map((error) => `${error.field}: ${error.message}`).join("; ") : "Clear"}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        ) : (
          <Alert severity="info">Select or preview an employee import to inspect mapped rows and validation errors.</Alert>
        )}
      </Paper>

      <Paper className="integration-export-panel" elevation={0}>
        <Stack spacing={1.5}>
          <SectionTitle icon={<FileDown size={19} />} title="Approved timesheet exports" />
          <Alert severity="info">Exports are tenant-scoped and include approved timesheets only.</Alert>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <Button variant="outlined" startIcon={<FileDown size={18} />} onClick={() => exportCsv.mutate()} disabled={!canExport || exportCsv.isPending}>
              Export CSV
            </Button>
            <Button variant="outlined" startIcon={<FileDown size={18} />} onClick={() => exportJson.mutate()} disabled={!canExport || exportJson.isPending}>
              Export JSON
            </Button>
          </Stack>
          {!canExport ? <Typography color="text.secondary">Timesheet exports are available to payroll and system admins.</Typography> : null}
        </Stack>
      </Paper>

      <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="flex-start">
        <Paper className="integration-webhook-list" elevation={0}>
          <SectionTitle icon={<PlugZap size={19} />} title="Webhook history" />
          <Stack spacing={1} sx={{ mt: 1.5 }}>
            {(webhooks.data ?? []).map((event) => (
              <Button key={event.id} className="integration-list-button" variant={selectedWebhookId === event.id ? "contained" : "outlined"} onClick={() => setSelectedWebhookId(event.id)}>
                <span>{event.eventType}</span>
                <small>{event.status} · {event.latestAttempt?.status ?? "no attempts"}</small>
              </Button>
            ))}
            {webhooks.data?.length === 0 ? <Typography color="text.secondary">No webhook events yet.</Typography> : null}
          </Stack>
        </Paper>
        <Paper className="integration-webhook-detail" elevation={0}>
          {webhookDetail.data ? (
            <Stack spacing={1.5}>
              <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1}>
                <Box>
                  <Typography variant="h6">{webhookDetail.data.event.eventType}</Typography>
                  <Typography variant="body2" color="text.secondary">{webhookDetail.data.event.entityType} · {webhookDetail.data.event.status}</Typography>
                </Box>
                <Button variant="outlined" startIcon={<RefreshCcw size={18} />} onClick={() => redeliver.mutate(webhookDetail.data?.event.id ?? "")} disabled={redeliver.isPending}>
                  Redeliver
                </Button>
              </Stack>
              <Box className="json-viewer">{prettyJson(webhookDetail.data.payloadJson)}</Box>
              <Divider />
              <Table size="small" className="data-table">
                <TableHead>
                  <TableRow>
                    <TableCell>Destination</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Response</TableCell>
                    <TableCell>Attempted</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {webhookDetail.data.attempts.map((attempt) => (
                    <TableRow key={attempt.id}>
                      <TableCell>{attempt.destinationName}</TableCell>
                      <TableCell>{attempt.status}</TableCell>
                      <TableCell>{attempt.responseCode ?? "-"} {attempt.responseBody}</TableCell>
                      <TableCell>{formatDateTime(attempt.attemptedAt)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Stack>
          ) : (
            <Alert severity="info">Select a webhook event to inspect its payload and delivery attempts.</Alert>
          )}
        </Paper>
      </Stack>
    </Stack>
  );

  function loadFile(file?: File) {
    if (!file) {
      return;
    }
    setFileName(file.name);
    const reader = new FileReader();
    reader.onload = () => setCsvContent(String(reader.result ?? ""));
    reader.readAsText(file);
  }
}

function SectionTitle({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      {icon}
      <Typography variant="h6">{title}</Typography>
    </Stack>
  );
}

function parseHeaders(csvContent: string) {
  const firstLine = csvContent.split(/\r?\n/)[0] ?? "";
  const headers: string[] = [];
  let value = "";
  let quoted = false;
  for (let index = 0; index < firstLine.length; index += 1) {
    const ch = firstLine[index];
    if (ch === '"') {
      quoted = !quoted;
    } else if (ch === "," && !quoted) {
      headers.push(value.trim());
      value = "";
    } else {
      value += ch;
    }
  }
  headers.push(value.trim());
  return headers.filter(Boolean);
}

function labelize(value: string) {
  return value.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase());
}

function prettyJson(value: string) {
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(value));
}
