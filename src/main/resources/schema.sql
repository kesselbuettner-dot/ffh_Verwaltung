-- Executed by Spring SQL initialization BEFORE Hibernate schema update.
-- PostgreSQL cannot add a non-null column without a default to a populated table.
-- The dashboard_messages table is absent on fresh installations; Hibernate creates it afterwards.
DO $$
BEGIN
 IF to_regclass('public.dashboard_messages') IS NOT NULL THEN
  ALTER TABLE public.dashboard_messages
    ADD COLUMN IF NOT EXISTS on_wallboard boolean DEFAULT FALSE;
  UPDATE public.dashboard_messages SET on_wallboard = FALSE WHERE on_wallboard IS NULL;
  ALTER TABLE public.dashboard_messages
    ALTER COLUMN on_wallboard SET DEFAULT FALSE,
    ALTER COLUMN on_wallboard SET NOT NULL;
 END IF;
END
$^^
