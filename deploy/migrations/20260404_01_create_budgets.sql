CREATE TABLE IF NOT EXISTS schema_migrations (
    version VARCHAR(100) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT NOW()
);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM schema_migrations WHERE version = '20260404_01_create_budgets') THEN
        CREATE TABLE IF NOT EXISTS budgets (
            id BIGSERIAL PRIMARY KEY,
            tenant_id VARCHAR(120) NOT NULL,
            budget_number VARCHAR(60) NOT NULL,
            customer_name VARCHAR(180) NOT NULL,
            customer_phone VARCHAR(40),
            equipment VARCHAR(180) NOT NULL,
            problem_description TEXT NOT NULL,
            items_description TEXT,
            labor_cost NUMERIC(12,2) NOT NULL DEFAULT 0,
            parts_cost NUMERIC(12,2) NOT NULL DEFAULT 0,
            discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
            total_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
            status VARCHAR(40) NOT NULL,
            valid_until DATE,
            notes TEXT,
            created_at TIMESTAMP NOT NULL,
            updated_at TIMESTAMP NOT NULL
        );

        CREATE INDEX IF NOT EXISTS idx_budgets_tenant_updated ON budgets (tenant_id, updated_at DESC);
        CREATE INDEX IF NOT EXISTS idx_budgets_tenant_status ON budgets (tenant_id, status);
        CREATE INDEX IF NOT EXISTS idx_budgets_tenant_number ON budgets (tenant_id, budget_number);

        INSERT INTO schema_migrations(version) VALUES ('20260404_01_create_budgets');
    END IF;
END $$;
