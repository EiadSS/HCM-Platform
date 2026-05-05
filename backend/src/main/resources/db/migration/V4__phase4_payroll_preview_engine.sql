create table pay_rule_configs (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    location_id uuid,
    name varchar(160) not null,
    effective_start_date date not null,
    effective_end_date date,
    weekly_regular_hours numeric(6, 2) not null,
    overtime_multiplier numeric(5, 2) not null,
    holiday_premium_multiplier numeric(5, 2) not null,
    unpaid_breaks_deductible boolean not null default true,
    constraint fk_pay_rule_config_tenant foreign key (tenant_id) references tenants(id)
);

create index ix_pay_rule_config_tenant_location on pay_rule_configs (tenant_id, location_id, effective_start_date desc) where deleted = false;

create table payroll_holidays (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    location_id uuid,
    holiday_date date not null,
    name varchar(160) not null,
    constraint fk_payroll_holiday_tenant foreign key (tenant_id) references tenants(id)
);

create index ix_payroll_holiday_tenant_date on payroll_holidays (tenant_id, holiday_date) where deleted = false;

alter table payroll_previews
    add column location_id uuid,
    add column employee_count integer not null default 0,
    add column timesheet_count integer not null default 0,
    add column unpaid_break_hours numeric(8, 2) not null default 0,
    add column unpaid_leave_hours numeric(8, 2) not null default 0,
    add column holiday_hours numeric(8, 2) not null default 0,
    add column holiday_premium_pay numeric(12, 2) not null default 0,
    add column metadata text;

create table payroll_preview_lines (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    payroll_preview_id uuid not null,
    employee_id uuid not null,
    employee_name varchar(255) not null,
    location_id uuid,
    location_name varchar(255),
    timesheet_count integer not null,
    hourly_rate numeric(12, 2) not null,
    regular_hours numeric(8, 2) not null,
    overtime_hours numeric(8, 2) not null,
    holiday_hours numeric(8, 2) not null,
    unpaid_break_hours numeric(8, 2) not null,
    unpaid_leave_hours numeric(8, 2) not null,
    regular_pay numeric(12, 2) not null,
    overtime_pay numeric(12, 2) not null,
    holiday_premium_pay numeric(12, 2) not null,
    gross_pay numeric(12, 2) not null,
    rule_name varchar(160) not null,
    explanation text not null,
    constraint fk_payroll_preview_line_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_payroll_preview_line_preview foreign key (payroll_preview_id) references payroll_previews(id)
);

create index ix_payroll_preview_line_preview on payroll_preview_lines (tenant_id, payroll_preview_id, employee_name) where deleted = false;
