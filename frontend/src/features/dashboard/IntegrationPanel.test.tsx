import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import type { ImportJob, ImportJobDetail, MeResponse, WebhookEvent, WebhookEventDetail } from "../../types/api";
import { IntegrationPanel } from "./IntegrationPanel";

vi.mock("../../api/client", () => ({
  api: {
    imports: vi.fn(),
    employeeImportDetail: vi.fn(),
    previewEmployeeImport: vi.fn(),
    commitEmployeeImport: vi.fn(),
    downloadImportErrors: vi.fn(),
    exportTimesheetsCsv: vi.fn(),
    exportTimesheetsJson: vi.fn(),
    webhookEvents: vi.fn(),
    webhookEventDetail: vi.fn(),
    redeliverWebhookEvent: vi.fn()
  }
}));

let mockUser: MeResponse;

vi.mock("../auth/AuthContext", () => ({
  useAuth: () => ({ user: mockUser })
}));

const mockedApi = vi.mocked(api);

describe("IntegrationPanel", () => {
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
    mockedApi.imports.mockResolvedValue(imports());
    mockedApi.employeeImportDetail.mockResolvedValue(importDetail());
    mockedApi.previewEmployeeImport.mockResolvedValue(importDetail("preview-2"));
    mockedApi.commitEmployeeImport.mockResolvedValue({ ...importDetail(), job: { ...importDetail().job, status: "COMPLETED_WITH_ERRORS", committedRows: 1 } });
    mockedApi.downloadImportErrors.mockResolvedValue(undefined);
    mockedApi.exportTimesheetsCsv.mockResolvedValue(undefined);
    mockedApi.exportTimesheetsJson.mockResolvedValue(undefined);
    mockedApi.webhookEvents.mockResolvedValue(webhooks());
    mockedApi.webhookEventDetail.mockResolvedValue(webhookDetail());
    mockedApi.redeliverWebhookEvent.mockResolvedValue({ ...webhookDetail(), event: { ...webhookDetail().event, status: "DELIVERED" } });
  });

  afterEach(() => cleanup());

  it("renders seeded import history and webhook history", async () => {
    renderPanel();

    expect(await screen.findByText("Employee CSV import")).toBeInTheDocument();
    expect(await screen.findByText(/northstar-new-hires-may.csv/i)).toBeInTheDocument();
    expect(await screen.findByText(/payroll.preview.generated/i)).toBeInTheDocument();
    expect(await screen.findByText(/Invalid department/i)).toBeInTheDocument();
  });

  it("lets HR preview mapped CSV and commit valid rows", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Preview import" }));
    await waitFor(() =>
      expect(mockedApi.previewEmployeeImport).toHaveBeenCalledWith(
        expect.objectContaining({
          fileName: "northstar-phase6-preview.csv",
          fieldMapping: expect.objectContaining({ employeeNumber: "Worker ID" })
        })
      )
    );

    await user.click(await screen.findByRole("button", { name: "Commit valid rows" }));
    await waitFor(() => expect(mockedApi.commitEmployeeImport).toHaveBeenCalled());
  });

  it("downloads import error reports and approved timesheet exports", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: "Download error report" }));
    await user.click(screen.getByRole("button", { name: "Export CSV" }));
    await user.click(screen.getByRole("button", { name: "Export JSON" }));

    await waitFor(() => expect(mockedApi.downloadImportErrors).toHaveBeenCalled());
    expect(mockedApi.downloadImportErrors.mock.calls[0][0]).toBe("import-1");
    expect(mockedApi.exportTimesheetsCsv).toHaveBeenCalled();
    expect(mockedApi.exportTimesheetsJson).toHaveBeenCalled();
  });

  it("shows failed webhook attempts and supports redelivery", async () => {
    const user = userEvent.setup();
    renderPanel();

    await user.click(await screen.findByRole("button", { name: /payroll.preview.generated/i }));
    expect(await screen.findByText(/Unavailable/i)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Redeliver" }));

    await waitFor(() => expect(mockedApi.redeliverWebhookEvent).toHaveBeenCalled());
    expect(mockedApi.redeliverWebhookEvent.mock.calls[0][0]).toBe("webhook-1");
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
      <IntegrationPanel />
    </QueryClientProvider>
  );
}

function imports(): ImportJob[] {
  return [
    {
      id: "import-1",
      fileName: "northstar-new-hires-may.csv",
      status: "PREVIEW_READY",
      totalRows: 2,
      successRows: 1,
      errorRows: 1,
      committedRows: 0,
      summary: "1 row ready to import. 1 row needs correction."
    }
  ];
}

function importDetail(id = "import-1"): ImportJobDetail {
  return {
    job: { ...imports()[0], id },
    detectedHeaders: ["Employee ID", "Department"],
    fieldMapping: { employeeNumber: "Employee ID", department: "Department" },
    rows: [
      {
        id: "row-1",
        rowNumber: 2,
        status: "VALID",
        rawValues: { "Employee ID": "NS-030" },
        mappedValues: { employeeNumber: "NS-030", firstName: "Ava", lastName: "Lopez", department: "Retail", hourlyRate: "23.00" },
        errors: []
      },
      {
        id: "row-2",
        rowNumber: 3,
        status: "ERROR",
        rawValues: { "Employee ID": "NS-004" },
        mappedValues: { employeeNumber: "NS-004", firstName: "Existing", lastName: "Conflict", department: "Merch", hourlyRate: "-1" },
        errors: [{ rowNumber: 3, field: "department", message: "Invalid department" }]
      }
    ]
  };
}

function webhooks(): WebhookEvent[] {
  return [
    {
      id: "webhook-1",
      eventType: "payroll.preview.generated",
      entityType: "PayrollPreview",
      entityId: "preview-1",
      status: "FAILED",
      generatedAt: "2026-05-04T14:00:00Z",
      latestAttempt: {
        id: "attempt-1",
        destinationName: "Northstar Demo Receiver",
        destinationUrl: "https://example.test",
        status: "FAILED",
        responseCode: 503,
        responseBody: "Unavailable",
        attemptedAt: "2026-05-04T14:01:00Z"
      }
    }
  ];
}

function webhookDetail(): WebhookEventDetail {
  return {
    event: webhooks()[0],
    payloadJson: "{\"grossPay\":1740.42}",
    attempts: [webhooks()[0].latestAttempt!]
  };
}
