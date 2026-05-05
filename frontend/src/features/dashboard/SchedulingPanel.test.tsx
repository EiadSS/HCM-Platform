import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import type { WeeklySchedule } from "../../types/api";
import { SchedulingPanel } from "./SchedulingPanel";

vi.mock("../../api/client", () => ({
  api: {
    scheduleWeek: vi.fn(),
    employees: vi.fn(),
    organization: vi.fn(),
    createShift: vi.fn(),
    updateShift: vi.fn(),
    deleteShift: vi.fn(),
    validateScheduleWeek: vi.fn(),
    publishScheduleWeek: vi.fn()
  }
}));

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({
    user: {
      userId: "user-1",
      tenantId: "tenant-1",
      email: "manager@demo.hcm.local",
      displayName: "Maya Thompson",
      roles: ["MANAGER"],
      demoMode: true
    }
  })
}));

const mockedApi = vi.mocked(api);

describe("SchedulingPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    const schedule = sampleSchedule();
    mockedApi.scheduleWeek.mockResolvedValue(schedule);
    mockedApi.employees.mockResolvedValue([
      {
        id: "emp-1",
        employeeNumber: "NS-004",
        firstName: "Jordan",
        lastName: "Kim",
        fullName: "Jordan Kim",
        workEmail: "employee@demo.hcm.local",
        status: "ACTIVE",
        employmentType: "PART_TIME",
        departmentName: "Retail",
        locationName: "Downtown Store",
        hourlyRate: 24.75,
        weeklyHourCap: 28,
        hireDate: "2022-06-07"
      }
    ]);
    mockedApi.organization.mockResolvedValue({
      departments: [{ id: "dept-1", name: "Retail", costCenter: "RET-200" }],
      locations: [{ id: "loc-1", name: "Downtown Store", timezone: "America/Toronto", region: "Ontario" }],
      jobTitles: []
    });
    mockedApi.createShift.mockResolvedValue(schedule);
    mockedApi.publishScheduleWeek.mockResolvedValue(schedule);
  });

  it("renders seeded weekly schedule data", async () => {
    renderPanel();

    expect(await screen.findAllByText("Jordan Kim")).not.toHaveLength(0);
    expect(await screen.findAllByText("Open Shift")).not.toHaveLength(0);
    expect(screen.getByText("OVERLAP")).toBeInTheDocument();
  });

  it("lets managers create an open shift", async () => {
    const user = userEvent.setup();
    renderPanel();

    await waitFor(() => expect(screen.getByRole("button", { name: "Add shift" })).toBeEnabled());
    await user.click(screen.getByRole("button", { name: "Add shift" }));
    await user.click(await screen.findByRole("button", { name: "Save shift" }));

    await waitFor(() => expect(mockedApi.createShift).toHaveBeenCalled());
    expect(mockedApi.createShift.mock.calls[0][1]).toMatchObject({
      employeeId: null,
      departmentId: "dept-1",
      locationId: "loc-1"
    });
  });

  it("shows publish validation errors from the API", async () => {
    mockedApi.publishScheduleWeek.mockRejectedValueOnce(new Error("Resolve high-severity schedule issues before publishing"));
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Publish" }));

    expect(await screen.findByText("Resolve high-severity schedule issues before publishing")).toBeInTheDocument();
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
      <SchedulingPanel />
    </QueryClientProvider>
  );
}

function sampleSchedule(): WeeklySchedule {
  const weekStart = currentWeekStart();
  return {
    week: {
      id: "week-1",
      weekStartDate: weekStart,
      status: "DRAFT",
      shiftCount: 2,
      openShiftCount: 1,
      violationCount: 1,
      highSeverityCount: 1
    },
    shifts: [
      {
        id: "shift-1",
        employeeId: "emp-1",
        employeeName: "Jordan Kim",
        departmentId: "dept-1",
        departmentName: "Retail",
        locationId: "loc-1",
        locationName: "Downtown Store",
        shiftDate: weekStart,
        startTime: "09:00:00",
        endTime: "17:00:00",
        status: "ASSIGNED",
        published: false
      },
      {
        id: "shift-2",
        employeeId: null,
        employeeName: "Open Shift",
        departmentId: "dept-1",
        departmentName: "Retail",
        locationId: "loc-1",
        locationName: "Downtown Store",
        shiftDate: addDays(weekStart, 4),
        startTime: "16:00:00",
        endTime: "22:00:00",
        status: "OPEN",
        published: false
      }
    ],
    alerts: [],
    validation: {
      valid: false,
      highSeverityCount: 1,
      violations: [
        {
          type: "OVERLAP",
          severity: "HIGH",
          employeeName: "Jordan Kim",
          message: "Jordan Kim has overlapping shifts on Monday."
        }
      ]
    }
  };
}

function currentWeekStart() {
  const date = new Date();
  const copy = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const day = copy.getDay();
  const diff = day === 0 ? -6 : 1 - day;
  copy.setDate(copy.getDate() + diff);
  return formatDate(copy);
}

function addDays(dateValue: string, days: number) {
  const [year, month, day] = dateValue.split("-").map(Number);
  const date = new Date(year, month - 1, day);
  date.setDate(date.getDate() + days);
  return formatDate(date);
}

function formatDate(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}
