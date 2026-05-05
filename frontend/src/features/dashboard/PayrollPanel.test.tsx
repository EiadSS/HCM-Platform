import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import type { MeResponse, OrganizationResponse, PayrollPreview, PayrollPreviewDetail } from "../../types/api";
import { PayrollPanel } from "./PayrollPanel";

vi.mock("../../api/client", () => ({
  api: {
    payrollPreviews: vi.fn(),
    payrollPreviewDetail: vi.fn(),
    generatePayrollPreview: vi.fn(),
    organization: vi.fn()
  }
}));

let mockUser: MeResponse;

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: mockUser })
}));

const mockedApi = vi.mocked(api);

describe("PayrollPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser = {
      userId: "payroll-1",
      tenantId: "tenant-1",
      email: "payroll@demo.hcm.local",
      displayName: "Sam Carter",
      roles: ["PAYROLL_ADMIN"],
      demoMode: true
    };
    mockedApi.payrollPreviews.mockResolvedValue([preview()]);
    mockedApi.payrollPreviewDetail.mockResolvedValue(detail());
    mockedApi.generatePayrollPreview.mockResolvedValue(detail());
    mockedApi.organization.mockResolvedValue(organization());
  });

  it("renders seeded preview summary and employee explanations", async () => {
    renderPanel();

    expect(await screen.findByText("Payroll preview report")).toBeInTheDocument();
    expect(await screen.findByText("Amara Singh")).toBeInTheDocument();
    expect(screen.getByText(/holiday worked hours added/i)).toBeInTheDocument();
    expect(screen.getAllByText(/1,740\.42/).length).toBeGreaterThan(0);
  });

  it("lets payroll users generate a preview request", async () => {
    const user = userEvent.setup();
    renderPanel();

    const buttons = await screen.findAllByRole("button", { name: "Generate preview" });
    await user.click(buttons[0]);

    await waitFor(() =>
      expect(mockedApi.generatePayrollPreview).toHaveBeenCalledWith(
        expect.objectContaining({
          periodStart: expect.any(String),
          periodEnd: expect.any(String)
        })
      )
    );
  });

  it("shows blocking validation errors from preview generation", async () => {
    mockedApi.generatePayrollPreview.mockRejectedValue(new Error("Payroll preview blocked: Missed punch for Jordan Kim"));
    const user = userEvent.setup();
    renderPanel();

    const buttons = await screen.findAllByRole("button", { name: "Generate preview" });
    await user.click(buttons[0]);

    expect(await screen.findByText(/Missed punch for Jordan Kim/i)).toBeInTheDocument();
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
      <PayrollPanel />
    </QueryClientProvider>
  );
}

function preview(): PayrollPreview {
  return {
    id: "preview-1",
    periodStart: "2026-04-27",
    periodEnd: "2026-05-03",
    regularHours: 60.88,
    overtimeHours: 4,
    unpaidBreakHours: 2,
    unpaidLeaveHours: 4,
    holidayHours: 14.5,
    holidayPremiumPay: 166.51,
    grossPay: 1740.42,
    employeeCount: 3,
    timesheetCount: 3,
    status: "READY_FOR_REVIEW",
    explanation: "Seeded payroll preview from submitted and approved timesheets."
  };
}

function detail(): PayrollPreviewDetail {
  return {
    preview: preview(),
    lines: [
      {
        id: "line-1",
        employeeId: "emp-1",
        employeeName: "Amara Singh",
        locationId: "loc-store",
        locationName: "Downtown Store",
        timesheetCount: 1,
        hourlyRate: 23.25,
        regularHours: 40,
        overtimeHours: 4,
        holidayHours: 9,
        unpaidBreakHours: 1,
        unpaidLeaveHours: 0,
        regularPay: 930,
        overtimePay: 139.5,
        holidayPremiumPay: 104.63,
        grossPay: 1174.13,
        ruleName: "Downtown Store holiday premium rule",
        explanation: "Amara Singh: 9 holiday worked hours added $104.63 premium."
      }
    ]
  };
}

function organization(): OrganizationResponse {
  return {
    departments: [],
    locations: [{ id: "loc-store", name: "Downtown Store", timezone: "America/Toronto", region: "Ontario" }],
    jobTitles: []
  };
}
