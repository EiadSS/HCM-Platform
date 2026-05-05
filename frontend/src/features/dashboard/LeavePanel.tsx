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
import { CalendarDays, CheckCircle2, ClipboardList, RefreshCcw, Send, XCircle } from "lucide-react";
import { useMemo, useState } from "react";
import type { ReactNode } from "react";
import { api } from "../../api/client";
import type { LeaveDecisionRequest, LeaveRequestCreate } from "../../types/api";
import { useAuth } from "../auth/AuthContext";

type LeaveType = "VACATION" | "SICK" | "UNPAID";

interface LeaveFormState {
  leaveType: LeaveType;
  startDate: string;
  endDate: string;
  hours: string;
  note: string;
}

export function LeavePanel() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const canManage = Boolean(user?.roles.some((role) => role === "MANAGER" || role === "HR_ADMIN" || role === "SYSTEM_ADMIN"));
  const canRunAccruals = Boolean(user?.roles.some((role) => role === "HR_ADMIN" || role === "SYSTEM_ADMIN"));
  const isEmployee = Boolean(user?.roles.includes("EMPLOYEE"));
  const month = useMemo(() => currentMonthRange(), []);
  const [form, setForm] = useState<LeaveFormState>(() => ({
    leaveType: "VACATION",
    startDate: todayInput(),
    endDate: todayInput(),
    hours: "8",
    note: ""
  }));
  const [decisionNotes, setDecisionNotes] = useState<Record<string, string>>({});

  const balances = useQuery({ queryKey: ["leave-balances"], queryFn: () => api.leaveBalances() });
  const requests = useQuery({ queryKey: ["leave-requests", month.start, month.end, canManage], queryFn: () => api.leaveRequests(month.start, month.end, !canManage) });
  const calendar = useQuery({ queryKey: ["leave-calendar", month.start, month.end], queryFn: () => api.leaveCalendar(month.start, month.end) });

  const invalidateLeave = () => {
    void queryClient.invalidateQueries({ queryKey: ["leave-balances"] });
    void queryClient.invalidateQueries({ queryKey: ["leave-requests"] });
    void queryClient.invalidateQueries({ queryKey: ["leave-calendar"] });
    void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
    void queryClient.invalidateQueries({ queryKey: ["payroll"] });
  };

  const create = useMutation({
    mutationFn: (request: LeaveRequestCreate) => api.createLeaveRequest(request),
    onSuccess: () => {
      setForm((current) => ({ ...current, note: "", hours: "8" }));
      invalidateLeave();
    }
  });
  const approve = useMutation({
    mutationFn: ({ id, request }: { id: string; request: LeaveDecisionRequest }) => api.approveLeaveRequest(id, request),
    onSuccess: (_, variables) => {
      clearDecisionNote(variables.id);
      invalidateLeave();
    }
  });
  const reject = useMutation({
    mutationFn: ({ id, request }: { id: string; request: LeaveDecisionRequest }) => api.rejectLeaveRequest(id, request),
    onSuccess: (_, variables) => {
      clearDecisionNote(variables.id);
      invalidateLeave();
    }
  });
  const runAccruals = useMutation({ mutationFn: () => api.runLeaveAccruals({ asOfDate: todayInput() }), onSuccess: invalidateLeave });

  const pendingRequests = useMemo(() => (requests.data ?? []).filter((request) => request.status === "PENDING"), [requests.data]);
  const mutationError = create.error ?? approve.error ?? reject.error ?? runAccruals.error;

  return (
    <Stack spacing={2.5}>
      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}
      {create.isSuccess ? <Alert severity="success">Leave request submitted and pending balance reserved when applicable.</Alert> : null}
      {approve.isSuccess ? <Alert severity="success">Leave request approved, balance updated, and audit history written.</Alert> : null}
      {reject.isSuccess ? <Alert severity="success">Leave request rejected and reserved balance released.</Alert> : null}
      {runAccruals.isSuccess ? <Alert severity="success">Monthly accrual run applied {formatHours(runAccruals.data.hoursAccrued)} hours.</Alert> : null}

      <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="stretch">
        <Paper className="leave-balances-panel" elevation={0}>
          <Stack spacing={1.5}>
            <SectionTitle icon={<ClipboardList size={19} />} title={canManage ? "Leave balances" : "My balances"} />
            <Box className="leave-balance-grid">
              {(balances.data ?? []).slice(0, canManage ? 8 : 3).map((balance) => (
                <Paper key={balance.id} className="leave-balance-card" elevation={0}>
                  <Typography variant="overline">{balance.leaveType}</Typography>
                  <Typography variant="h6">{formatHours(balance.availableHours)} h available</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {balance.employeeName}
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1 }}>
                    <Chip size="small" label={`${formatHours(balance.usedHours)} used`} />
                    <Chip size="small" label={`${formatHours(balance.pendingHours)} pending`} />
                    <Chip size="small" label={`${formatHours(balance.maxHours)} max`} />
                  </Stack>
                </Paper>
              ))}
              {balances.data?.length === 0 ? <Typography color="text.secondary">No balances available for this role.</Typography> : null}
            </Box>
            {canRunAccruals ? (
              <Button variant="outlined" startIcon={<RefreshCcw size={18} />} onClick={() => runAccruals.mutate()} disabled={runAccruals.isPending}>
                Run monthly accrual
              </Button>
            ) : null}
          </Stack>
        </Paper>

        {isEmployee ? (
          <Paper className="leave-request-panel" elevation={0}>
            <Stack spacing={1.5}>
              <SectionTitle icon={<Send size={19} />} title="Request leave" />
              <Stack direction={{ xs: "column", sm: "row" }} spacing={1.5}>
                <TextField label="Type" select size="small" value={form.leaveType} onChange={(event) => setForm({ ...form, leaveType: event.target.value as LeaveType })} sx={{ minWidth: 150 }}>
                  <MenuItem value="VACATION">Vacation</MenuItem>
                  <MenuItem value="SICK">Sick</MenuItem>
                  <MenuItem value="UNPAID">Unpaid</MenuItem>
                </TextField>
                <TextField label="Start" type="date" size="small" value={form.startDate} onChange={(event) => setForm({ ...form, startDate: event.target.value })} InputLabelProps={{ shrink: true }} />
                <TextField label="End" type="date" size="small" value={form.endDate} onChange={(event) => setForm({ ...form, endDate: event.target.value })} InputLabelProps={{ shrink: true }} />
                <TextField label="Hours" type="number" size="small" value={form.hours} onChange={(event) => setForm({ ...form, hours: event.target.value })} inputProps={{ min: 0.25, step: 0.25 }} sx={{ width: 120 }} />
              </Stack>
              <TextField label="Note" size="small" value={form.note} onChange={(event) => setForm({ ...form, note: event.target.value })} multiline minRows={2} />
              <Button variant="contained" startIcon={<Send size={18} />} onClick={submitRequest} disabled={create.isPending || !canSubmit(form)}>
                Submit request
              </Button>
            </Stack>
          </Paper>
        ) : null}
      </Stack>

      {canManage ? (
        <Paper className="leave-queue-panel" elevation={0}>
          <Stack spacing={1.5}>
            <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1}>
              <SectionTitle icon={<CheckCircle2 size={19} />} title="Pending approvals" />
              <Chip label={`${pendingRequests.length} pending`} color={pendingRequests.length ? "warning" : "default"} />
            </Stack>
            {pendingRequests.length === 0 ? <Alert severity="info">No leave requests are waiting for approval.</Alert> : null}
            <Stack spacing={1.5}>
              {pendingRequests.map((request) => (
                <Paper key={request.id} className="leave-approval-card" elevation={0}>
                  <Stack spacing={1.2}>
                    <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1}>
                      <Box>
                        <Typography variant="subtitle1">{request.employeeName}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {request.leaveType} · {formatDate(request.startDate)} - {formatDate(request.endDate)} · {formatHours(request.hours)} h
                        </Typography>
                      </Box>
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                        <Chip size="small" label={request.status} />
                        {request.conflict ? <Chip size="small" color="warning" label={`${request.conflictCount} schedule conflict(s)`} /> : <Chip size="small" color="success" label="No conflicts" />}
                      </Stack>
                    </Stack>
                    {request.conflictSummary ? <Alert severity="warning">{request.conflictSummary}</Alert> : null}
                    {request.employeeNote ? <Typography variant="body2">{request.employeeNote}</Typography> : null}
                    <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                      <TextField
                        label="Decision note"
                        size="small"
                        value={decisionNotes[request.id] ?? ""}
                        onChange={(event) => setDecisionNotes({ ...decisionNotes, [request.id]: event.target.value })}
                        fullWidth
                      />
                      <Button variant="contained" startIcon={<CheckCircle2 size={18} />} onClick={() => decide(request.id, "APPROVE")} disabled={approve.isPending}>
                        Approve
                      </Button>
                      <Button variant="outlined" color="warning" startIcon={<XCircle size={18} />} onClick={() => decide(request.id, "REJECT")} disabled={reject.isPending}>
                        Reject
                      </Button>
                    </Stack>
                  </Stack>
                </Paper>
              ))}
            </Stack>
          </Stack>
        </Paper>
      ) : null}

      <Paper className="leave-calendar-panel" elevation={0}>
        <Stack spacing={2}>
          <Stack direction={{ xs: "column", sm: "row" }} justifyContent="space-between" spacing={1}>
            <SectionTitle icon={<CalendarDays size={19} />} title="Leave calendar" />
            <Typography variant="body2" color="text.secondary">
              {formatDate(month.start)} - {formatDate(month.end)}
            </Typography>
          </Stack>
          <Box className="leave-calendar-strip">
            {(calendar.data ?? []).map((entry) => (
              <Paper key={entry.id} className={`leave-calendar-item ${entry.status.toLowerCase()}`} elevation={0}>
                <Stack spacing={0.75}>
                  <Stack direction="row" spacing={1} alignItems="center" justifyContent="space-between">
                    <Typography variant="subtitle2">{entry.employeeName}</Typography>
                    <Chip size="small" label={entry.leaveType} />
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    {formatDate(entry.startDate)} - {formatDate(entry.endDate)} · {formatHours(entry.hours)} h
                  </Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip size="small" label={entry.status} color={entry.status === "APPROVED" ? "success" : entry.status === "PENDING" ? "warning" : "default"} />
                    {entry.conflict ? <Chip size="small" color="warning" label="Conflict" /> : null}
                  </Stack>
                </Stack>
              </Paper>
            ))}
            {calendar.data?.length === 0 ? <Typography color="text.secondary">No leave entries for this month.</Typography> : null}
          </Box>
          <Divider />
          <Table size="small" className="data-table">
            <TableHead>
              <TableRow>
                <TableCell>Employee</TableCell>
                <TableCell>Type</TableCell>
                <TableCell>Dates</TableCell>
                <TableCell>Hours</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Conflict</TableCell>
                <TableCell>Decision</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {(requests.data ?? []).map((request) => (
                <TableRow key={request.id}>
                  <TableCell>{request.employeeName}</TableCell>
                  <TableCell>{request.leaveType}</TableCell>
                  <TableCell>
                    {request.startDate} - {request.endDate}
                  </TableCell>
                  <TableCell>{formatHours(request.hours)}</TableCell>
                  <TableCell>{request.status}</TableCell>
                  <TableCell>{request.conflictSummary ?? (request.conflict ? "Conflict" : "Clear")}</TableCell>
                  <TableCell>{request.decisionNote ?? request.managerNote ?? "-"}</TableCell>
                </TableRow>
              ))}
              {requests.data?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7}>No leave requests in this range.</TableCell>
                </TableRow>
              ) : null}
            </TableBody>
          </Table>
        </Stack>
      </Paper>
    </Stack>
  );

  function submitRequest() {
    if (!canSubmit(form)) {
      return;
    }
    create.mutate({
      leaveType: form.leaveType,
      startDate: form.startDate,
      endDate: form.endDate,
      hours: Number(form.hours),
      note: form.note.trim() || undefined
    });
  }

  function decide(id: string, decision: "APPROVE" | "REJECT") {
    const request = { note: decisionNotes[id]?.trim() || undefined };
    if (decision === "APPROVE") {
      approve.mutate({ id, request });
      return;
    }
    reject.mutate({ id, request });
  }

  function clearDecisionNote(id: string) {
    setDecisionNotes((current) => {
      const next = { ...current };
      delete next[id];
      return next;
    });
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

function canSubmit(form: LeaveFormState) {
  return Boolean(form.startDate && form.endDate && Number(form.hours) > 0);
}

function currentMonthRange() {
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  return { start: formatInputDate(start), end: formatInputDate(end) };
}

function todayInput() {
  return formatInputDate(new Date());
}

function formatInputDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(new Date(`${value}T00:00:00`));
}

function formatHours(value: number) {
  return Number(value).toFixed(2);
}
