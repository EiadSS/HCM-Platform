import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography
} from "@mui/material";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CalendarDays, CheckCircle2, ChevronLeft, ChevronRight, Edit3, Plus, Send, Trash2 } from "lucide-react";
import { useMemo, useState } from "react";
import { api } from "../../api/client";
import type { Shift, ShiftRequest, WeeklySchedule } from "../../types/api";
import { useAuth } from "../auth/AuthContext";

const OPEN_SHIFT_VALUE = "__OPEN_SHIFT__";

interface ShiftFormState {
  employeeId: string;
  departmentId: string;
  locationId: string;
  shiftDate: string;
  startTime: string;
  endTime: string;
}

export function SchedulingPanel() {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [weekStart, setWeekStart] = useState(() => formatDate(startOfWeek(new Date())));
  const [editingShift, setEditingShift] = useState<Shift | null>(null);
  const [form, setForm] = useState<ShiftFormState | null>(null);

  const schedule = useQuery({
    queryKey: ["schedule-week", weekStart],
    queryFn: () => api.scheduleWeek(weekStart)
  });
  const employees = useQuery({ queryKey: ["employees"], queryFn: api.employees });
  const organization = useQuery({ queryKey: ["organization"], queryFn: api.organization });

  const canManage = Boolean(user?.roles.some((role) => role === "MANAGER" || role === "HR_ADMIN" || role === "SYSTEM_ADMIN"));
  const days = useMemo(() => Array.from({ length: 7 }, (_, index) => addDays(weekStart, index)), [weekStart]);

  const updateScheduleCache = (data: WeeklySchedule) => {
    queryClient.setQueryData(["schedule-week", data.week.weekStartDate], data);
    void queryClient.invalidateQueries({ queryKey: ["schedule-alerts"] });
    void queryClient.invalidateQueries({ queryKey: ["dashboard"] });
  };

  const saveShift = useMutation({
    mutationFn: ({ shiftId, request }: { shiftId?: string; request: ShiftRequest }) =>
      shiftId ? api.updateShift(weekStart, shiftId, request) : api.createShift(weekStart, request),
    onSuccess: (data) => {
      updateScheduleCache(data);
      setEditingShift(null);
      setForm(null);
    }
  });

  const deleteShift = useMutation({
    mutationFn: (shiftId: string) => api.deleteShift(weekStart, shiftId),
    onSuccess: updateScheduleCache
  });

  const validateWeek = useMutation({
    mutationFn: () => api.validateScheduleWeek(weekStart),
    onSuccess: updateScheduleCache
  });

  const publishWeek = useMutation({
    mutationFn: () => api.publishScheduleWeek(weekStart),
    onSuccess: updateScheduleCache
  });

  const defaults = (shiftDate: string): ShiftFormState => ({
    employeeId: OPEN_SHIFT_VALUE,
    departmentId: organization.data?.departments[0]?.id ?? "",
    locationId: organization.data?.locations[0]?.id ?? "",
    shiftDate,
    startTime: "09:00",
    endTime: "17:00"
  });

  const openCreateDialog = (shiftDate = weekStart) => {
    setEditingShift(null);
    setForm(defaults(shiftDate));
  };

  const openEditDialog = (shift: Shift) => {
    setEditingShift(shift);
    setForm({
      employeeId: shift.employeeId ?? OPEN_SHIFT_VALUE,
      departmentId: shift.departmentId ?? organization.data?.departments[0]?.id ?? "",
      locationId: shift.locationId ?? organization.data?.locations[0]?.id ?? "",
      shiftDate: shift.shiftDate,
      startTime: toTimeInput(shift.startTime),
      endTime: toTimeInput(shift.endTime)
    });
  };

  const submitShift = () => {
    if (!form) {
      return;
    }
    saveShift.mutate({
      shiftId: editingShift?.id,
      request: {
        employeeId: form.employeeId === OPEN_SHIFT_VALUE ? null : form.employeeId,
        departmentId: form.departmentId,
        locationId: form.locationId,
        shiftDate: form.shiftDate,
        startTime: form.startTime,
        endTime: form.endTime
      }
    });
  };

  const mutationError = saveShift.error ?? deleteShift.error ?? validateWeek.error ?? publishWeek.error;

  if (schedule.isLoading) {
    return (
      <Stack alignItems="center" justifyContent="center" sx={{ minHeight: 320 }}>
        <CircularProgress />
      </Stack>
    );
  }

  if (schedule.error) {
    return <Alert severity="error">{schedule.error.message}</Alert>;
  }

  const validation = schedule.data?.validation;
  const shifts = schedule.data?.shifts ?? [];

  return (
    <Stack spacing={2.5}>
      <Stack direction={{ xs: "column", md: "row" }} justifyContent="space-between" spacing={1.5} alignItems={{ xs: "stretch", md: "center" }}>
        <Stack spacing={1}>
          <Stack direction="row" spacing={1} alignItems="center">
            <CalendarDays size={20} />
            <Typography variant="h6">Weekly schedule</Typography>
          </Stack>
          <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
            <Tooltip title="Previous week">
              <IconButton aria-label="Previous week" onClick={() => setWeekStart(addDays(weekStart, -7))}>
                <ChevronLeft size={18} />
              </IconButton>
            </Tooltip>
            <Typography variant="subtitle1">{weekRangeLabel(weekStart)}</Typography>
            <Tooltip title="Next week">
              <IconButton aria-label="Next week" onClick={() => setWeekStart(addDays(weekStart, 7))}>
                <ChevronRight size={18} />
              </IconButton>
            </Tooltip>
            <Chip size="small" label={schedule.data?.week.status ?? "DRAFT"} color={schedule.data?.week.status === "PUBLISHED" ? "success" : "default"} />
            <Chip size="small" label={`${shifts.length} shifts`} />
            <Chip size="small" label={`${schedule.data?.week.openShiftCount ?? 0} open`} color={schedule.data?.week.openShiftCount ? "warning" : "default"} />
            <Chip size="small" label={`${validation?.highSeverityCount ?? 0} blockers`} color={validation?.highSeverityCount ? "error" : "success"} />
          </Stack>
        </Stack>

        {canManage ? (
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <Button variant="outlined" startIcon={<Plus size={18} />} onClick={() => openCreateDialog()} disabled={!organization.data}>
              Add shift
            </Button>
            <Button variant="outlined" startIcon={<CheckCircle2 size={18} />} onClick={() => validateWeek.mutate()} disabled={validateWeek.isPending}>
              Validate
            </Button>
            <Button variant="contained" startIcon={<Send size={18} />} onClick={() => publishWeek.mutate()} disabled={publishWeek.isPending}>
              Publish
            </Button>
          </Stack>
        ) : (
          <Alert severity="info">Schedule editing is available to manager, HR admin, and system admin roles.</Alert>
        )}
      </Stack>

      {mutationError ? <Alert severity="error">{mutationError.message}</Alert> : null}

      {validation?.violations.length ? (
        <Paper className="schedule-validation" elevation={0}>
          <Stack spacing={1.5}>
            <Typography variant="subtitle2">Validation results</Typography>
            <Table size="small" className="data-table">
              <TableHead>
                <TableRow>
                  <TableCell>Severity</TableCell>
                  <TableCell>Type</TableCell>
                  <TableCell>Employee</TableCell>
                  <TableCell>Message</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {validation.violations.map((violation, index) => (
                  <TableRow key={`${violation.type}-${index}`}>
                    <TableCell>
                      <Chip size="small" label={violation.severity} color={violation.severity === "HIGH" ? "error" : "warning"} />
                    </TableCell>
                    <TableCell>{violation.type}</TableCell>
                    <TableCell>{violation.employeeName}</TableCell>
                    <TableCell>{violation.message}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Stack>
        </Paper>
      ) : (
        <Alert severity="success">No schedule validation issues for this week.</Alert>
      )}

      <Box className="schedule-week-grid">
        {days.map((day) => {
          const dayShifts = shifts.filter((shift) => shift.shiftDate === day);
          return (
            <Paper key={day} className="schedule-day" elevation={0}>
              <Stack spacing={1.25}>
                <Stack direction="row" alignItems="center" justifyContent="space-between">
                  <Box>
                    <Typography variant="subtitle2">{dayName(day)}</Typography>
                    <Typography variant="caption" color="text.secondary">
                      {shortDate(day)}
                    </Typography>
                  </Box>
                  {canManage ? (
                    <Tooltip title="Add shift">
                      <IconButton aria-label={`Add shift on ${day}`} size="small" onClick={() => openCreateDialog(day)} disabled={!organization.data}>
                        <Plus size={16} />
                      </IconButton>
                    </Tooltip>
                  ) : null}
                </Stack>
                <Divider />
                {dayShifts.length ? (
                  dayShifts.map((shift) => (
                    <Box key={shift.id} className={`schedule-shift ${shift.employeeId ? "assigned" : "open"}`}>
                      <Stack spacing={0.75}>
                        <Stack direction="row" justifyContent="space-between" spacing={1} alignItems="flex-start">
                          <Box>
                            <Typography variant="subtitle2">{shift.employeeName}</Typography>
                            <Typography variant="body2" color="text.secondary">
                              {toTimeInput(shift.startTime)}-{toTimeInput(shift.endTime)}
                            </Typography>
                          </Box>
                          <Chip size="small" label={shift.status} color={shift.employeeId ? "default" : "warning"} />
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                          {shift.departmentName ?? "Unassigned department"} - {shift.locationName ?? "Unassigned location"}
                        </Typography>
                        <Stack direction="row" spacing={0.5} alignItems="center" justifyContent="space-between">
                          <Chip size="small" variant="outlined" label={shift.published ? "Published" : "Draft"} />
                          {canManage ? (
                            <Stack direction="row" spacing={0.5}>
                              <Tooltip title="Edit shift">
                                <IconButton aria-label={`Edit ${shift.employeeName}`} size="small" onClick={() => openEditDialog(shift)}>
                                  <Edit3 size={15} />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Delete shift">
                                <IconButton
                                  aria-label={`Delete ${shift.employeeName}`}
                                  size="small"
                                  onClick={() => {
                                    if (window.confirm("Delete this shift from the draft schedule?")) {
                                      deleteShift.mutate(shift.id);
                                    }
                                  }}
                                >
                                  <Trash2 size={15} />
                                </IconButton>
                              </Tooltip>
                            </Stack>
                          ) : null}
                        </Stack>
                      </Stack>
                    </Box>
                  ))
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    No shifts
                  </Typography>
                )}
              </Stack>
            </Paper>
          );
        })}
      </Box>

      <Dialog open={Boolean(form)} onClose={() => setForm(null)} fullWidth maxWidth="sm">
        <DialogTitle>{editingShift ? "Edit shift" : "Create shift"}</DialogTitle>
        <DialogContent>
          {form ? (
            <Stack spacing={2} sx={{ pt: 1 }}>
              <TextField select label="Employee" value={form.employeeId} onChange={(event) => setForm({ ...form, employeeId: event.target.value })}>
                <MenuItem value={OPEN_SHIFT_VALUE}>Open Shift</MenuItem>
                {(employees.data ?? []).map((employee) => (
                  <MenuItem key={employee.id} value={employee.id}>
                    {employee.fullName}
                  </MenuItem>
                ))}
              </TextField>
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                <TextField
                  select
                  fullWidth
                  label="Department"
                  value={form.departmentId}
                  onChange={(event) => setForm({ ...form, departmentId: event.target.value })}
                >
                  {(organization.data?.departments ?? []).map((department) => (
                    <MenuItem key={department.id} value={department.id}>
                      {department.name}
                    </MenuItem>
                  ))}
                </TextField>
                <TextField
                  select
                  fullWidth
                  label="Location"
                  value={form.locationId}
                  onChange={(event) => setForm({ ...form, locationId: event.target.value })}
                >
                  {(organization.data?.locations ?? []).map((location) => (
                    <MenuItem key={location.id} value={location.id}>
                      {location.name}
                    </MenuItem>
                  ))}
                </TextField>
              </Stack>
              <TextField type="date" label="Shift date" value={form.shiftDate} onChange={(event) => setForm({ ...form, shiftDate: event.target.value })} InputLabelProps={{ shrink: true }} />
              <Stack direction={{ xs: "column", sm: "row" }} spacing={2}>
                <TextField
                  fullWidth
                  type="time"
                  label="Start time"
                  value={form.startTime}
                  onChange={(event) => setForm({ ...form, startTime: event.target.value })}
                  InputLabelProps={{ shrink: true }}
                />
                <TextField
                  fullWidth
                  type="time"
                  label="End time"
                  value={form.endTime}
                  onChange={(event) => setForm({ ...form, endTime: event.target.value })}
                  InputLabelProps={{ shrink: true }}
                />
              </Stack>
            </Stack>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setForm(null)}>Cancel</Button>
          <Button variant="contained" onClick={submitShift} disabled={saveShift.isPending || !form?.departmentId || !form?.locationId}>
            Save shift
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}

function startOfWeek(date: Date) {
  const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const day = copy.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  copy.setDate(copy.getDate() + diff);
  return copy;
}

function addDays(dateValue: string, days: number) {
  const date = parseDate(dateValue);
  date.setDate(date.getDate() + days);
  return formatDate(date);
}

function parseDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function dayName(value: string) {
  return new Intl.DateTimeFormat(undefined, { weekday: "short" }).format(parseDate(value));
}

function shortDate(value: string) {
  return new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric" }).format(parseDate(value));
}

function weekRangeLabel(weekStart: string) {
  return `${shortDate(weekStart)} - ${new Intl.DateTimeFormat(undefined, { month: "short", day: "numeric", year: "numeric" }).format(parseDate(addDays(weekStart, 6)))}`;
}

function toTimeInput(value: string) {
  return value.slice(0, 5);
}
