import type { UserRole } from "../../types/api";

export interface DemoAccount {
  label: string;
  role: UserRole;
  email: string;
  password: string;
  focus: string;
}

export const demoAccounts: DemoAccount[] = [
  {
    label: "HR Admin",
    role: "HR_ADMIN",
    email: "hr@demo.hcm.local",
    password: "DemoPass123!",
    focus: "Employees, imports, leave, audit"
  },
  {
    label: "Manager",
    role: "MANAGER",
    email: "manager@demo.hcm.local",
    password: "DemoPass123!",
    focus: "Schedules, conflicts, approvals"
  },
  {
    label: "Employee",
    role: "EMPLOYEE",
    email: "employee@demo.hcm.local",
    password: "DemoPass123!",
    focus: "My shifts, time entries, leave"
  },
  {
    label: "Payroll Admin",
    role: "PAYROLL_ADMIN",
    email: "payroll@demo.hcm.local",
    password: "DemoPass123!",
    focus: "Timesheets, payroll previews"
  },
  {
    label: "System Admin",
    role: "SYSTEM_ADMIN",
    email: "admin@demo.hcm.local",
    password: "DemoPass123!",
    focus: "Tenant health, audit, reset"
  }
];
