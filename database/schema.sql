-- GuardianVoice database schema (MySQL)
-- Matches the JPA entities in backend/src/main/java/com/guardianvoice/model
-- Hibernate can also create/update these automatically via
-- spring.jpa.hibernate.ddl-auto=update -- this file is for manual setup,
-- migrations, or reviewing the schema without running the app.

CREATE DATABASE IF NOT EXISTS guardianvoice
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE guardianvoice;

CREATE TABLE IF NOT EXISTS users (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  name          VARCHAR(120) NOT NULL,
  email         VARCHAR(160) NOT NULL UNIQUE,
  phone         VARCHAR(30),
  password_hash VARCHAR(255) NOT NULL,
  created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contacts (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  name                  VARCHAR(120) NOT NULL,
  phone                 VARCHAR(30) NOT NULL,
  relation              VARCHAR(60),
  is_primary            BOOLEAN NOT NULL DEFAULT FALSE,
  live_location_recipient BOOLEAN NOT NULL DEFAULT FALSE,
  user_id               BIGINT NOT NULL,
  CONSTRAINT fk_contacts_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS emergencies (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  status    VARCHAR(20) NOT NULL DEFAULT 'SENT',
  lat       DOUBLE,
  lng       DOUBLE,
  message   VARCHAR(500),
  station_name  VARCHAR(200),
  station_phone VARCHAR(40),
  station_lat   DOUBLE,
  station_lng   DOUBLE,
  timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  user_id   BIGINT NOT NULL,
  CONSTRAINT fk_emergencies_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS incident_reports (
  id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
  title               VARCHAR(150) NOT NULL,
  category            VARCHAR(30) NOT NULL DEFAULT 'OTHER',
  description         VARCHAR(2000) NOT NULL,
  lat                 DOUBLE,
  lng                 DOUBLE,
  address             VARCHAR(250),
  occurred_at         TIMESTAMP NULL,
  status              VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  synced_from_offline BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  user_id             BIGINT NOT NULL,
  CONSTRAINT fk_incident_reports_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS live_location_sessions (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  share_token  VARCHAR(64) NOT NULL UNIQUE,
  lat          DOUBLE,
  lng          DOUBLE,
  started_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  ended_at     TIMESTAMP NULL,
  user_id      BIGINT NOT NULL,
  emergency_id BIGINT NULL,
  CONSTRAINT fk_live_location_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_live_location_emergency FOREIGN KEY (emergency_id) REFERENCES emergencies(id) ON DELETE SET NULL
);

-- One row per user; created lazily by the app on first read/write, but
-- included here for completeness in manual/MySQL setups.
CREATE TABLE IF NOT EXISTS user_settings (
  user_id            BIGINT PRIMARY KEY,
  sos_keyword        VARCHAR(60)  NOT NULL DEFAULT 'help me',
  fake_call_keyword  VARCHAR(60)  NOT NULL DEFAULT 'call me',
  caller_name        VARCHAR(120) NOT NULL DEFAULT 'Mom',
  listen_enabled     BOOLEAN NOT NULL DEFAULT FALSE,
  tracking_enabled   BOOLEAN NOT NULL DEFAULT FALSE,
  CONSTRAINT fk_user_settings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_contacts_user_id ON contacts(user_id);
CREATE INDEX idx_emergencies_user_id ON emergencies(user_id);
CREATE INDEX idx_emergencies_timestamp ON emergencies(timestamp);
CREATE INDEX idx_incident_reports_user_id ON incident_reports(user_id);
CREATE INDEX idx_incident_reports_created_at ON incident_reports(created_at);
CREATE INDEX idx_live_location_user_id ON live_location_sessions(user_id);
CREATE INDEX idx_live_location_status ON live_location_sessions(status);
