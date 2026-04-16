-- Tenant contract and support information
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS client_name VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS support_level VARCHAR(50);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS contract_reference VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS contract_framework VARCHAR(255);
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS contract_end_date DATE;
ALTER TABLE tenants ADD COLUMN IF NOT EXISTS notes TEXT;
