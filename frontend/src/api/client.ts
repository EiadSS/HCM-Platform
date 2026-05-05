import type {
  AuditLog,
  AuditLogFilters,
  AuthResponse,
  ChangeRequestRequest,
  ClockRequest,
  DashboardResponse,
  Employee,
  EmployeeImportPreviewRequest,
  ImportJob,
  ImportJobDetail,
  LeaveAccrualRunRequest,
  LeaveAccrualRunResult,
  LeaveBalance,
  LeaveCalendarEntry,
  LeaveDecisionRequest,
  LeaveRequest,
  LeaveRequestCreate,
  ManualTimeEntryRequest,
  MeResponse,
  OrganizationResponse,
  PayrollPreview,
  PayrollPreviewDetail,
  PayrollPreviewRequest,
  ScheduleAlert,
  ScheduleWeek,
  ShiftRequest,
  Timesheet,
  TimesheetChangeRequest,
  TimesheetDetail,
  TimeEntry,
  TimeStatus,
  TimesheetExportRow,
  WebhookEvent,
  WebhookEventDetail,
  WeeklySchedule
} from "../types/api";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api/v1";
const TOKEN_KEY = "hcm_demo_token";

function backendBaseUrl() {
  return API_BASE_URL.replace(/\/api\/v1\/?$/, "");
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY);
}

export async function wakeBackend() {
  const response = await fetch(`${backendBaseUrl()}/actuator/health`, {
    cache: "no-store"
  });
  return response.ok;
}

export async function downloadTimesheetsCsv() {
  const token = getToken();
  const response = await fetch(`${API_BASE_URL}/integrations/exports/timesheets.csv`, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  });
  if (!response.ok) {
    throw new Error("Unable to export timesheets");
  }
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = "approved-timesheets-demo.csv";
  link.click();
  URL.revokeObjectURL(href);
}

export async function downloadTimesheetsJson() {
  const rows = await apiFetch<TimesheetExportRow[]>("/integrations/exports/timesheets.json");
  const blob = new Blob([JSON.stringify(rows, null, 2)], { type: "application/json" });
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = "approved-timesheets-demo.json";
  link.click();
  URL.revokeObjectURL(href);
}

export async function downloadImportErrors(importJobId: string) {
  const token = getToken();
  const response = await fetch(`${API_BASE_URL}/integrations/imports/employees/${importJobId}/errors.csv`, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  });
  if (!response.ok) {
    throw new Error("Unable to download import error report");
  }
  const blob = await response.blob();
  const href = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = href;
  link.download = "employee-import-errors.csv";
  link.click();
  URL.revokeObjectURL(href);
}

async function apiFetch<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getToken();
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers
    }
  });

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }));
    throw new Error(error.message ?? "API request failed");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

export const api = {
  login: (email: string, password: string) =>
    apiFetch<AuthResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    }),
  wakeBackend,
  me: () => apiFetch<MeResponse>("/auth/me"),
  dashboard: () => apiFetch<DashboardResponse>("/dashboard"),
  employees: () => apiFetch<Employee[]>("/employees"),
  organization: () => apiFetch<OrganizationResponse>("/organization"),
  scheduleAlerts: () => apiFetch<ScheduleAlert[]>("/schedules/alerts"),
  scheduleWeeks: (from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) {
      params.set("from", from);
    }
    if (to) {
      params.set("to", to);
    }
    const query = params.toString();
    return apiFetch<ScheduleWeek[]>(`/schedules/weeks${query ? `?${query}` : ""}`);
  },
  scheduleWeek: (weekStartDate: string) => apiFetch<WeeklySchedule>(`/schedules/weeks/${weekStartDate}`),
  createShift: (weekStartDate: string, request: ShiftRequest) =>
    apiFetch<WeeklySchedule>(`/schedules/weeks/${weekStartDate}/shifts`, {
      method: "POST",
      body: JSON.stringify(request)
    }),
  updateShift: (weekStartDate: string, shiftId: string, request: ShiftRequest) =>
    apiFetch<WeeklySchedule>(`/schedules/weeks/${weekStartDate}/shifts/${shiftId}`, {
      method: "PUT",
      body: JSON.stringify(request)
    }),
  deleteShift: (weekStartDate: string, shiftId: string) =>
    apiFetch<WeeklySchedule>(`/schedules/weeks/${weekStartDate}/shifts/${shiftId}`, {
      method: "DELETE"
    }),
  validateScheduleWeek: (weekStartDate: string) =>
    apiFetch<WeeklySchedule>(`/schedules/weeks/${weekStartDate}/validate`, { method: "POST" }),
  publishScheduleWeek: (weekStartDate: string) =>
    apiFetch<WeeklySchedule>(`/schedules/weeks/${weekStartDate}/publish`, { method: "POST" }),
  timesheets: () => apiFetch<Timesheet[]>("/timesheets"),
  timeStatus: () => apiFetch<TimeStatus>("/time/me/status"),
  clockIn: (request: ClockRequest = {}) =>
    apiFetch<TimeStatus>("/time/clock-in", {
      method: "POST",
      body: JSON.stringify(request)
    }),
  clockOut: (request: ClockRequest = {}) =>
    apiFetch<TimeStatus>("/time/clock-out", {
      method: "POST",
      body: JSON.stringify(request)
    }),
  startBreak: (entryId: string, request: ClockRequest = {}) =>
    apiFetch<TimeEntry>(`/time/entries/${entryId}/breaks/start`, {
      method: "POST",
      body: JSON.stringify(request)
    }),
  endBreak: (breakId: string, request: ClockRequest = {}) =>
    apiFetch<TimeEntry>(`/time/breaks/${breakId}/end`, {
      method: "POST",
      body: JSON.stringify(request)
    }),
  timesheetDetail: (id: string) => apiFetch<TimesheetDetail>(`/timesheets/${id}`),
  currentTimesheetWeek: (weekStartDate: string) => apiFetch<TimesheetDetail>(`/timesheets/weeks/${weekStartDate}`),
  addTimeEntry: (timesheetId: string, request: ManualTimeEntryRequest) =>
    apiFetch<TimesheetDetail>(`/timesheets/${timesheetId}/entries`, {
      method: "POST",
      body: JSON.stringify(request)
    }),
  updateTimeEntry: (timesheetId: string, entryId: string, request: ManualTimeEntryRequest) =>
    apiFetch<TimesheetDetail>(`/timesheets/${timesheetId}/entries/${entryId}`, {
      method: "PUT",
      body: JSON.stringify(request)
    }),
  deleteTimeEntry: (timesheetId: string, entryId: string) =>
    apiFetch<TimesheetDetail>(`/timesheets/${timesheetId}/entries/${entryId}`, {
      method: "DELETE"
    }),
  submitTimesheet: (id: string) => apiFetch<Timesheet>(`/timesheets/${id}/submit`, { method: "POST" }),
  approveTimesheet: (id: string) =>
    apiFetch<Timesheet>(`/timesheets/${id}/approve`, {
      method: "POST",
      body: JSON.stringify({ note: "Approved during recruiter walkthrough" })
    }),
  rejectTimesheet: (id: string, note = "Rejected for correction") =>
    apiFetch<Timesheet>(`/timesheets/${id}/reject`, {
      method: "POST",
      body: JSON.stringify({ note })
    }),
  requestTimesheetChange: (id: string, request: ChangeRequestRequest) =>
    apiFetch<TimesheetChangeRequest>(`/timesheets/${id}/change-requests`, {
      method: "POST",
      body: JSON.stringify(request)
    }),
  approveTimesheetChangeRequest: (timesheetId: string, requestId: string, note = "Approved for correction") =>
    apiFetch<TimesheetChangeRequest>(`/timesheets/${timesheetId}/change-requests/${requestId}/approve`, {
      method: "POST",
      body: JSON.stringify({ note })
    }),
  rejectTimesheetChangeRequest: (timesheetId: string, requestId: string, note = "Rejected") =>
    apiFetch<TimesheetChangeRequest>(`/timesheets/${timesheetId}/change-requests/${requestId}/reject`, {
      method: "POST",
      body: JSON.stringify({ note })
    }),
  lockTimesheet: (id: string) => apiFetch<Timesheet>(`/timesheets/${id}/lock`, { method: "POST" }),
  unlockTimesheet: (id: string) => apiFetch<Timesheet>(`/timesheets/${id}/unlock`, { method: "POST" }),
  payrollPreviews: () => apiFetch<PayrollPreview[]>("/payroll/previews"),
  payrollPreviewDetail: (id: string) => apiFetch<PayrollPreviewDetail>(`/payroll/previews/${id}`),
  generatePayrollPreview: (request?: PayrollPreviewRequest) =>
    apiFetch<PayrollPreviewDetail>("/payroll/previews/generate", {
      method: "POST",
      body: JSON.stringify(request ?? {})
    }),
  leaveRequests: (from?: string, to?: string, mine?: boolean) => {
    const params = new URLSearchParams();
    if (from) {
      params.set("from", from);
    }
    if (to) {
      params.set("to", to);
    }
    if (mine !== undefined) {
      params.set("mine", String(mine));
    }
    const query = params.toString();
    return apiFetch<LeaveRequest[]>(`/leave/requests${query ? `?${query}` : ""}`);
  },
  createLeaveRequest: (request: LeaveRequestCreate) =>
    apiFetch<LeaveRequest>("/leave/requests", {
      method: "POST",
      body: JSON.stringify(request)
    }),
  approveLeaveRequest: (id: string, request: LeaveDecisionRequest = {}) =>
    apiFetch<LeaveRequest>(`/leave/requests/${id}/approve`, {
      method: "POST",
      body: JSON.stringify(request)
    }),
  rejectLeaveRequest: (id: string, request: LeaveDecisionRequest = {}) =>
    apiFetch<LeaveRequest>(`/leave/requests/${id}/reject`, {
      method: "POST",
      body: JSON.stringify(request)
    }),
  leaveBalances: (employeeId?: string) => apiFetch<LeaveBalance[]>(`/leave/balances${employeeId ? `?employeeId=${employeeId}` : ""}`),
  leaveCalendar: (from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) {
      params.set("from", from);
    }
    if (to) {
      params.set("to", to);
    }
    const query = params.toString();
    return apiFetch<LeaveCalendarEntry[]>(`/leave/calendar${query ? `?${query}` : ""}`);
  },
  runLeaveAccruals: (request?: LeaveAccrualRunRequest) =>
    apiFetch<LeaveAccrualRunResult>("/leave/accruals/run", {
      method: "POST",
      body: JSON.stringify(request ?? {})
    }),
  imports: () => apiFetch<ImportJob[]>("/integrations/imports/employees"),
  previewEmployeeImport: (request: EmployeeImportPreviewRequest) =>
    apiFetch<ImportJobDetail>("/integrations/imports/employees/preview", {
      method: "POST",
      body: JSON.stringify(request)
    }),
  employeeImportDetail: (id: string) => apiFetch<ImportJobDetail>(`/integrations/imports/employees/${id}`),
  commitEmployeeImport: (id: string) => apiFetch<ImportJobDetail>(`/integrations/imports/employees/${id}/commit`, { method: "POST" }),
  downloadImportErrors,
  webhookEvents: () => apiFetch<WebhookEvent[]>("/integrations/webhooks/events"),
  webhookEventDetail: (id: string) => apiFetch<WebhookEventDetail>(`/integrations/webhooks/events/${id}`),
  redeliverWebhookEvent: (id: string) => apiFetch<WebhookEventDetail>(`/integrations/webhooks/events/${id}/redeliver`, { method: "POST" }),
  auditLogs: (filters: AuditLogFilters = {}) => {
    const params = new URLSearchParams();
    if (filters.from) {
      params.set("from", filters.from);
    }
    if (filters.to) {
      params.set("to", filters.to);
    }
    if (filters.actorEmail) {
      params.set("actorEmail", filters.actorEmail);
    }
    if (filters.actionType) {
      params.set("actionType", filters.actionType);
    }
    if (filters.entityType) {
      params.set("entityType", filters.entityType);
    }
    if (filters.entityId) {
      params.set("entityId", filters.entityId);
    }
    if (filters.limit) {
      params.set("limit", String(filters.limit));
    }
    const query = params.toString();
    return apiFetch<AuditLog[]>(`/audit-logs${query ? `?${query}` : ""}`);
  },
  exportTimesheetsCsv: downloadTimesheetsCsv,
  exportTimesheetsJson: downloadTimesheetsJson,
  resetDemo: () => apiFetch<{ demoMode: boolean; tenantName: string; checkedAt: string }>("/demo/reset", { method: "POST" })
};
