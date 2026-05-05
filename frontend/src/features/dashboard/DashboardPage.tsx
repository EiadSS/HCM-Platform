import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Tabs,
  Typography
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  AlertTriangle,
  CheckCircle2,
  RefreshCcw,
  WalletCards
} from "lucide-react";
import { useMemo, useState } from "react";
import { api } from "../../api/client";
import { useAuth } from "../auth/AuthContext";
import { AuditPanel } from "./AuditPanel";
import { IntegrationPanel } from "./IntegrationPanel";
import { LeavePanel } from "./LeavePanel";
import { PayrollPanel } from "./PayrollPanel";
import { SchedulingPanel } from "./SchedulingPanel";
import { TimePanel } from "./TimePanel";

const tabLabels = ["Command Center", "Employees", "Scheduling", "Timesheets", "Leave", "Payroll", "Integrations", "Audit"];

export function DashboardPage() {
  const { user } = useAuth();
  const [tab, setTab] = useState(0);
  const queryClient = useQueryClient();

  const dashboard = useQuery({ queryKey: ["dashboard"], queryFn: api.dashboard });
  const employees = useQuery({ queryKey: ["employees"], queryFn: api.employees });
  useQuery({
    queryKey: ["schedule-alerts"],
    queryFn: api.scheduleAlerts,
    enabled: Boolean(user?.roles.some((role) => role === "MANAGER" || role === "HR_ADMIN" || role === "SYSTEM_ADMIN"))
  });
  const timesheets = useQuery({ queryKey: ["timesheets"], queryFn: api.timesheets });
  const approveMutation = useMutation({
    mutationFn: api.approveTimesheet,
    onSuccess: () => {
      void queryClient.invalidateQueries();
    }
  });

  const payrollMutation = useMutation({
    mutationFn: api.generatePayrollPreview,
    onSuccess: () => {
      void queryClient.invalidateQueries();
    }
  });

  const resetMutation = useMutation({
    mutationFn: api.resetDemo,
    onSuccess: () => {
      void queryClient.invalidateQueries();
    }
  });

  const submittedTimesheet = useMemo(
    () => timesheets.data?.find((item) => item.status === "SUBMITTED" || item.status === "CHANGE_REQUESTED"),
    [timesheets.data]
  );

  if (dashboard.isLoading) {
    return (
      <Stack alignItems="center" justifyContent="center" sx={{ minHeight: 420 }}>
        <CircularProgress />
      </Stack>
    );
  }

  if (dashboard.error) {
    return <Alert severity="error">{dashboard.error.message}</Alert>;
  }

  const canReset = user?.roles.includes("SYSTEM_ADMIN");

  return (
    <Stack spacing={3}>
      <Box className="page-header">
        <Box>
          <Typography variant="overline">Demo tenant</Typography>
          <Typography variant="h4">{dashboard.data?.tenantName}</Typography>
          <Typography variant="body2" color="text.secondary">
            Seeded workforce workflows for a public recruiter review.
          </Typography>
        </Box>
        <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
          {submittedTimesheet ? (
            <Button
              variant="contained"
              startIcon={<CheckCircle2 size={18} />}
              onClick={() => approveMutation.mutate(submittedTimesheet.id)}
              disabled={approveMutation.isPending}
            >
              Approve timesheet
            </Button>
          ) : null}
          {user?.roles.includes("PAYROLL_ADMIN") || user?.roles.includes("SYSTEM_ADMIN") ? (
            <Button
              variant="outlined"
              startIcon={<WalletCards size={18} />}
              onClick={() => payrollMutation.mutate({})}
              disabled={payrollMutation.isPending}
            >
              Generate payroll
            </Button>
          ) : null}
          {canReset ? (
            <Button
              color="warning"
              variant="outlined"
              startIcon={<RefreshCcw size={18} />}
              onClick={() => {
                if (window.confirm("Reset Northstar demo data back to the seeded recruiter walkthrough state?")) {
                  resetMutation.mutate();
                }
              }}
              disabled={resetMutation.isPending}
            >
              Reset Demo Data
            </Button>
          ) : null}
        </Stack>
      </Box>

      {approveMutation.isSuccess ? <Alert severity="success">Timesheet approved and audit log updated.</Alert> : null}
      {payrollMutation.isSuccess ? <Alert severity="success">Payroll preview generated with explanation output.</Alert> : null}
      {resetMutation.isSuccess ? <Alert severity="success">Demo data reset to the seeded recruiter state.</Alert> : null}

      <Box className="metric-grid">
        {dashboard.data?.metrics.map((metric) => (
          <Paper key={metric.label} className={`metric-card tone-${metric.tone}`} elevation={0}>
            <Typography variant="overline">{metric.label}</Typography>
            <Typography variant="h4">{metric.value}</Typography>
            <Typography variant="body2">{metric.detail}</Typography>
          </Paper>
        ))}
      </Box>

      <Paper className="workspace-panel" elevation={0}>
        <Tabs value={tab} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">
          {tabLabels.map((label) => (
            <Tab key={label} label={label} />
          ))}
        </Tabs>
        <Divider />
        <Box className="tab-panel">
          {tab === 0 ? (
            <Stack spacing={2}>
              <Typography variant="h6">Priority work</Typography>
              <Box className="work-grid">
                {dashboard.data?.priorityWork.map((item, index) => (
                  <Paper key={`${item.type}-${index}`} className="work-card" elevation={0}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <AlertTriangle size={18} />
                      <Typography variant="subtitle2">{item.type}</Typography>
                      <Chip size="small" label={item.severity} />
                    </Stack>
                    <Typography variant="h6">{item.title}</Typography>
                    <Typography variant="body2" color="text.secondary">
                      {item.detail}
                    </Typography>
                  </Paper>
                ))}
              </Box>
              <Typography variant="h6">Role quick actions</Typography>
              <Stack direction="row" flexWrap="wrap" gap={1}>
                {dashboard.data?.quickActions.map((action) => <Chip key={action} label={action} />)}
              </Stack>
            </Stack>
          ) : null}

          {tab === 1 ? (
            <DataTable
              title="Employees and organization"
              rows={employees.data ?? []}
              columns={["employeeNumber", "fullName", "workEmail", "status", "employmentType", "departmentName", "managerName", "weeklyHourCap"]}
            />
          ) : null}

          {tab === 2 ? (
            <SchedulingPanel />
          ) : null}

          {tab === 3 ? (
            <TimePanel />
          ) : null}

          {tab === 4 ? (
            <LeavePanel />
          ) : null}

          {tab === 5 ? (
            <PayrollPanel />
          ) : null}

          {tab === 6 ? (
            <IntegrationPanel />
          ) : null}

          {tab === 7 ? (
            <AuditPanel />
          ) : null}
        </Box>
      </Paper>
    </Stack>
  );
}

function DataTable<T extends object>({
  title,
  rows,
  columns
}: {
  title?: string;
  rows: T[];
  columns: string[];
}) {
  return (
    <Stack spacing={1.5}>
      {title ? <Typography variant="h6">{title}</Typography> : null}
      <Table size="small" className="data-table">
        <TableHead>
          <TableRow>
            {columns.map((column) => (
              <TableCell key={column}>{labelize(column)}</TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={columns.length}>No records available for this role.</TableCell>
            </TableRow>
          ) : (
            rows.map((row, rowIndex) => {
              const record = row as Record<string, unknown>;
              return (
              <TableRow key={String(record.id ?? rowIndex)}>
                {columns.map((column) => (
                  <TableCell key={column}>{formatValue(record[column])}</TableCell>
                ))}
              </TableRow>
              );
            })
          )}
        </TableBody>
      </Table>
    </Stack>
  );
}

function labelize(value: string) {
  return value.replace(/([A-Z])/g, " $1").replace(/^./, (letter) => letter.toUpperCase());
}

function formatValue(value: unknown) {
  if (typeof value === "boolean") {
    return value ? "Yes" : "No";
  }
  if (typeof value === "number") {
    return Number.isInteger(value) ? value : value.toFixed(2);
  }
  if (value === null || value === undefined || value === "") {
    return "—";
  }
  return String(value);
}
