--liquibase formatted sql
--changeset dayora:016-wear-pairing
CREATE TABLE wear_pairing_token (
 token_hash VARCHAR(64) PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 device_id VARCHAR(120) NOT NULL,
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE wear_device (
 owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 device_id VARCHAR(120) NOT NULL,
 paired_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY (owner_id, device_id)
);
CREATE TABLE wear_command (
 owner_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
 operation_id UUID NOT NULL,
 device_id VARCHAR(120) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 response TEXT NOT NULL,
 PRIMARY KEY (owner_id, operation_id)
);
