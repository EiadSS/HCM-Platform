import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";
import { AuthProvider } from "./AuthContext";
import { LoginPage } from "./LoginPage";

vi.mock("../../api/client", () => ({
  getToken: () => null,
  setToken: vi.fn(),
  clearToken: vi.fn(),
  api: {
    wakeBackend: vi.fn(() => Promise.resolve(true)),
    login: vi.fn(),
    me: vi.fn()
  }
}));

describe("LoginPage", () => {
  it("quick-fills seeded role credentials", async () => {
    render(
      <MemoryRouter>
        <AuthProvider>
          <LoginPage />
        </AuthProvider>
      </MemoryRouter>
    );

    await userEvent.click(screen.getByRole("button", { name: /System Admin/i }));

    expect(screen.getByDisplayValue("admin@demo.hcm.local")).toBeInTheDocument();
    expect(screen.getByDisplayValue("DemoPass123!")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /analytics/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /analytics/i })).not.toBeInTheDocument();
  });
});
