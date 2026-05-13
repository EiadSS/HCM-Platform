import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { api } from "../../api/client";
import { AnalyticsPage } from "./AnalyticsPage";

vi.mock("../../api/client", () => ({
  api: {
    analyticsSummary: vi.fn(),
    analyticsEvents: vi.fn()
  }
}));

const mockedApi = vi.mocked(api);

describe("AnalyticsPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    mockedApi.analyticsSummary.mockResolvedValue({
      totalEvents: 8,
      totalVisits: 5,
      uniqueVisitors: 2,
      activeVisitors: 1,
      lastUsedAt: "2026-05-12T15:00:00Z",
      totalLogins: 3,
      topPages: [{ label: "/login", value: 4 }],
      loginRoles: [{ label: "SYSTEM_ADMIN", value: 2 }]
    });
    mockedApi.analyticsEvents.mockResolvedValue([
      {
        id: "event-1",
        occurredAt: "2026-05-12T15:00:00Z",
        eventType: "LOGIN_SUCCESS",
        path: "/login",
        accountEmail: "admin@demo.hcm.local",
        accountRole: "SYSTEM_ADMIN"
      }
    ]);
  });

  afterEach(() => cleanup());

  it("renders the owner code gate first", () => {
    render(<AnalyticsPage />);

    expect(screen.getByText("Portfolio Analytics")).toBeInTheDocument();
    expect(screen.getByLabelText("Owner analytics code")).toBeInTheDocument();
    expect(screen.queryByText("Total visits")).not.toBeInTheDocument();
  });

  it("shows analytics after a valid owner code", async () => {
    const user = userEvent.setup();
    render(<AnalyticsPage />);

    await user.type(screen.getByLabelText("Owner analytics code"), "owner-code");
    await user.click(screen.getByRole("button", { name: /Unlock analytics/i }));

    expect(await screen.findByText("Total visits")).toBeInTheDocument();
    expect(screen.getByText("Unique visitors")).toBeInTheDocument();
    expect(screen.getAllByText("/login").length).toBeGreaterThan(0);
    expect(screen.getByText("admin@demo.hcm.local")).toBeInTheDocument();
    expect(sessionStorage.getItem("hcm_demo_analytics_owner_code")).toBe("owner-code");
  });

  it("shows invalid-code errors clearly", async () => {
    mockedApi.analyticsSummary.mockRejectedValue(new Error("Invalid analytics owner code"));
    const user = userEvent.setup();
    render(<AnalyticsPage />);

    await user.type(screen.getByLabelText("Owner analytics code"), "wrong");
    await user.click(screen.getByRole("button", { name: /Unlock analytics/i }));

    expect(await screen.findByText("Invalid analytics owner code")).toBeInTheDocument();
    expect(sessionStorage.getItem("hcm_demo_analytics_owner_code")).toBeNull();
  });

  it("passes date filters when refreshing", async () => {
    const user = userEvent.setup();
    sessionStorage.setItem("hcm_demo_analytics_owner_code", "owner-code");
    render(<AnalyticsPage />);

    await screen.findByText("Total visits");
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-05-01" } });
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-05-12" } });
    await user.click(screen.getByRole("button", { name: /Refresh/i }));

    await waitFor(() =>
      expect(
        mockedApi.analyticsSummary.mock.calls.some(
          ([ownerCode, filters]) => ownerCode === "owner-code" && Boolean(filters?.from?.startsWith("2026-05-01")) && Boolean(filters?.to?.startsWith("2026-05-12"))
        )
      ).toBe(true)
    );
  });
});
