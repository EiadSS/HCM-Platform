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
import { Calculator, FileText, WalletCards } from "lucide-react";
import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { api } from "../../api/client";
import type { PayrollPreview, PayrollPreviewRequest } from "../../types/api";
import { useAuth } from "../auth/AuthContext";

export function PayrollPanel() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const canGenerate = Boolean(user?.roles.some((role) => role === "PAYROLL_ADMIN" || role === "SYSTEM_ADMIN"));
  const defaultPeriod = useMemo(() => previousWeekPeriod(), []);
  const [periodStart, setPeriodStart] = useState(defaultPeriod.periodStart);
  const [periodEnd, setPeriodEnd] = useState(defaultPeriod.periodEnd);
  const [locationId, setLocationId] = useState("");
  const [selectedId, setSelectedId] = useState<string | undefined>();

  const previews = useQuery({ queryKey: ["payroll"], queryFn: api.payrollPreviews });
  const organization = useQuery({ queryKey: ["organization"], queryFn: api.organization });
  const activePreviewId = selectedId ?? previews.data?.[0]?.id;
  const detail = useQuery({
    queryKey: ["payroll-detail", activePreviewId],
    queryFn: () => api.payrollPreviewDetail(activePreviewId ?? ""),
    enabled: Boolean(activePreviewId)
  });

  const generateMutation = useMutation({
    mutationFn: (request: PayrollPreviewRequest) => api.generatePayrollPreview(request),
    onSuccess: (result) => {
      setSelectedId(result.preview.id);
      void queryClient.invalidateQueries({ queryKey: ["payroll"] });
      void queryClient.invalidateQueries({ queryKey: ["payroll-detail"] });
      void queryClient.invalidateQueries({ queryKey: ["audit"] });
    }
  });

  const currentPreview = detail.data?.preview ?? previews.data?.find((preview) => preview.id === activePreviewId);
  const lines = detail.data?.lines ?? [];

  return (
    <Stack spacing={2}>
      <Stack direction={{ xs: "column", md: "row" }} spacing={2} alignItems="stretch">
        <Paper className="payroll-generate-panel" elevation={0}>
          <Stack spacing={1.5}>
            <SectionTitle icon={<Calculator size={19} />} title="Generate preview" />
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
              <TextField
                label="Period start"
                type="date"
                size="small"
                value={periodStart}
                onChange={(event) => setPeriodStart(event.target.value)}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                label="Period end"
                type="date"
                size="small"
                value={periodEnd}
                onChange={(event) => setPeriodEnd(event.target.value)}
                InputLabelProps={{ shrink: true }}
              />
              <TextField
                label="Location"
                select
                size="small"
                value={locationId}
                onChange={(event) => setLocationId(event.target.value)}
                sx={{ minWidth: 210 }}
              >
                <MenuItem value="">All locations</MenuItem>
                {organization.data?.locations.map((location) => (
                  <MenuItem key={location.id} value={location.id}>
                    {location.name}
                  </MenuItem>
                ))}
              </TextField>
            </Stack>
            <Button
              variant="contained"
              startIcon={<WalletCards size={18} />}
              onClick={() => generateMutation.mutate({ periodStart, periodEnd, locationId: locationId || undefined })}
              disabled={!canGenerate || generateMutation.isPending}
            >
              Generate preview
            </Button>
            {!canGenerate ? <Alert severity="info">Payroll preview generation is available to payroll and system admins.</Alert> : null}
            {generateMutation.error ? <Alert severity="error">{generateMutation.error.message}</Alert> : null}
            {generateMutation.isSuccess ? <Alert severity="success">Payroll preview generated and audit log updated.</Alert> : null}
          </Stack>
        </Paper>

        <Paper className="payroll-list-panel" elevation={0}>
          <SectionTitle icon={<FileText size={19} />} title="Preview history" />
          <Stack spacing={1} sx={{ mt: 1.5 }}>
            {(previews.data ?? []).map((preview) => (
              <Button
                key={preview.id}
                className="payroll-list-button"
                variant={preview.id === activePreviewId ? "contained" : "outlined"}
                onClick={() => setSelectedId(preview.id)}
              >
                <span>
                  {formatDate(preview.periodStart)} - {formatDate(preview.periodEnd)}
                </span>
                <small>{formatCurrency(preview.grossPay)}</small>
              </Button>
            ))}
            {previews.data?.length === 0 ? <Typography color="text.secondary">No payroll previews yet.</Typography> : null}
          </Stack>
        </Paper>
      </Stack>

      {currentPreview ? (
        <Paper className="payroll-report-panel" elevation={0}>
          <Stack spacing={2}>
            <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1}>
              <Box>
                <SectionTitle icon={<WalletCards size={19} />} title="Payroll preview report" />
                <Typography variant="body2" color="text.secondary">
                  {formatDate(currentPreview.periodStart)} - {formatDate(currentPreview.periodEnd)}
                </Typography>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                <Chip label={currentPreview.status} />
                <Chip label={`${currentPreview.employeeCount} employees`} />
                <Chip label={`${currentPreview.timesheetCount} timesheets`} />
              </Stack>
            </Stack>

            <Box className="payroll-summary-grid">
              <SummaryMetric label="Gross pay" value={formatCurrency(currentPreview.grossPay)} />
              <SummaryMetric label="Regular" value={`${formatNumber(currentPreview.regularHours)} h`} />
              <SummaryMetric label="Overtime" value={`${formatNumber(currentPreview.overtimeHours)} h`} />
              <SummaryMetric label="Holiday premium" value={formatCurrency(currentPreview.holidayPremiumPay)} />
              <SummaryMetric label="Unpaid breaks" value={`${formatNumber(currentPreview.unpaidBreakHours)} h`} />
              <SummaryMetric label="Unpaid leave" value={`${formatNumber(currentPreview.unpaidLeaveHours)} h`} />
            </Box>

            <Alert severity="info">{currentPreview.explanation}</Alert>
            <Divider />
            <Table size="small" className="data-table">
              <TableHead>
                <TableRow>
                  <TableCell>Employee</TableCell>
                  <TableCell>Rate</TableCell>
                  <TableCell>Regular</TableCell>
                  <TableCell>Overtime</TableCell>
                  <TableCell>Holiday</TableCell>
                  <TableCell>Breaks</TableCell>
                  <TableCell>Leave</TableCell>
                  <TableCell>Gross</TableCell>
                  <TableCell>Explanation</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {lines.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={9}>No employee calculation lines for this preview.</TableCell>
                  </TableRow>
                ) : (
                  lines.map((line) => (
                    <TableRow key={line.id}>
                      <TableCell>
                        <Typography variant="subtitle2">{line.employeeName}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {line.locationName ?? "Unassigned location"}
                        </Typography>
                      </TableCell>
                      <TableCell>{formatCurrency(line.hourlyRate)}</TableCell>
                      <TableCell>{formatNumber(line.regularHours)} h</TableCell>
                      <TableCell>{formatNumber(line.overtimeHours)} h</TableCell>
                      <TableCell>{formatNumber(line.holidayHours)} h</TableCell>
                      <TableCell>{formatNumber(line.unpaidBreakHours)} h</TableCell>
                      <TableCell>{formatNumber(line.unpaidLeaveHours)} h</TableCell>
                      <TableCell>{formatCurrency(line.grossPay)}</TableCell>
                      <TableCell>{line.explanation}</TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </Stack>
        </Paper>
      ) : (
        <Alert severity="info">Generate a payroll preview to see employee-level gross-pay explanations.</Alert>
      )}
    </Stack>
  );
}

function SectionTitle({ icon, title }: { icon: ReactNode; title: string }) {
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      {icon}
      <Typography variant="h6">{title}</Typography>
    </Stack>
  );
}

function SummaryMetric({ label, value }: { label: string; value: string }) {
  return (
    <Paper className="payroll-summary-card" elevation={0}>
      <Typography variant="overline">{label}</Typography>
      <Typography variant="h6">{value}</Typography>
    </Paper>
  );
}

function previousWeekPeriod() {
  const today = new Date();
  const day = today.getDay() === 0 ? 7 : today.getDay();
  const monday = new Date(today);
  monday.setDate(today.getDate() - day + 1 - 7);
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  return { periodStart: toDateInput(monday), periodEnd: toDateInput(sunday) };
}

function toDateInput(value: Date) {
  return value.toISOString().slice(0, 10);
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(new Date(`${value}T00:00:00`));
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat(undefined, { style: "currency", currency: "USD" }).format(value);
}

function formatNumber(value: number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(value);
}
