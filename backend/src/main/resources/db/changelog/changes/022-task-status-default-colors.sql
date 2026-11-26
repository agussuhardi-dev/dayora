--liquibase formatted sql
--changeset dayora:022-task-status-default-colors runOnChange:true
UPDATE task_status
SET color = CASE category
 WHEN 'UNSTARTED' THEN '#6d5dfc'
 WHEN 'STARTED' THEN '#3b82f6'
 WHEN 'COMPLETED' THEN '#10b981'
 WHEN 'CANCELED' THEN '#ef4444'
END
WHERE color IS NULL;

CREATE OR REPLACE FUNCTION seed_task_statuses() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO task_status(id,owner_id,workspace_id,name,category,color,position,is_default)
 SELECT gen_random_uuid(),NEW.owner_id,NEW.id,name,category,color,position,is_default FROM
 (VALUES
  ('To do','UNSTARTED','#6d5dfc',0,true),
  ('In progress','STARTED','#3b82f6',1,false),
  ('Done','COMPLETED','#10b981',2,false),
  ('Canceled','CANCELED','#ef4444',3,false)
 ) s(name,category,color,position,is_default);
 RETURN NEW;
END $$;
