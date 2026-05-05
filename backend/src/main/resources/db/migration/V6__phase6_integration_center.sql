alter table import_jobs
    add column field_mapping_json text,
    add column queued_at timestamp with time zone,
    add column started_at timestamp with time zone,
    add column previewed_at timestamp with time zone,
    add column committed_at timestamp with time zone,
    add column failed_at timestamp with time zone,
    add column committed_rows integer not null default 0,
    add column source_metadata text;

create table employee_import_rows (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    import_job_id uuid not null,
    row_number integer not null,
    raw_json text not null,
    mapped_json text not null,
    status varchar(60) not null,
    error_json text,
    imported_employee_id uuid,
    constraint fk_employee_import_row_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_employee_import_row_job foreign key (import_job_id) references import_jobs(id)
);

create index ix_employee_import_row_job on employee_import_rows (tenant_id, import_job_id, row_number) where deleted = false;

create table webhook_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    event_type varchar(120) not null,
    entity_type varchar(120) not null,
    entity_id uuid,
    payload_json text not null,
    status varchar(60) not null,
    generated_at timestamp with time zone not null,
    constraint fk_webhook_event_tenant foreign key (tenant_id) references tenants(id)
);

create index ix_webhook_event_tenant_generated on webhook_events (tenant_id, generated_at desc) where deleted = false;

create table webhook_delivery_attempts (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    webhook_event_id uuid not null,
    destination_name varchar(255) not null,
    destination_url varchar(500) not null,
    status varchar(60) not null,
    response_code integer,
    response_body text,
    attempted_at timestamp with time zone not null,
    constraint fk_webhook_delivery_tenant foreign key (tenant_id) references tenants(id),
    constraint fk_webhook_delivery_event foreign key (webhook_event_id) references webhook_events(id)
);

create index ix_webhook_delivery_event on webhook_delivery_attempts (tenant_id, webhook_event_id, attempted_at desc) where deleted = false;
