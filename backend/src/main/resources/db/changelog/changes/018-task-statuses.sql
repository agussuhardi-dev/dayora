--liquibase formatted sql
--changeset dayora:018-task-statuses splitStatements:false
CREATE TABLE task_status (
 id uuid PRIMARY KEY, owner_id uuid NOT NULL, workspace_id uuid NOT NULL,
 name varchar(120) NOT NULL CHECK(length(trim(name))>0), color varchar(20),
 category varchar(20) NOT NULL CHECK(category IN ('UNSTARTED','STARTED','COMPLETED','CANCELED')),
 position integer NOT NULL DEFAULT 0, is_default boolean NOT NULL DEFAULT false, archived boolean NOT NULL DEFAULT false,
 UNIQUE(owner_id,workspace_id,id), UNIQUE(workspace_id,name),
 FOREIGN KEY(owner_id,workspace_id) REFERENCES workspace(owner_id,id)
);
CREATE FUNCTION seed_task_statuses() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 INSERT INTO task_status(id,owner_id,workspace_id,name,category,position,is_default)
 SELECT gen_random_uuid(),NEW.owner_id,NEW.id,name,category,position,is_default FROM
 (VALUES ('To do','UNSTARTED',0,true),('In progress','STARTED',1,false),('Done','COMPLETED',2,true),('Canceled','CANCELED',3,false)) s(name,category,position,is_default);
 RETURN NEW;
END $$;
CREATE TRIGGER seed_task_statuses AFTER INSERT ON workspace FOR EACH ROW EXECUTE FUNCTION seed_task_statuses();
INSERT INTO task_status(id,owner_id,workspace_id,name,category,position,is_default)
 SELECT gen_random_uuid(),w.owner_id,w.id,s.name,s.category,s.position,s.is_default FROM workspace w CROSS JOIN
 (VALUES ('To do','UNSTARTED',0,true),('In progress','STARTED',1,false),('Done','COMPLETED',2,true),('Canceled','CANCELED',3,false)) s(name,category,position,is_default);
ALTER TABLE task ADD COLUMN status_id uuid;
UPDATE task t SET status_id=s.id FROM task_status s WHERE s.workspace_id=t.workspace_id AND s.category=CASE t.status WHEN 'TODO' THEN 'UNSTARTED' WHEN 'IN_PROGRESS' THEN 'STARTED' WHEN 'DONE' THEN 'COMPLETED' ELSE 'CANCELED' END;
ALTER TABLE task ALTER COLUMN status_id SET NOT NULL;
ALTER TABLE task ADD CONSTRAINT fk_task_status_workspace FOREIGN KEY(owner_id,workspace_id,status_id) REFERENCES task_status(owner_id,workspace_id,id);
CREATE INDEX ix_task_status ON task(status_id);
-- ponytail: retain status as a derived legacy sync field until old clients retire.
CREATE FUNCTION task_status_compatibility() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE s task_status;
BEGIN
 IF NEW.status_id IS NULL THEN
  SELECT * INTO s FROM task_status WHERE workspace_id=NEW.workspace_id AND NOT archived AND category=CASE NEW.status WHEN 'TODO' THEN 'UNSTARTED' WHEN 'IN_PROGRESS' THEN 'STARTED' WHEN 'DONE' THEN 'COMPLETED' ELSE 'CANCELED' END ORDER BY position,id LIMIT 1;
  NEW.status_id:=s.id;
 ELSE
  SELECT * INTO s FROM task_status WHERE id=NEW.status_id AND owner_id=NEW.owner_id AND workspace_id=NEW.workspace_id FOR SHARE;
 END IF;
 IF s.id IS NULL THEN RAISE EXCEPTION 'status unavailable'; END IF;
 IF s.archived AND (TG_OP='INSERT' OR NEW.status_id IS DISTINCT FROM OLD.status_id) THEN RAISE EXCEPTION 'status archived'; END IF;
 NEW.status:=CASE s.category WHEN 'UNSTARTED' THEN 'TODO' WHEN 'STARTED' THEN 'IN_PROGRESS' WHEN 'COMPLETED' THEN 'DONE' ELSE 'CANCELED' END;
 NEW.completed_at:=CASE WHEN s.category='COMPLETED' THEN coalesce(NEW.completed_at,now()) ELSE NULL END;
 RETURN NEW;
END $$;
CREATE TRIGGER task_status_compatibility BEFORE INSERT OR UPDATE ON task FOR EACH ROW EXECUTE FUNCTION task_status_compatibility();
