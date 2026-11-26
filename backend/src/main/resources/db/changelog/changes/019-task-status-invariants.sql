--liquibase formatted sql
--changeset dayora:019-task-status-invariants splitStatements:false
UPDATE task_status SET is_default=false WHERE category<>'UNSTARTED' OR archived;
WITH chosen AS (SELECT DISTINCT ON (workspace_id) id FROM task_status WHERE category='UNSTARTED' AND NOT archived ORDER BY workspace_id,position,id) UPDATE task_status SET is_default=true WHERE id IN (SELECT id FROM chosen);
CREATE OR REPLACE FUNCTION seed_task_statuses() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO task_status(id,owner_id,workspace_id,name,category,position,is_default)
 SELECT gen_random_uuid(),NEW.owner_id,NEW.id,name,category,position,is_default FROM
 (VALUES ('To do','UNSTARTED',0,true),('In progress','STARTED',1,false),('Done','COMPLETED',2,false),('Canceled','CANCELED',3,false)) s(name,category,position,is_default);
 RETURN NEW;
END $$;
CREATE UNIQUE INDEX uq_task_status_workspace_name ON task_status(workspace_id,lower(name));
CREATE UNIQUE INDEX uq_task_status_default ON task_status(workspace_id) WHERE is_default AND NOT archived;
