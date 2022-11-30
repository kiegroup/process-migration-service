alter table migration_report_logs
    drop constraint FKj8bsydiucvs2kygnscp1bt1wy;

alter table plan_mappings
    drop constraint FKk892t85t9vt1xe6vf9nqwgqoh;

alter table process_instance_ids
    drop constraint FKobucfuy73fgsmkncl9q2rv6ko;

drop table migration_report_logs;
drop table migration_reports;
drop table migrations;
drop table plan_mappings;
drop table plans;
drop table process_instance_ids;

drop sequence MIG_REP_ID_SEQ;
drop sequence MIGRATION_ID_SEQ;
drop sequence PLAN_ID_SEQ;
