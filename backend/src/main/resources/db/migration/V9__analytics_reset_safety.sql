alter table analytics_events
    drop constraint if exists fk_analytics_event_tenant;

alter table analytics_events
    add constraint fk_analytics_event_tenant
        foreign key (tenant_id) references tenants(id)
        on delete set null;
