create table schedule_weeks (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    tenant_id uuid not null,
    week_start_date date not null,
    status varchar(40) not null,
    published_at timestamp with time zone,
    published_by_user_id uuid,
    constraint fk_schedule_week_tenant foreign key (tenant_id) references tenants(id)
);

create unique index ux_schedule_week_tenant_start_active on schedule_weeks (tenant_id, week_start_date) where deleted = false;
create index ix_shift_tenant_date_active on shifts (tenant_id, shift_date) where deleted = false;
