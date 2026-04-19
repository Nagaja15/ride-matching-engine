-- V1__initial_schema.sql
-- Initial schema for ride-matching engine (Ola/Uber style)
-- Run by Flyway on first startup

-- 1. Enable PostGIS extension (geospatial support)
CREATE EXTENSION IF NOT EXISTS postgis;

-- 2. Users table (riders + drivers)
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) UNIQUE NOT NULL,
    password        VARCHAR(255) NOT NULL,              -- bcrypt hashed
    role            VARCHAR(20) NOT NULL 
                    CHECK (role IN ('RIDER', 'DRIVER')),
    name            VARCHAR(100),
    phone           VARCHAR(20) UNIQUE,                 -- optional
    vehicle_type    VARCHAR(50),                        -- SEDAN, SUV, BIKE... NULL for riders
    is_available    BOOLEAN DEFAULT TRUE,               -- driver online/offline
    rating          DECIMAL(3,2) DEFAULT 5.00,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Trips / ride requests table
CREATE TABLE trips (
    id                BIGSERIAL PRIMARY KEY,
    rider_id          BIGINT REFERENCES users(id) ON DELETE SET NULL,
    driver_id         BIGINT REFERENCES users(id) ON DELETE SET NULL,
    pickup            GEOMETRY(Point, 4326) NOT NULL,
    dropoff           GEOMETRY(Point, 4326) NOT NULL,
    status            VARCHAR(30) NOT NULL DEFAULT 'REQUESTED'
                      CHECK (status IN ('REQUESTED', 'MATCHED', 'STARTED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    fare              NUMERIC(10,2),
    surge_multiplier  NUMERIC(4,2) DEFAULT 1.0,
    distance_km       NUMERIC(8,2),                    -- calculated after matching
    duration_min      INTEGER,                         -- calculated
    created_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Spatial indexes (critical for performance)
CREATE INDEX idx_trips_pickup_gist  ON trips USING GIST (pickup);
CREATE INDEX idx_trips_dropoff_gist ON trips USING GIST (dropoff);

-- 5. Index on status (fast filtering of active rides)
CREATE INDEX idx_trips_status ON trips (status);

-- 6. Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply trigger to both tables
CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_trips_updated_at
    BEFORE UPDATE ON trips
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();