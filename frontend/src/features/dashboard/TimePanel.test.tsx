import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import type { MeResponse, TimeStatus, Timesheet, TimesheetDetail } from "../../types/api";
import { TimePanel } from "./TimePanel";

vi.mock("../../api/client", () => ({
  api: {
    timesheets: vi.fn(),
    timeStatus: vi.fn(),
    timesheetDetail: vi.fn(),
    clockIn: vi.fn(),
    clockOut: vi.fn(),
    startBreak: vi.fn(),
    endBreak: vi.fn(),
    submitTimesheet: vi.fn(),
    approveTimesheet: vi.fn(),
    rejectTimesheet: vi.fn(),
    lockTimesheet: vi.fn(),
    unlockTimesheet: vi.fn(),
    requestTimesheetChange: vi.fn(),
    approveTimesheetChangeRequest: vi.fn(),
    rejectTimesheetChangeRequest: vi.fn(),
    addTimeEntry: vi.fn(),
    updateTimeEntry: vi.fn()
  }
}));

let mockUser: MeResponse;

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: mockUser })
}));

const mockedApi = vi.mocked(api);

describe("TimePanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser = {
      userId: "user-1",
      tenantId: "tenant-1",
      email: "employee@demo.hcm.local",
      displayName: "Jordan Kim",
      roles: ["EMPLOYEE"],
      demoMode: true
    };
    mockedApi.timesheets.mockResolvedValue([timesheet("DRAFT")]);
    mockedApi.timeStatus.mockResolvedValue(timeStatus());
    mockedApi.timesheetDetail.mockResolvedValue(detail("DRAFT"));
    mockedApi.clockOut.mockResolvedValue(timeStatus());
    mockedApi.submitTimesheet.mockResolvedValue(timesheet("SUBMITTED"));
    mockedApi.approveTimesheet.mockResolvedValue(timesheet("APPROVED"));
  });

  it("renders employee clock status and active punch controls", async () => {
    renderPanel();

    expect(await screen.findByText(/Clocked in since/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Clock out/i })).toBeInTheDocument();
    expect(screen.getByText("Jordan Kim")).toBeInTheDocument();
  });

  it("lets an employee submit a draft timesheet", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Submit" }));

    await waitFor(() =>
      expect(mockedApi.submitTimesheet).toHaveBeenCalledWith("timesheet-1", expect.any(Object))
    );
  });

  it("lets managers approve submitted timesheets", async () => {
    mockUser = {
      userId: "manager-1",
      tenantId: "tenant-1",
      email: "manager@demo.hcm.local",
      displayName: "Maya Thompson",
      roles: ["MANAGER"],
      demoMode: true
    };
    mockedApi.timesheets.mockResolvedValue([timesheet("SUBMITTED")]);
    mockedApi.timesheetDetail.mockResolvedValue(detail("SUBMITTED"));
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Approve" }));

    await waitFor(() =>
      expect(mockedApi.approveTimesheet).toHaveBeenCalledWith("timesheet-1", expect.any(Object))
    );
  });
});

function renderPanel() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false }
    }
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <TimePanel />
    </QueryClientProvider>
  );
}

function timesheet(status: string): Timesheet {
  return {
    id: "timesheet-1",
    employeeId: "emp-1",
    employeeName: "Jordan Kim",
    weekStartDate: "2026-05-04",
    regularHours: 11.4,
    overtimeHours: 0,
    status,
    lockedPayPeriod: false
  };
}

function detail(status: string): TimesheetDetail {
  return {
    timesheet: timesheet(status),
    entries: [
      {
        id: "entry-1",
        employeeId: "emp-1",
        employeeName: "Jordan Kim",
        entryDate: "2026-05-04",
        clockInAt: "2026-05-04T13:00:00Z",
        clockOutAt: "2026-05-04T21:00:00Z",
        source: "CLOCK",
        status: "COMPLETE",
        paidHours: 8,
        breaks: []
      }
    ],
    changeRequests: [],
    validationIssues: [],
    history: []
  };
}

function timeStatus(): TimeStatus {
  return {
    currentTimesheet: timesheet("DRAFT"),
    activeEntry: {
      id: "entry-active",
      employeeId: "emp-1",
      employeeName: "Jordan Kim",
      entryDate: "2026-05-04",
      clockInAt: "2026-05-04T13:00:00Z",
      source: "CLOCK",
      status: "OPEN",
      paidHours: 0,
      breaks: []
    },
    validationIssues: []
  };
}
