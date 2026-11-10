package com.bank.aml.security.tenant;

/**
 * ThreadLocal holder for the current request's tenant identifier.
 * Ensures strict multi-tenant boundary isolation across repository lookups,
 * vector retrievals, and audit logs.
 */
public final class TenantContext {

    public static final String DEFAULT_TENANT_ID = "default-bank";
    private static final ThreadLocal<String> CURRENT_TENANT = ThreadLocal.withInitial(() -> DEFAULT_TENANT_ID);

    private TenantContext() {}

    public static String getTenantId() {
        String tenantId = CURRENT_TENANT.get();
        return (tenantId != null && !tenantId.isBlank()) ? tenantId : DEFAULT_TENANT_ID;
    }

    public static void setTenantId(String tenantId) {
        if (tenantId != null && !tenantId.isBlank()) {
            CURRENT_TENANT.set(tenantId.trim());
        } else {
            CURRENT_TENANT.set(DEFAULT_TENANT_ID);
        }
    }

    public static void clear() {
        CURRENT_TENANT.set(DEFAULT_TENANT_ID);
    }
}
