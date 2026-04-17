-- Fleet tracking: source IP and geo-location for clusters
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS source_ip VARCHAR(45);
ALTER TABLE clusters ADD COLUMN IF NOT EXISTS geo_location VARCHAR(255);
