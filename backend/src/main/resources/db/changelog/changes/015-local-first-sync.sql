--liquibase formatted sql
--changeset dayora:015-local-first-sync splitStatements:false
CREATE TABLE sync_device (
 owner_id uuid NOT NULL REFERENCES app_user(id), id uuid NOT NULL,
 last_seen_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(owner_id,id)
);
CREATE TABLE sync_receipt (
 owner_id uuid NOT NULL, device_id uuid NOT NULL, operation_id uuid NOT NULL,
 request jsonb NOT NULL, response jsonb NOT NULL,
 PRIMARY KEY(owner_id,device_id,operation_id),
 FOREIGN KEY(owner_id,device_id) REFERENCES sync_device(owner_id,id)
);
CREATE TABLE sync_entity (
 owner_id uuid NOT NULL REFERENCES app_user(id), entity_type text NOT NULL, entity_id uuid NOT NULL,
 revision bigint NOT NULL, deleted boolean NOT NULL, payload jsonb NOT NULL,
 PRIMARY KEY(owner_id,entity_type,entity_id)
);
CREATE TABLE sync_change (
 owner_id uuid NOT NULL REFERENCES app_user(id), cursor bigint NOT NULL,
 entity_type text NOT NULL, entity_id uuid NOT NULL, revision bigint NOT NULL,
 deleted boolean NOT NULL, payload jsonb NOT NULL, PRIMARY KEY(owner_id,cursor)
);
CREATE FUNCTION sync_payload(row_data jsonb) RETURNS jsonb LANGUAGE sql IMMUTABLE AS $$
 SELECT jsonb_object_agg((SELECT string_agg(CASE WHEN ord=1 THEN part ELSE initcap(part) END,'' ORDER BY ord) FROM unnest(string_to_array(key,'_')) WITH ORDINALITY AS p(part,ord)), value)
 FROM jsonb_each(row_data - 'owner_id')
$$;
CREATE FUNCTION sync_capture() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE d jsonb; o uuid; i uuid; typ text; rev bigint; seq bigint; gone boolean; p jsonb;
BEGIN
 d := CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
 o := (d->>'owner_id')::uuid;
 i := coalesce((d->>'id')::uuid,o);
 typ := CASE WHEN TG_TABLE_NAME='account_settings' THEN 'SETTINGS' ELSE upper(TG_TABLE_NAME) END;
 INSERT INTO account_event_counter(owner_id,next_sequence) VALUES(o,1) ON CONFLICT DO NOTHING;
 SELECT next_sequence INTO seq FROM account_event_counter WHERE owner_id=o FOR UPDATE;
 UPDATE account_event_counter SET next_sequence=seq+1 WHERE owner_id=o;
 SELECT revision+1 INTO rev FROM sync_entity WHERE owner_id=o AND entity_type=typ AND entity_id=i;
 rev := coalesce((d->>'revision')::bigint,rev,1);
 gone := TG_OP='DELETE' OR d->>'deleted_at' IS NOT NULL;
 p := sync_payload(d) || jsonb_build_object('id',i,'revision',rev);
 INSERT INTO sync_entity VALUES(o,typ,i,rev,gone,p)
 ON CONFLICT(owner_id,entity_type,entity_id) DO UPDATE SET revision=excluded.revision,deleted=excluded.deleted,payload=excluded.payload;
 INSERT INTO sync_change VALUES(o,seq,typ,i,rev,gone,p);
 RETURN CASE WHEN TG_OP='DELETE' THEN OLD ELSE NEW END;
END $$;
DO $$
DECLARE t text;
BEGIN
 FOREACH t IN ARRAY ARRAY['workspace','task','calendar_event','focus_session','time_entry','account_settings'] LOOP
  EXECUTE format('INSERT INTO sync_entity SELECT owner_id,%L,coalesce((to_jsonb(t)->>''id'')::uuid,owner_id),coalesce((to_jsonb(t)->>''revision'')::bigint,1),to_jsonb(t)->>''deleted_at'' IS NOT NULL,sync_payload(to_jsonb(t)) || jsonb_build_object(''id'',coalesce((to_jsonb(t)->>''id'')::uuid,owner_id),''revision'',coalesce((to_jsonb(t)->>''revision'')::bigint,1)) FROM %I t',CASE WHEN t='account_settings' THEN 'SETTINGS' ELSE upper(t) END,t);
  EXECUTE format('CREATE TRIGGER sync_capture AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION sync_capture()',t);
 END LOOP;
END $$;
