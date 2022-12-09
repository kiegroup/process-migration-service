alter table migration_report_logs alter column log type oid;
alter table migrations alter column error_message type oid;
alter table if exists migration_reports
  drop constraint if exists FK98ckwvu4fyt55u6sq680xwkmx;