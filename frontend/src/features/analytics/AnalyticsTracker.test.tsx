import { render, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "../../api/client";
import { AnalyticsTracker } from "./AnalyticsTracker";

vi.mock("../../api/client", () => ({
  api: {
    recordAnalyticsEvent: vi.fn(() => Promise.resolve())
  }
}));

const mockedApi = vi.mocked(api);

describe("AnalyticsTracker", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("records page views for normal public routes", async () => {
    render(
      <MemoryRouter initialEntries={["/login"]}>
        <AnalyticsTracker />
      </MemoryRouter>
    );

    await waitFor(() =>
      expect(mockedApi.recordAnalyticsEvent).toHaveBeenCalledWith(
        expect.objectContaining({
          eventType: "PAGE_VIEW",
          path: "/login"
        })
      )
    );
  });

  it("does not record visits to the private analytics route", async () => {
    render(
      <MemoryRouter initialEntries={["/analytics"]}>
        <AnalyticsTracker />
      </MemoryRouter>
    );

    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(mockedApi.recordAnalyticsEvent).not.toHaveBeenCalled();
  });
});
