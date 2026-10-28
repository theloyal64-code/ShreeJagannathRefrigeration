-- Shree Jagannath Refrigeration PostgreSQL schema.
-- Safe to apply repeatedly. Never store plain-text passwords.

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL CHECK (length(trim(name)) >= 2),
  phone TEXT UNIQUE NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('customer','admin','mechanic')),
  mechanic_profile TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS bookings (
  id BIGSERIAL PRIMARY KEY,
  customer_id BIGINT NOT NULL REFERENCES users(id),
  customer_name TEXT NOT NULL,
  phone TEXT NOT NULL,
  appliance TEXT NOT NULL CHECK (appliance IN ('AC Service','Refrigerator','Washing Machine','Microwave Oven','Geyser')),
  issue TEXT NOT NULL,
  address TEXT NOT NULL,
  preferred_slot TEXT NOT NULL DEFAULT '',
  pincode TEXT NOT NULL DEFAULT '',
  latitude DOUBLE PRECISION CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
  longitude DOUBLE PRECISION CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180),
  status TEXT NOT NULL DEFAULT 'Pending' CHECK (status IN ('Pending','Accepted','On the Way','In Progress','Completed','Rejected','Cancelled')),
  assigned_mechanic_id BIGINT REFERENCES users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  status_updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  rating SMALLINT CHECK (rating BETWEEN 1 AND 5),
  review TEXT NOT NULL DEFAULT '' CHECK (length(review) <= 500),
  reviewed_at TIMESTAMPTZ,
  CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX IF NOT EXISTS bookings_customer_idx ON bookings(customer_id, created_at DESC);
CREATE INDEX IF NOT EXISTS bookings_status_idx ON bookings(status, created_at DESC);
CREATE INDEX IF NOT EXISTS bookings_assigned_mechanic_idx ON bookings(assigned_mechanic_id, created_at DESC);
CREATE INDEX IF NOT EXISTS bookings_reviewed_idx ON bookings(reviewed_at DESC) WHERE rating IS NOT NULL;

CREATE TABLE IF NOT EXISTS booking_status_events (
  id BIGSERIAL PRIMARY KEY,
  booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
  changed_by BIGINT REFERENCES users(id),
  old_status TEXT,
  new_status TEXT NOT NULL CHECK (new_status IN ('Pending','Accepted','On the Way','In Progress','Completed','Rejected','Cancelled')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS booking_status_events_booking_idx ON booking_status_events(booking_id, created_at);
