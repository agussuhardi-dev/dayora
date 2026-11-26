--liquibase formatted sql
--changeset dayora:021-focus-cycles
ALTER TABLE focus_session ADD COLUMN cycles_before_long_break integer NOT NULL DEFAULT 4 CHECK (cycles_before_long_break BETWEEN 1 AND 12);
