create index if not exists idx_audit_logs_tenant_created
    on audit_logs (tenant_id, created_at desc)
    where deleted = false;

create index if not exists idx_audit_logs_tenant_action
    on audit_logs (tenant_id, action_type, created_at desc)
    where deleted = false;

create index if not exists idx_audit_logs_tenant_entity
    on audit_logs (tenant_id, entity_type, entity_id, created_at desc)
    where deleted = false;

create index if not exists idx_audit_logs_tenant_actor
    on audit_logs (tenant_id, lower(actor_email), created_at desc)
    where deleted = false;
