--liquibase formatted sql
--changeset dayora:017-task-labels
CREATE TABLE label (
  id UUID PRIMARY KEY,
  owner_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  name VARCHAR(120) NOT NULL,
  color VARCHAR(7) NOT NULL,
  CONSTRAINT fk_label_workspace FOREIGN KEY (owner_id,workspace_id) REFERENCES workspace(owner_id,id),
  CONSTRAINT uq_label_scope_id UNIQUE (owner_id,workspace_id,id),
  CONSTRAINT ck_label_name CHECK (length(btrim(name)) BETWEEN 1 AND 120 AND name=btrim(name)),
  CONSTRAINT ck_label_color CHECK (color ~ '^#[0-9A-Fa-f]{6}$')
);
CREATE UNIQUE INDEX uq_label_workspace_normalized_name ON label(workspace_id,lower(name));
CREATE TABLE task_label (
  owner_id UUID NOT NULL,
  workspace_id UUID NOT NULL,
  task_id UUID NOT NULL,
  label_id UUID NOT NULL,
  CONSTRAINT pk_task_label PRIMARY KEY (task_id,label_id),
  CONSTRAINT fk_task_label_task FOREIGN KEY (owner_id,workspace_id,task_id) REFERENCES task(owner_id,workspace_id,id) ON DELETE CASCADE,
  CONSTRAINT fk_task_label_label FOREIGN KEY (owner_id,workspace_id,label_id) REFERENCES label(owner_id,workspace_id,id) ON DELETE CASCADE
);
CREATE INDEX ix_task_label_label ON task_label(label_id);
