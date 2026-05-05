import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import type { LeaveBalance, LeaveCalendarEntry, LeaveRequest, MeResponse } from "../../types/api";
import { LeavePanel } from "./LeavePanel";

vi.mock("../../api/client", () => ({
  api: {
    leaveBalances: vi.fn(),
    leaveRequests: vi.fn(),
    leaveCalendar: vi.fn(),
    createLeaveRequest: vi.fn(),
    approveLeaveRequest: vi.fn(),
    rejectLeaveRequest: vi.fn(),
    runLeaveAccruals: vi.fn()
  }
}));

let mockUser: MeResponse;

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: mockUser })
}));

const mockedApi = vi.mocked(api);

describe("LeavePanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser = {
      userId: "employee-user",
      tenantId: "tenant-1",
      email: "employee@demo.hcm.local",
      displayName: "Jordan Kim",
      roles: ["EMPLOYEE"],
      demoMode: true
    };
    mockedApi.leaveBalances.mockResolvedValue(balances());
    mockedApi.leaveRequests.mockResolvedValue(requests());
    mockedApi.leaveCalendar.mockResolvedValue(calendar());
    mockedApi.createLeaveRequest.mockResolvedValue(requests()[0]);
    mockedApi.approveLeaveRequest.mockResolvedValue({ ...requests()[0], status: "APPROVED", decisionNote: "Approved with coverage" });
    mockedApi.rejectLeaveRequest.mockResolvedValue({ ...requests()[0], status: "REJECTED", decisionNote: "Rejected for coverage" });
    mockedApi.runLeaveAccruals.mockResolvedValue({ accrualPeriod: "2026-05-01", balancesUpdated: 2, hoursAccrued: 14 });
  });

  afterEach(() => {
    cleanup();
  });

  it("renders balances, requests, and calendar hybrid entries", async () => {
    renderPanel();

    expect(await screen.findByText("40.00 h available")).toBeInTheDocument();
    expect(screen.getAllByText("Jordan Kim").length).toBeGreaterThan(0);
    expect(screen.getAllByText("VACATION").length).toBeGreaterThan(0);
    expect(screen.getByText(/scheduled shift/i)).toBeInTheDocument();
  });

  it("lets employees submit vacation, sick, or unpaid requests", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Submit request" }));

    await waitFor(() =>
      expect(mockedApi.createLeaveRequest).toHaveBeenCalledWith(
        expect.objectContaining({
          leaveType: "VACATION",
          hours: 8
        })
      )
    );
  });

  it("lets managers approve and reject pending requests with conflict warnings", async () => {
    mockUser = {
      userId: "manager-user",
      tenantId: "tenant-1",
      email: "manager@demo.hcm.local",
      displayName: "Maya Thompson",
      roles: ["MANAGER"],
      demoMode: true
    };
    const user = userEvent.setup();
    renderPanel();

    expect(await screen.findByText("2 schedule conflict(s)")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Decision note"), "Coverage arranged");
    await user.click(screen.getByRole("button", { name: "Approve" }));
    await waitFor(() =>
      expect(mockedApi.approveLeaveRequest).toHaveBeenCalledWith(
        "leave-1",
        expect.objectContaining({
          note: "Coverage arranged"
        })
      )
    );

    await user.click(screen.getByRole("button", { name: "Reject" }));
    await waitFor(() => expect(mockedApi.rejectLeaveRequest).toHaveBeenCalledWith("leave-1", expect.any(Object)));
  });

  it("displays balance errors from the API clearly", async () => {
    mockedApi.createLeaveRequest.mockRejectedValue(new Error("Insufficient vacation balance. Available: 2.00 hours"));
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Submit request" }));

    expect(await screen.findByText(/Insufficient vacation balance/i)).toBeInTheDocument();
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
      <LeavePanel />
    </QueryClientProvider>
  );
}

function balances(): LeaveBalance[] {
  return [
    {
      id: "balance-1",
      employeeId: "emp-1",
      employeeName: "Jordan Kim",
      leaveType: "VACATION",
      accruedHours: 64,
      usedHours: 8,
      pendingHours: 16,
      availableHours: 40,
      maxHours: 80
    },
    {
      id: "balance-2",
      employeeId: "emp-1",
      employeeName: "Jordan Kim",
      leaveType: "SICK",
      accruedHours: 22,
      usedHours: 0,
      pendingHours: 0,
      availableHours: 22,
      maxHours: 40
    }
  ];
}

function requests(): LeaveRequest[] {
  return [
    {
      id: "leave-1",
      employeeId: "emp-1",
      employeeName: "Jordan Kim",
      requestedByUserId: "employee-user",
      leaveType: "VACATION",
      startDate: "2026-05-12",
      endDate: "2026-05-13",
      hours: 16,
      status: "PENDING",
      conflict: true,
      conflictCount: 2,
      conflictSummary: "Conflicts with 2 scheduled shift(s): 2026-05-12, 2026-05-13",
      submittedAt: "2026-05-04T12:00:00Z",
      employeeNote: "Family trip"
    }
  ];
}

function calendar(): LeaveCalendarEntry[] {
  return [
    {
      id: "leave-1",
      employeeId: "emp-1",
      employeeName: "Jordan Kim",
      leaveType: "VACATION",
      startDate: "2026-05-12",
      endDate: "2026-05-13",
      hours: 16,
      status: "PENDING",
      conflict: true,
      conflictSummary: "Conflicts with 2 scheduled shift(s): 2026-05-12, 2026-05-13"
    }
  ];
}
