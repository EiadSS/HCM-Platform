create table tenants (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    slug varchar(120) not null unique,
    name varchar(255) not null,
    status varchar(40) not null,
    demo_mode boolean not null default false
);

create table user_accounts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    email varchar(255) not null,
    display_name varchar(255) not null,
    password_hash varchar(255) not null,
    status varchar(40) not null,
    protected_demo_account boolean not null default false,
    constraint fk_user_tenant foreign key (tenant_id) references tenants(id)
);

create unique index ux_user_tenant_email_active on user_accounts (tenant_id, lower(email)) where deleted = false;

create table user_roles (
    user_id uuid not null,
    role varchar(60) not null,
    primary key (user_id, role),
    constraint fk_user_roles_user foreign key (user_id) references user_accounts(id) on delete cascade
);

create table departments (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    name varchar(160) not null,
    cost_center varchar(80) not null,
    constraint fk_department_tenant foreign key (tenant_id) references tenants(id)
);

create unique index ux_department_tenant_name_active on departments (tenant_id, lower(name)) where deleted = false;

create table locations (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    name varchar(160) not null,
    timezone varchar(80) not null,
    region varchar(120) not null,
    constraint fk_location_tenant foreign key (tenant_id) references tenants(id)
);

create unique index ux_location_tenant_name_active on locations (tenant_id, lower(name)) where deleted = false;

create table job_titles (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    name varchar(160) not null,
    career_level varchar(80) not null,
    constraint fk_job_title_tenant foreign key (tenant_id) references tenants(id)
);

create unique index ux_job_title_tenant_name_active on job_titles (tenant_id, lower(name)) where deleted = false;

create table employees (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    employee_number varchar(80) not null,
    first_name varchar(120) not null,
    last_name varchar(120) not null,
    work_email varchar(255) not null,
    status varchar(40) not null,
    employment_type varchar(40) not null,
    department_id uuid,
    location_id uuid,
    job_title_id uuid,
    manager_employee_id uuid,
    user_account_id uuid,
    hourly_rate numeric(12, 2) not null,
    weekly_hour_cap numeric(5, 2) not null,
    hire_date date not null,
    termination_date date,
    constraint fk_employee_tenant foreign key (tenant_id) references tenants(id)
);

create unique index ux_employee_tenant_number_active on employees (tenant_id, lower(employee_number)) where deleted = false;
create unique index ux_employee_tenant_email_active on employees (tenant_id, lower(work_email)) where deleted = false;

create table audit_logs (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    actor_user_id uuid,
    actor_email varchar(255) not null,
    action_type varchar(120) not null,
    entity_type varchar(120) not null,
    entity_id uuid,
    previous_value text,
    new_value text,
    metadata text,
    constraint fk_audit_tenant foreign key (tenant_id) references tenants(id)
);

create index ix_audit_tenant_created on audit_logs (tenant_id, created_at desc);

create table schedule_alerts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    employee_id uuid,
    employee_name varchar(255) not null,
    week_start_date date not null,
    alert_type varchar(80) not null,
    severity varchar(40) not null,
    message text not null,
    status varchar(40) not null,
    constraint fk_schedule_alert_tenant foreign key (tenant_id) references tenants(id)
);

create table shifts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    employee_id uuid,
    department_id uuid,
    location_id uuid,
    employee_name varchar(255) not null,
    shift_date date not null,
    start_time time not null,
    end_time time not null,
    status varchar(40) not null,
    published boolean not null default false,
    constraint fk_shift_tenant foreign key (tenant_id) references tenants(id)
);

create table timesheets (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    employee_id uuid not null,
    employee_name varchar(255) not null,
    week_start_date date not null,
    regular_hours numeric(6, 2) not null,
    overtime_hours numeric(6, 2) not null,
    status varchar(60) not null,
    submitted_at timestamp with time zone,
    approved_at timestamp with time zone,
    approver_user_id uuid,
    manager_note text,
    locked_pay_period boolean not null default false,
    constraint fk_timesheet_tenant foreign key (tenant_id) references tenants(id)
);

create table payroll_previews (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    period_start date not null,
    period_end date not null,
    regular_hours numeric(8, 2) not null,
    overtime_hours numeric(8, 2) not null,
    gross_pay numeric(12, 2) not null,
    status varchar(60) not null,
    explanation text not null,
    generated_by_user_id uuid,
    constraint fk_payroll_preview_tenant foreign key (tenant_id) references tenants(id)
);

create table leave_requests (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    employee_id uuid not null,
    employee_name varchar(255) not null,
    leave_type varchar(60) not null,
    start_date date not null,
    end_date date not null,
    hours numeric(6, 2) not null,
    status varchar(60) not null,
    manager_note text,
    constraint fk_leave_request_tenant foreign key (tenant_id) references tenants(id)
);

create table import_jobs (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    file_name varchar(255) not null,
    status varchar(60) not null,
    total_rows integer not null,
    success_rows integer not null,
    error_rows integer not null,
    summary text not null,
    error_report_csv text,
    completed_at timestamp with time zone,
    constraint fk_import_job_tenant foreign key (tenant_id) references tenants(id)
);
