alter table leave_requests
    add column requested_by_user_id uuid,
    add column submitted_at timestamp with time zone,
    add column decided_by_user_id uuid,
    add column decided_at timestamp with time zone,
    add column decision_note text,
    add column employee_note text,
    add column conflict_count integer not null default 0,
    add column conflict_summary text;

create table leave_balances (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    employee_id uuid not null,
    employee_name varchar(255) not null,
    leave_type varchar(60) not null,
    accrued_hours numeric(8, 2) not null,
    used_hours numeric(8, 2) not null,
    pending_hours numeric(8, 2) not null,
    max_hours numeric(8, 2) not null,
    constraint fk_leave_balance_tenant foreign key (tenant_id) references tenants(id)
);

create unique index ux_leave_balance_employee_type_active on leave_balances (tenant_id, employee_id, leave_type) where deleted = false;

create table leave_accrual_rules (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    employment_type varchar(60) not null,
    leave_type varchar(60) not null,
    monthly_accrual_hours numeric(6, 2) not null,
    max_balance_hours numeric(8, 2) not null,
    active boolean not null default true,
    constraint fk_leave_accrual_rule_tenant foreign key (tenant_id) references tenants(id)
);

create index ix_leave_accrual_rule_tenant on leave_accrual_rules (tenant_id, employment_type, leave_type) where deleted = false;

create table leave_balance_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    leave_balance_id uuid not null,
    employee_id uuid not null,
    employee_name varchar(255) not null,
    leave_request_id uuid,
    leave_type varchar(60) not null,
    event_type varchar(80) not null,
    event_date date not null,
    accrual_period date,
    hours numeric(8, 2) not null,
    balance_after_hours numeric(8, 2) not null,
    note text,
    constraint fk_leave_balance_event_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_leave_balance_event_balance foreign key (leave_balance_id) references leave_balances(id)
);

create index ix_leave_balance_event_employee on leave_balance_events (tenant_id, employee_id, event_date desc) where deleted = false;
create unique index ux_leave_monthly_accrual_event on leave_balance_events (tenant_id, employee_id, leave_type, event_type, accrual_period) where deleted = false and accrual_period is not null;
