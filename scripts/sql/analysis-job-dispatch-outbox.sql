create table if not exists analysis_job_dispatch_outbox (
    outbox_id bigint not null auto_increment,
    analysis_job_id bigint not null,
    promotion_id bigint not null,
    status varchar(20) not null,
    payload longtext not null,
    published_at datetime null,
    publish_attempt_count int not null default 0,
    last_error varchar(1000) null,
    created_at datetime not null,
    updated_at datetime not null,
    primary key (outbox_id),
    index idx_analysis_job_dispatch_outbox_status_id (status, outbox_id),
    index idx_analysis_job_dispatch_outbox_analysis_job_id (analysis_job_id)
);
