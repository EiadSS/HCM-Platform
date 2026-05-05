import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
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
import { AlarmClock, CheckCircle2, Coffee, Edit3, Lock, LogIn, LogOut, Plus, Send, Unlock, XCircle } from "lucide-react";
import { useEffect, useMemo, useState } from "react";
import { api } from "../../api/client";
import type { ManualTimeEntryRequest, TimeEntry, Timesheet } from "../../types/api";
import { useAuth } from "../auth/AuthContext";

interface EntryFormState {
  clockInAt: string;
  clockOutAt: string;
  breakStartAt: string;
  breakEndAt: string;
  note: string;
}

export function TimePanel() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [selectedTimesheetId, setSelectedTimesheetId] = useState<string | null>(null);
  const [editingEntry, setEditingEntry] = useState<TimeEntry | null>(null);
  const [entryForm, setEntryForm] = useState<EntryFormState | null>(null);
  const [changeReason, setChangeReason] = useState("");

  const canManage = Boolean(user?.roles.some((role) => role === "MANAGER" || role === "HR_ADMIN" || role === "SYSTEM_ADMIN"));
  const canPayroll = Boolean(user?.roles.some((role) => role === "PAYROLL_ADMIN" || role === "SYSTEM_ADMIN"));
  const isEmployee = Boolean(user?.roles.includes("EMPLOYEE"));

  const timesheets = useQuery({ queryKey: ["timesheets"], queryFn: api.timesheets });
  const timeStatus = useQuery({ queryKey: ["time-status"], queryFn: api.timeStatus, enabled: isEmployee });
  const detail = useQuery({
    queryKey: ["timesheet-detail", selectedTimesheetId],
    queryFn: () => api.timesheetDetail(selectedTimesheetId as string),
    enabled: Boolean(selectedTimesheetId)
  });

  useEffect(() => {
    if (!selectedTimesheetId && timesheets.data?.length) {
      const preferred = canManage
        ? timesheets.data.find((item) => item.status === "SUBMITTED" || item.status === "CHANGE_REQUESTED") ?? timesheets.data[0]
        : timesheets.data[0];
      setSelectedTimesheetId(preferred.id);
    }
  }, [canManage, selectedTimesheetId, timesheets.data]);

  const invalidateTime = () => {
    void queryClient.invalidateQueries({ queryKey: ["timesheets"] });
    void queryClient.invalidateQueries({ queryKey: ["time-status"] });
    void queryClient.invalidateQueries({ queryKey: ["timesheet-detail"] });
    void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };

  const clockIn = useMutation({ mutationFn: () => api.clockIn(), onSuccess: invalidateTime });
  const clockOut = useMutation({ mutationFn: () => api.clockOut(), onSuccess: invalidateTime });
  const startBreak = useMutation({ mutationFn: (entryId: string) => api.startBreak(entryId), onSuccess: invalidateTime });
  const endBreak = useMutation({ mutationFn: (breakId: string) => api.endBreak(breakId), onSuccess: invalidateTime });
  const submit = useMutation({ mutationFn: api.submitTimesheet, onSuccess: invalidateTime });
  const approve = useMutation({ mutationFn: api.approveTimesheet, onSuccess: invalidateTime });
  const reject = useMutation({ mutationFn: (id: string) => api.rejectTimesheet(id), onSuccess: invalidateTime });
  const lock = useMutation({ mutationFn: api.lockTimesheet, onSuccess: invalidateTime });
  const unlock = useMutation({ mutationFn: api.unlockTimesheet, onSuccess: invalidateTime });
  const requestChange = useMutation({
    mutationFn: ({ id, reason }: { id: string; reason: string }) => api.requestTimesheetChange(id, { reason }),
    onSuccess: () => {
      setChangeReason("");
      invalidateTime();
    }
  });
  const approveChange = useMutation({
    mutationFn: ({ timesheetId, requestId }: { timesheetId: string; requestId: string }) => api.approveTimesheetChangeRequest(timesheetId, requestId),
    onSuccess: invalidateTime
  });
  const rejectChange = useMutation({
    mutationFn: ({ timesheetId, requestId }: { timesheetId: string; requestId: string }) => api.rejectTimesheetChangeRequest(timesheetId, requestId),
    onSuccess: invalidateTime
  });
  const saveEntry = useMutation({
    mutationFn: ({ timesheetId, entryId, request }: { timesheetId: string; entryId?: string; request: ManualTimeEntryRequest }) =>
      entryId ? api.updateTimeEntry(timesheetId, entryId, request) : api.addTimeEntry(timesheetId, request),
    onSuccess: () => {
      setEntryForm(null);
      setEditingEntry(null);
      invalidateTime();
    }
  });

  const pendingTimesheets = useMemo(
    () => (timesheets.data ?? []).filter((item) => item.status === "SUBMITTED" || item.status === "CHANGE_REQUESTED"),
    [timesheets.data]
  );
  const mutationError =
    clockIn.error ?? clockOut.error ?? startBreak.error ?? endBreak.error ?? submit.error ?? approve.error ?? reject.error ?? lock.error ?? unlock.error ?? requestChange.error ?? approveChange.error ?? rejectChange.error ?? saveEntry.error;

  const selected = detail.data?.timesheet;

  return (
    <Stack spacing={2.5}>
      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}

      {isEmployee ? (
        <Paper className="time-clock-panel" elevation={0}>
          <Stack direction={{ xs: "column", md: "row" }} spacing={2} justifyContent="space-between" alignItems={{ xs: "stretch", md: "center" }}>
            <Box>
              <Stack direction="row" spacing={1} alignItems="center">
                <AlarmClock size={20} />
                <Typography variant="h6">My clock</Typography>
              </Stack>
              <Typography variant="body2" color="text.secondary">
                {timeStatus.data?.activeEntry ? `Clocked in since ${formatDateTime(timeStatus.data.activeEntry.clockInAt)}` : "No active punch"}
              </Typography>
            </Box>
            <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
              {timeStatus.data?.activeEntry ? (
                <>
                  {timeStatus.data.activeBreak ? (
                    <Button variant="outlined" startIcon={<Coffee size={18} />} onClick={() => endBreak.mutate(timeStatus.data?.activeBreak?.id ?? "")}>
                      End break
                    </Button>
                  ) : (
                    <Button variant="outlined" startIcon={<Coffee size={18} />} onClick={() => startBreak.mutate(timeStatus.data?.activeEntry?.id ?? "")}>
                      Start break
                    </Button>
                  )}
                  <Button variant="contained" startIcon={<LogOut size={18} />} onClick={() => clockOut.mutate()}>
                    Clock out
                  </Button>
                </>
              ) : (
                <Button variant="contained" startIcon={<LogIn size={18} />} onClick={() => clockIn.mutate()}>
                  Clock in
                </Button>
              )}
            </Stack>
          </Stack>
        </Paper>
      ) : null}

      <Stack direction={{ xs: "column", lg: "row" }} spacing={2} alignItems="flex-start">
        <Paper className="time-list-panel" elevation={0}>
          <Stack spacing={1.5}>
            <Typography variant="h6">{canManage ? "Timesheet queue" : "My timesheets"}</Typography>
            {canManage && pendingTimesheets.length ? (
              <Alert severity="warning">{pendingTimesheets.length} timesheet(s) waiting for manager review.</Alert>
            ) : null}
            {(timesheets.data ?? []).map((timesheet) => (
              <Button
                key={timesheet.id}
                variant={selectedTimesheetId === timesheet.id ? "contained" : "outlined"}
                className="timesheet-list-button"
                onClick={() => setSelectedTimesheetId(timesheet.id)}
              >
                <span>{timesheet.employeeName}</span>
                <small>{timesheet.weekStartDate} - {timesheet.status}</small>
              </Button>
            ))}
          </Stack>
        </Paper>

        <Paper className="time-detail-panel" elevation={0}>
          {selected ? (
            <Stack spacing={2}>
              <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5}>
                <Box>
                  <Typography variant="h6">{selected.employeeName}</Typography>
                  <Stack direction="row" spacing={1} flexWrap="wrap" alignItems="center">
                    <Chip size="small" label={selected.weekStartDate} />
                    <Chip size="small" label={selected.status} color={selected.status === "APPROVED" ? "success" : selected.status === "SUBMITTED" ? "warning" : "default"} />
                    <Chip size="small" label={selected.lockedPayPeriod ? "Locked" : "Unlocked"} color={selected.lockedPayPeriod ? "error" : "default"} />
                    <Chip size="small" label={`${formatHours(selected.regularHours)} regular`} />
                    <Chip size="small" label={`${formatHours(selected.overtimeHours)} overtime`} />
                  </Stack>
                </Box>
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <Button variant="outlined" startIcon={<Plus size={18} />} onClick={() => openEntryDialog()}>
                    Add entry
                  </Button>
                  {(selected.status === "DRAFT" || selected.status === "REJECTED" || selected.status === "CHANGE_REQUESTED") && !selected.lockedPayPeriod ? (
                    <Button variant="contained" startIcon={<Send size={18} />} onClick={() => submit.mutate(selected.id)}>
                      Submit
                    </Button>
                  ) : null}
                  {canManage && (selected.status === "SUBMITTED" || selected.status === "CHANGE_REQUESTED") ? (
                    <>
                      <Button variant="contained" startIcon={<CheckCircle2 size={18} />} onClick={() => approve.mutate(selected.id)}>
                        Approve
                      </Button>
                      <Button variant="outlined" color="warning" startIcon={<XCircle size={18} />} onClick={() => reject.mutate(selected.id)}>
                        Reject
                      </Button>
                    </>
                  ) : null}
                  {canPayroll && selected.status === "APPROVED" ? (
                    selected.lockedPayPeriod ? (
                      <Button variant="outlined" startIcon={<Unlock size={18} />} onClick={() => unlock.mutate(selected.id)}>
                        Unlock
                      </Button>
                    ) : (
                      <Button variant="outlined" startIcon={<Lock size={18} />} onClick={() => lock.mutate(selected.id)}>
                        Lock
                      </Button>
                    )
                  ) : null}
                </Stack>
              </Stack>

              {detail.data?.validationIssues.length ? (
                <Alert severity={detail.data.validationIssues.some((issue) => issue.severity === "HIGH") ? "error" : "warning"}>
                  {detail.data.validationIssues.map((issue) => issue.message).join(" ")}
                </Alert>
              ) : (
                <Alert severity="success">No blocking time validation issues.</Alert>
              )}

              <Table size="small" className="data-table">
                <TableHead>
                  <TableRow>
                    <TableCell>Date</TableCell>
                    <TableCell>Clock in</TableCell>
                    <TableCell>Clock out</TableCell>
                    <TableCell>Breaks</TableCell>
                    <TableCell>Paid</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {(detail.data?.entries ?? []).map((entry) => (
                    <TableRow key={entry.id}>
                      <TableCell>{entry.entryDate}</TableCell>
                      <TableCell>{formatDateTime(entry.clockInAt)}</TableCell>
                      <TableCell>{entry.clockOutAt ? formatDateTime(entry.clockOutAt) : "Open"}</TableCell>
                      <TableCell>{entry.breaks.reduce((sum, item) => sum + (item.durationMinutes ?? 0), 0)} min</TableCell>
                      <TableCell>{formatHours(entry.paidHours)}</TableCell>
                      <TableCell>{entry.status}</TableCell>
                      <TableCell>
                        <Button size="small" startIcon={<Edit3 size={15} />} onClick={() => openEntryDialog(entry)}>
                          Edit
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>

              {selected.status === "APPROVED" && !selected.lockedPayPeriod ? (
                <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
                  <TextField size="small" label="Change request reason" value={changeReason} onChange={(event) => setChangeReason(event.target.value)} fullWidth />
                  <Button variant="outlined" onClick={() => requestChange.mutate({ id: selected.id, reason: changeReason })} disabled={!changeReason.trim()}>
                    Request change
                  </Button>
                </Stack>
              ) : null}

              {detail.data?.changeRequests.length ? (
                <Stack spacing={1}>
                  <Typography variant="subtitle2">Change requests</Typography>
                  {detail.data.changeRequests.map((request) => (
                    <Stack key={request.id} direction={{ xs: "column", sm: "row" }} spacing={1} alignItems={{ xs: "stretch", sm: "center" }}>
                      <Chip size="small" label={request.status} />
                      <Typography variant="body2" sx={{ flexGrow: 1 }}>{request.reason}</Typography>
                      {canManage && request.status === "PENDING" ? (
                        <>
                          <Button size="small" onClick={() => approveChange.mutate({ timesheetId: selected.id, requestId: request.id })}>Approve</Button>
                          <Button size="small" color="warning" onClick={() => rejectChange.mutate({ timesheetId: selected.id, requestId: request.id })}>Reject</Button>
                        </>
                      ) : null}
                    </Stack>
                  ))}
                </Stack>
              ) : null}
            </Stack>
          ) : (
            <Alert severity="info">Select a timesheet to review entries and actions.</Alert>
          )}
        </Paper>
      </Stack>

      <Dialog open={Boolean(entryForm)} onClose={() => setEntryForm(null)} fullWidth maxWidth="sm">
        <DialogTitle>{editingEntry ? "Edit time entry" : "Add time entry"}</DialogTitle>
        <DialogContent>
          {entryForm ? (
            <Stack spacing={2} sx={{ pt: 1 }}>
              <TextField type="datetime-local" label="Clock in" value={entryForm.clockInAt} onChange={(event) => setEntryForm({ ...entryForm, clockInAt: event.target.value })} InputLabelProps={{ shrink: true }} />
              <TextField type="datetime-local" label="Clock out" value={entryForm.clockOutAt} onChange={(event) => setEntryForm({ ...entryForm, clockOutAt: event.target.value })} InputLabelProps={{ shrink: true }} />
              <Divider />
              <TextField type="datetime-local" label="Break start" value={entryForm.breakStartAt} onChange={(event) => setEntryForm({ ...entryForm, breakStartAt: event.target.value })} InputLabelProps={{ shrink: true }} />
              <TextField type="datetime-local" label="Break end" value={entryForm.breakEndAt} onChange={(event) => setEntryForm({ ...entryForm, breakEndAt: event.target.value })} InputLabelProps={{ shrink: true }} />
              <TextField label="Note" value={entryForm.note} onChange={(event) => setEntryForm({ ...entryForm, note: event.target.value })} multiline minRows={2} />
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEntryForm(null)}>Cancel</Button>
          <Button variant="contained" onClick={saveEntryForm} disabled={!selected || !entryForm?.clockInAt}>
            Save entry
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );

  function openEntryDialog(entry?: TimeEntry) {
    setEditingEntry(entry ?? null);
    setEntryForm({
      clockInAt: entry ? toInputDateTime(entry.clockInAt) : toInputDateTime(new Date().toISOString()),
      clockOutAt: entry?.clockOutAt ? toInputDateTime(entry.clockOutAt) : "",
      breakStartAt: entry?.breaks[0]?.breakStartAt ? toInputDateTime(entry.breaks[0].breakStartAt) : "",
      breakEndAt: entry?.breaks[0]?.breakEndAt ? toInputDateTime(entry.breaks[0].breakEndAt) : "",
      note: entry?.note ?? ""
    });
  }

  function saveEntryForm() {
    if (!selected || !entryForm) {
      return;
    }
    saveEntry.mutate({
      timesheetId: selected.id,
      entryId: editingEntry?.id,
      request: {
        clockInAt: fromInputDateTime(entryForm.clockInAt),
        clockOutAt: entryForm.clockOutAt ? fromInputDateTime(entryForm.clockOutAt) : undefined,
        breakStartAt: entryForm.breakStartAt ? fromInputDateTime(entryForm.breakStartAt) : undefined,
        breakEndAt: entryForm.breakEndAt ? fromInputDateTime(entryForm.breakEndAt) : undefined,
        note: entryForm.note
      }
    });
  }
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }).format(new Date(value));
}

function formatHours(value: number) {
  return Number(value).toFixed(2);
}

function toInputDateTime(value: string) {
  const date = new Date(value);
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function fromInputDateTime(value: string) {
  return new Date(value).toISOString();
}
