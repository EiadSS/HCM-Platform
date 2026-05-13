create table analytics_events (
    id uuid primary key,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    deleted boolean not null default false,
    event_type varchar(80) not null,
    visitor_hash varchar(128),
    tenant_id uuid,
    account_email varchar(255),
    account_role varchar(80),
    path varchar(500),
    referrer varchar(1000),
    metadata_json text,
    occurred_at timestamp with time zone not null,
    constraint fk_analytics_event_tenant foreign key (tenant_id) references tenants(id)
);

create index ix_analytics_event_occurred on analytics_events (occurred_at desc) where deleted = false;
create index ix_analytics_event_type_occurred on analytics_events (event_type, occurred_at desc) where deleted = false;
create index ix_analytics_event_visitor on analytics_events (visitor_hash, occurred_at desc) where deleted = false;
