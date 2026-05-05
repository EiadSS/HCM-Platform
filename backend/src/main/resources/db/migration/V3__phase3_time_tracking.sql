create table time_entries (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    timesheet_id uuid not null,
    employee_id uuid not null,
    employee_name varchar(255) not null,
    shift_id uuid,
    entry_date date not null,
    clock_in_at timestamp with time zone not null,
    clock_out_at timestamp with time zone,
    source varchar(40) not null,
    status varchar(60) not null,
    note text,
    constraint fk_time_entry_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_time_entry_timesheet foreign key (timesheet_id) references timesheets(id) on delete cascade
);

create index ix_time_entry_tenant_employee_date on time_entries (tenant_id, employee_id, entry_date) where deleted = false;
create index ix_time_entry_timesheet on time_entries (timesheet_id) where deleted = false;

create table time_breaks (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    time_entry_id uuid not null,
    break_start_at timestamp with time zone not null,
    break_end_at timestamp with time zone,
    duration_minutes integer,
    source varchar(40) not null,
    note text,
    constraint fk_time_break_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_time_break_entry foreign key (time_entry_id) references time_entries(id) on delete cascade
);

create index ix_time_break_entry on time_breaks (time_entry_id) where deleted = false;

create table timesheet_change_requests (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    timesheet_id uuid not null,
    requested_by_user_id uuid not null,
    requester_email varchar(255) not null,
    reason text not null,
    status varchar(40) not null,
    decision_note text,
    decided_by_user_id uuid,
    decided_at timestamp with time zone,
    constraint fk_timesheet_change_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_timesheet_change_timesheet foreign key (timesheet_id) references timesheets(id) on delete cascade
);

create index ix_timesheet_change_tenant_status on timesheet_change_requests (tenant_id, status) where deleted = false;
create index ix_timesheet_change_timesheet on timesheet_change_requests (timesheet_id) where deleted = false;
