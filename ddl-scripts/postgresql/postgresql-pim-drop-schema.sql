alter table if exists migration_report_logs
    drop constraint if exists FKj8bsydiucvs2kygnscp1bt1wy;

alter table if exists plan_mappings
    drop constraint if exists FKk892t85t9vt1xe6vf9nqwgqoh;

alter table if exists process_instance_ids
    drop constraint if exists FKobucfuy73fgsmkncl9q2rv6ko;

drop table if exists migration_report_logs cascade;
drop table if exists migration_reports cascade;
drop table if exists migrations cascade;
drop table if exists plan_mappings cascade;
drop table if exists plans cascade;
drop table if exists process_instance_ids cascade;

drop sequence if exists MIG_REP_ID_SEQ;
drop sequence if exists MIGRATION_ID_SEQ;
drop sequence if exists PLAN_ID_SEQ;
