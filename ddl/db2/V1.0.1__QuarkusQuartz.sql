DROP TABLE QRTZ_FIRED_TRIGGERS;
DROP TABLE QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE QRTZ_SCHEDULER_STATE;
DROP TABLE QRTZ_LOCKS;
DROP TABLE QRTZ_SIMPLE_TRIGGERS;
DROP TABLE QRTZ_SIMPROP_TRIGGERS;
DROP TABLE QRTZ_CRON_TRIGGERS;
DROP TABLE QRTZ_TRIGGERS;
DROP TABLE QRTZ_JOB_DETAILS;
DROP TABLE QRTZ_CALENDARS;
DROP TABLE QRTZ_BLOB_TRIGGERS;

CREATE TABLE QRTZ_JOB_DETAILS
  (
    sched_name VARCHAR(120) NOT NULL,
    job_name VARCHAR(80) NOT NULL,
    job_group VARCHAR(80) NOT NULL,
    description VARCHAR(120),
    job_class_name VARCHAR(128) NOT NULL,
    is_durable INTEGER NOT NULL,
    is_nonconcurrent INTEGER NOT NULL,
    is_update_data INTEGER NOT NULL,
    requests_recovery INTEGER NOT NULL,
    job_data BLOB(2000),
    PRIMARY KEY (sched_name,job_name,job_group)
);

CREATE TABLE QRTZ_TRIGGERS
  (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(80) NOT NULL,
    trigger_group VARCHAR(80) NOT NULL,
    job_name VARCHAR(80) NOT NULL,
    job_group VARCHAR(80) NOT NULL,
    description VARCHAR(120),
    next_fire_time BIGINT,
    prev_fire_time BIGINT,
    priority INTEGER,
    trigger_state VARCHAR(16) NOT NULL,
    trigger_type VARCHAR(8) NOT NULL,
    start_time BIGINT NOT NULL,
    end_time BIGINT,
    calendar_name VARCHAR(80),
    misfire_instr smallint,
    job_data BLOB(2000),
    PRIMARY KEY (sched_name,trigger_name,trigger_group),
    FOREIGN KEY (sched_name,job_name,job_group) REFERENCES qrtz_job_details(sched_name,job_name,job_group)
);

CREATE TABLE QRTZ_SIMPLE_TRIGGERS
  (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(80) NOT NULL,
    trigger_group VARCHAR(80) NOT NULL,
    repeat_count BIGINT NOT NULL,
    repeat_interval BIGINT NOT NULL,
    times_triggered BIGINT NOT NULL,
    PRIMARY KEY (sched_name,trigger_name,trigger_group),
    FOREIGN KEY (sched_name,trigger_name,trigger_group) REFERENCES qrtz_triggers(sched_name,trigger_name,trigger_group)
);

CREATE TABLE QRTZ_CRON_TRIGGERS
  (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(80) NOT NULL,
    trigger_group VARCHAR(80) NOT NULL,
    cron_expression VARCHAR(120) NOT NULL,
    time_zone_id VARCHAR(80),
    PRIMARY KEY (sched_name,trigger_name,trigger_group),
    FOREIGN KEY (sched_name,trigger_name,trigger_group) REFERENCES qrtz_triggers(sched_name,trigger_name,trigger_group)
);

CREATE TABLE QRTZ_SIMPROP_TRIGGERS
  (
    sched_name VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(200) NOT NULL,
    TRIGGER_GROUP VARCHAR(200) NOT NULL,
    STR_PROP_1 VARCHAR(512),
    STR_PROP_2 VARCHAR(512),
    STR_PROP_3 VARCHAR(512),
    INT_PROP_1 INT,
    INT_PROP_2 INT,
    LONG_PROP_1 BIGINT,
    LONG_PROP_2 BIGINT,
    DEC_PROP_1 NUMERIC(13,4),
    DEC_PROP_2 NUMERIC(13,4),
    BOOL_PROP_1 VARCHAR(1),
    BOOL_PROP_2 VARCHAR(1),
    PRIMARY KEY (sched_name,TRIGGER_NAME,TRIGGER_GROUP),
    FOREIGN KEY (sched_name,TRIGGER_NAME,TRIGGER_GROUP)
    REFERENCES QRTZ_TRIGGERS(sched_name,TRIGGER_NAME,TRIGGER_GROUP)
);

CREATE TABLE QRTZ_BLOB_TRIGGERS
  (
    sched_name VARCHAR(120) NOT NULL,
    trigger_name VARCHAR(80) NOT NULL,
    trigger_group VARCHAR(80) NOT NULL,
    blob_data BLOB(2000),
    PRIMARY KEY (sched_name,trigger_name,trigger_group),
    FOREIGN KEY (sched_name,trigger_name,trigger_group) REFERENCES qrtz_triggers(sched_name,trigger_name,trigger_group)
);

CREATE TABLE QRTZ_CALENDARS
  (
    sched_name VARCHAR(120) NOT NULL,
    calendar_name VARCHAR(80) NOT NULL,
    calendar BLOB(2000) NOT NULL,
    PRIMARY KEY (calendar_name)
);

CREATE TABLE QRTZ_FIRED_TRIGGERS
  (
    sched_name VARCHAR(120) NOT NULL,
    entry_id VARCHAR(95) NOT NULL,
    trigger_name VARCHAR(80) NOT NULL,
    trigger_group VARCHAR(80) NOT NULL,
    instance_name VARCHAR(80) NOT NULL,
    fired_time BIGINT NOT NULL,
    sched_time BIGINT NOT NULL,
    priority INTEGER NOT NULL,
    state VARCHAR(16) NOT NULL,
    job_name VARCHAR(80),
    job_group VARCHAR(80),
    is_nonconcurrent INTEGER,
    requests_recovery INTEGER,
    PRIMARY KEY (sched_name,entry_id)
);

CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS
  (
    sched_name VARCHAR(120) NOT NULL,
    trigger_group VARCHAR(80) NOT NULL,
    PRIMARY KEY (sched_name,trigger_group)
);

CREATE TABLE QRTZ_SCHEDULER_STATE
  (
    sched_name VARCHAR(120) NOT NULL,
    instance_name VARCHAR(80) NOT NULL,
    last_checkin_time BIGINT NOT NULL,
    checkin_interval BIGINT NOT NULL,
    PRIMARY KEY (sched_name,instance_name)
);

CREATE TABLE QRTZ_LOCKS
  (
    sched_name VARCHAR(120) NOT NULL,
    lock_name VARCHAR(40) NOT NULL,
    PRIMARY KEY (sched_name,lock_name)
);