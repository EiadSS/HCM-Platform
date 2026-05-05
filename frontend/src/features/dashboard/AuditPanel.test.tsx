import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import type { AuditLog, MeResponse } from "../../types/api";
import { AuditPanel } from "./AuditPanel";

vi.mock("../../api/client", () => ({
  api: {
    auditLogs: vi.fn()
  }
}));

let mockUser: MeResponse;

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: mockUser })
}));

const mockedApi = vi.mocked(api);

describe("AuditPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUser = {
      userId: "admin-1",
      tenantId: "tenant-1",
      email: "admin@demo.hcm.local",
      displayName: "Alex Rivera",
      roles: ["SYSTEM_ADMIN"],
      demoMode: true
    };
    mockedApi.auditLogs.mockResolvedValue(auditRows());
  });

  afterEach(() => cleanup());

  it("renders audit rows and metadata details", async () => {
    const user = userEvent.setup();
    renderPanel();

    expect(await screen.findByText("Audit log browser")).toBeInTheDocument();
    expect(await screen.findByText("employee.updated")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Inspect" }));

    expect(await screen.findByText("Previous value")).toBeInTheDocument();
    expect(screen.getByText(/employee-api/i)).toBeInTheDocument();
  });

  it("applies audit filters to the API request", async () => {
    const user = userEvent.setup();
    renderPanel();

    await screen.findByText("employee.updated");
    await user.type(screen.getByLabelText("Actor email"), "hr@demo.hcm.local");
    await user.type(screen.getByLabelText("Action type"), "employee.updated");
    await user.clear(screen.getByLabelText("Limit"));
    await user.type(screen.getByLabelText("Limit"), "75");
    await user.click(screen.getByRole("button", { name: "Apply filters" }));

    await waitFor(() =>
      expect(
        mockedApi.auditLogs.mock.calls.some(([filters]) =>
          filters !== undefined &&
          filters.actorEmail === "hr@demo.hcm.local" &&
          filters.actionType === "employee.updated" &&
          filters.limit === 75
        )
      ).toBe(true)
    );
  });

  it("shows an empty state when filters match no rows", async () => {
    mockedApi.auditLogs.mockResolvedValue([]);
    renderPanel();

    expect(await screen.findByText("No audit records match these filters.")).toBeInTheDocument();
  });

  it("shows API errors clearly", async () => {
    mockedApi.auditLogs.mockRejectedValue(new Error("Unable to load audit logs"));
    renderPanel();

    expect(await screen.findByText(/Unable to load audit logs/i)).toBeInTheDocument();
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
      <AuditPanel />
    </QueryClientProvider>
  );
}

function auditRows(): AuditLog[] {
  return [
    {
      id: "audit-1",
      timestamp: "2026-05-04T14:00:00Z",
      actorEmail: "hr@demo.hcm.local",
      actionType: "employee.updated",
      entityType: "Employee",
      entityId: "emp-1",
      previousValue: "{\"status\":\"ACTIVE\"}",
      newValue: "{\"status\":\"ON_LEAVE\"}",
      metadata: "{\"source\":\"employee-api\"}"
    }
  ];
}
