alter table migration_report_logs
    drop
    foreign key if exists FKj8bsydiucvs2kygnscp1bt1wy;

alter table plan_mappings
    drop
    foreign key if exists FKk892t85t9vt1xe6vf9nqwgqoh;

alter table process_instance_ids
    drop
    foreign key if exists FKobucfuy73fgsmkncl9q2rv6ko;

drop table if exists migration_report_logs;
drop table if exists migration_reports;
drop table if exists migrations;
drop table if exists plan_mappings;
drop table if exists plans;
drop table if exists process_instance_ids;
drop sequence MIG_REP_ID_SEQ;
drop sequence MIGRATION_ID_SEQ;
drop sequence PLAN_ID_SEQ;
