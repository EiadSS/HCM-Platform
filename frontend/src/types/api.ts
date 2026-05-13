export type UserRole = "EMPLOYEE" | "MANAGER" | "HR_ADMIN" | "PAYROLL_ADMIN" | "SYSTEM_ADMIN";

export interface MeResponse {
  userId: string;
  tenantId: string;
  email: string;
  displayName: string;
  roles: UserRole[];
  demoMode: boolean;
}

export interface AuthResponse {
  token: string;
  user: MeResponse;
}

export interface MetricCard {
  label: string;
  value: string;
  tone: "warning" | "danger" | "success" | "info";
  detail: string;
}

export interface WorkItem {
  type: string;
  title: string;
  detail: string;
  severity: string;
}

export interface DashboardResponse {
  tenantName: string;
  roles: UserRole[];
  metrics: MetricCard[];
  priorityWork: WorkItem[];
  quickActions: string[];
  generatedAt: string;
}

export interface Employee {
  id: string;
  employeeNumber: string;
  firstName: string;
  lastName: string;
  fullName: string;
  workEmail: string;
  status: string;
  employmentType: string;
  departmentName?: string;
  locationName?: string;
  jobTitleName?: string;
  managerName?: string;
  hourlyRate: number;
  weeklyHourCap: number;
  hireDate: string;
}

export interface Department {
  id: string;
  name: string;
  costCenter: string;
}

export interface Location {
  id: string;
  name: string;
  timezone: string;
  region: string;
}

export interface JobTitle {
  id: string;
  name: string;
  careerLevel: string;
}

export interface OrganizationResponse {
  departments: Department[];
  locations: Location[];
  jobTitles: JobTitle[];
}

export interface ScheduleAlert {
  id: string;
  employeeName: string;
  weekStartDate: string;
  alertType: string;
  severity: string;
  message: string;
  status: string;
  createdAt?: string;
}

export interface ScheduleWeek {
  id: string;
  weekStartDate: string;
  status: "DRAFT" | "PUBLISHED";
  publishedAt?: string;
  publishedByUserId?: string;
  shiftCount: number;
  openShiftCount: number;
  violationCount: number;
  highSeverityCount: number;
}

export interface Shift {
  id: string;
  employeeId?: string | null;
  employeeName: string;
  departmentId?: string | null;
  departmentName?: string;
  locationId?: string | null;
  locationName?: string;
  shiftDate: string;
  startTime: string;
  endTime: string;
  status: string;
  published: boolean;
}

export interface ScheduleViolation {
  type: string;
  severity: "HIGH" | "MEDIUM" | "LOW";
  employeeName: string;
  message: string;
}

export interface ValidationResult {
  valid: boolean;
  highSeverityCount: number;
  violations: ScheduleViolation[];
}

export interface WeeklySchedule {
  week: ScheduleWeek;
  shifts: Shift[];
  alerts: ScheduleAlert[];
  validation: ValidationResult;
}

export interface ShiftRequest {
  employeeId: string | null;
  departmentId: string;
  locationId: string;
  shiftDate: string;
  startTime: string;
  endTime: string;
}

export interface Timesheet {
  id: string;
  employeeId: string;
  employeeName: string;
  weekStartDate: string;
  regularHours: number;
  overtimeHours: number;
  status: string;
  lockedPayPeriod: boolean;
  managerNote?: string;
  submittedAt?: string;
  approvedAt?: string;
}

export interface TimeBreak {
  id: string;
  breakStartAt: string;
  breakEndAt?: string;
  durationMinutes?: number;
  source: string;
  note?: string;
}

export interface TimeEntry {
  id: string;
  employeeId: string;
  employeeName: string;
  shiftId?: string;
  entryDate: string;
  clockInAt: string;
  clockOutAt?: string;
  source: string;
  status: string;
  note?: string;
  paidHours: number;
  breaks: TimeBreak[];
}

export interface TimesheetValidationIssue {
  type: string;
  severity: "HIGH" | "MEDIUM" | "LOW";
  message: string;
}

export interface TimesheetChangeRequest {
  id: string;
  timesheetId: string;
  requesterEmail: string;
  reason: string;
  status: string;
  decisionNote?: string;
  decidedAt?: string;
  createdAt?: string;
}

export interface TimesheetDetail {
  timesheet: Timesheet;
  entries: TimeEntry[];
  changeRequests: TimesheetChangeRequest[];
  validationIssues: TimesheetValidationIssue[];
  history: AuditLog[];
}

export interface TimeStatus {
  currentTimesheet: Timesheet;
  activeEntry?: TimeEntry;
  activeBreak?: TimeBreak;
  validationIssues: TimesheetValidationIssue[];
}

export interface ClockRequest {
  occurredAt?: string;
  note?: string;
}

export interface ManualTimeEntryRequest {
  clockInAt: string;
  clockOutAt?: string;
  breakStartAt?: string;
  breakEndAt?: string;
  note?: string;
}

export interface ChangeRequestRequest {
  reason: string;
}

export interface PayrollPreview {
  id: string;
  periodStart: string;
  periodEnd: string;
  locationId?: string;
  regularHours: number;
  overtimeHours: number;
  unpaidBreakHours: number;
  unpaidLeaveHours: number;
  holidayHours: number;
  holidayPremiumPay: number;
  grossPay: number;
  employeeCount: number;
  timesheetCount: number;
  status: string;
  explanation: string;
}

export interface PayrollPreviewLine {
  id: string;
  employeeId: string;
  employeeName: string;
  locationId?: string;
  locationName?: string;
  timesheetCount: number;
  hourlyRate: number;
  regularHours: number;
  overtimeHours: number;
  holidayHours: number;
  unpaidBreakHours: number;
  unpaidLeaveHours: number;
  regularPay: number;
  overtimePay: number;
  holidayPremiumPay: number;
  grossPay: number;
  ruleName: string;
  explanation: string;
}

export interface PayrollPreviewDetail {
  preview: PayrollPreview;
  lines: PayrollPreviewLine[];
}

export interface PayrollPreviewRequest {
  periodStart?: string;
  periodEnd?: string;
  locationId?: string;
}

export interface LeaveRequest {
  id: string;
  employeeId: string;
  employeeName: string;
  requestedByUserId?: string;
  leaveType: string;
  startDate: string;
  endDate: string;
  hours: number;
  status: string;
  conflict: boolean;
  conflictCount: number;
  conflictSummary?: string;
  submittedAt?: string;
  decidedByUserId?: string;
  decidedAt?: string;
  employeeNote?: string;
  managerNote?: string;
  decisionNote?: string;
}

export interface LeaveRequestCreate {
  leaveType: "VACATION" | "SICK" | "UNPAID";
  startDate: string;
  endDate: string;
  hours: number;
  note?: string;
}

export interface LeaveDecisionRequest {
  note?: string;
}

export interface LeaveBalance {
  id: string;
  employeeId: string;
  employeeName: string;
  leaveType: string;
  accruedHours: number;
  usedHours: number;
  pendingHours: number;
  availableHours: number;
  maxHours: number;
}

export interface LeaveCalendarEntry {
  id: string;
  employeeId: string;
  employeeName: string;
  leaveType: string;
  startDate: string;
  endDate: string;
  hours: number;
  status: string;
  conflict: boolean;
  conflictSummary?: string;
}

export interface LeaveAccrualRunRequest {
  asOfDate?: string;
}

export interface LeaveAccrualRunResult {
  accrualPeriod: string;
  balancesUpdated: number;
  hoursAccrued: number;
}

export interface ImportJob {
  id: string;
  fileName: string;
  status: string;
  totalRows: number;
  successRows: number;
  errorRows: number;
  committedRows: number;
  summary: string;
  queuedAt?: string;
  startedAt?: string;
  previewedAt?: string;
  committedAt?: string;
  completedAt?: string;
  failedAt?: string;
}

export interface EmployeeImportPreviewRequest {
  fileName: string;
  csvContent: string;
  fieldMapping: Record<string, string>;
}

export interface ImportRowError {
  rowNumber: number;
  field: string;
  message: string;
}

export interface ImportRow {
  id: string;
  rowNumber: number;
  status: string;
  rawValues: Record<string, string>;
  mappedValues: Record<string, string>;
  errors: ImportRowError[];
  importedEmployeeId?: string;
}

export interface ImportJobDetail {
  job: ImportJob;
  detectedHeaders: string[];
  fieldMapping: Record<string, string>;
  rows: ImportRow[];
}

export interface TimesheetExportRow {
  timesheetId: string;
  employeeId: string;
  employeeName: string;
  weekStartDate: string;
  regularHours: number;
  overtimeHours: number;
  status: string;
  approvedAt?: string;
  lockedPayPeriod: boolean;
  managerNote?: string;
}

export interface WebhookDeliveryAttempt {
  id: string;
  destinationName: string;
  destinationUrl: string;
  status: string;
  responseCode?: number;
  responseBody?: string;
  attemptedAt: string;
}

export interface WebhookEvent {
  id: string;
  eventType: string;
  entityType: string;
  entityId?: string;
  status: string;
  generatedAt: string;
  latestAttempt?: WebhookDeliveryAttempt;
}

export interface WebhookEventDetail {
  event: WebhookEvent;
  payloadJson: string;
  attempts: WebhookDeliveryAttempt[];
}

export interface AuditLog {
  id: string;
  timestamp: string;
  actorEmail: string;
  actionType: string;
  entityType: string;
  entityId?: string;
  previousValue?: string;
  newValue?: string;
  metadata?: string;
}

export interface AuditLogFilters {
  from?: string;
  to?: string;
  actorEmail?: string;
  actionType?: string;
  entityType?: string;
  entityId?: string;
  limit?: number;
}

export interface AnalyticsEventRequest {
  eventType: string;
  path?: string;
  referrer?: string;
  metadataJson?: string;
}

export interface AnalyticsMetric {
  label: string;
  value: number;
}

export interface AnalyticsSummary {
  totalEvents: number;
  totalVisits: number;
  uniqueVisitors: number;
  activeVisitors: number;
  lastUsedAt?: string;
  totalLogins: number;
  topPages: AnalyticsMetric[];
  loginRoles: AnalyticsMetric[];
}

export interface AnalyticsEvent {
  id: string;
  occurredAt: string;
  eventType: string;
  path?: string;
  referrer?: string;
  accountEmail?: string;
  accountRole?: string;
  metadataJson?: string;
}

export interface AnalyticsFilters {
  from?: string;
  to?: string;
}
