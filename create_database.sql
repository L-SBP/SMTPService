create table mailbox_prod.blacklist
(
    id         bigint auto_increment
        primary key,
    type       enum ('IP', 'EMAIL')               not null,
    value      varchar(255)                       not null,
    created_at datetime default CURRENT_TIMESTAMP not null,
    constraint value
        unique (value)
);

create index idx_type
    on mailbox_prod.blacklist (type);

create index idx_value
    on mailbox_prod.blacklist (value);

create table mailbox_prod.email_queue
(
    id             bigint auto_increment
        primary key,
    sender         varchar(255)                                                               not null,
    recipients     text                                                                       not null,
    subject        varchar(500)                                                               not null,
    body           longtext                                                                   null,
    status         enum ('PENDING', 'PROCESSING', 'SENT', 'FAILED') default 'PENDING'         null,
    retry_count    int                                              default 0                 null,
    error_message  text                                                                       null,
    scheduled_time datetime                                                                   null,
    sent_time      datetime                                                                   null,
    created_at     datetime                                         default CURRENT_TIMESTAMP not null,
    updated_at     datetime                                         default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP
);

create index idx_created_at
    on mailbox_prod.email_queue (created_at);

create index idx_scheduled_time
    on mailbox_prod.email_queue (scheduled_time);

create index idx_status
    on mailbox_prod.email_queue (status);

create table mailbox_prod.server_config
(
    id           bigint auto_increment
        primary key,
    config_key   varchar(255)                       not null,
    config_value text                               null,
    description  text                               null,
    updated_at   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP,
    constraint config_key
        unique (config_key)
);

create index idx_config_key
    on mailbox_prod.server_config (config_key);

create table mailbox_prod.system_log
(
    id         bigint auto_increment
        primary key,
    level      varchar(50)                        not null,
    logger     varchar(255)                       not null,
    message    text                               not null,
    exception  text                               null,
    created_at datetime default CURRENT_TIMESTAMP not null
);

create index idx_created_at
    on mailbox_prod.system_log (created_at);

create index idx_level
    on mailbox_prod.system_log (level);

create index idx_logger
    on mailbox_prod.system_log (logger);

create table mailbox_prod.users
(
    id          bigint auto_increment
        primary key,
    username    varchar(255)                         not null,
    email       varchar(255)                         not null,
    password    varchar(255)                         not null,
    signature   text                                 null,
    is_admin    tinyint(1) default 0                 null,
    enabled     tinyint(1) default 1                 null,
    quota_limit double     default 100               null,
    used_space  double     default 0                 null,
    last_login  datetime                             null,
    updated_at  datetime                             not null,
    created_at  datetime   default CURRENT_TIMESTAMP not null,
    constraint email
        unique (email),
    constraint username
        unique (username)
);

create table mailbox_prod.emails
(
    has_attachment bit                                      null,
    is_read        bit                                      null,
    is_starred     bit                                      null,
    size           int                                      null,
    created_at     datetime(6)                              null,
    id             bigint auto_increment
        primary key,
    received_time  datetime(6)                              null,
    user_id        bigint                                   null,
    sender         varchar(255)                             not null,
    subject        varchar(255)                             not null,
    body           tinytext                                 null,
    folder_type    enum ('INBOX', 'SENT', 'DRAFT', 'TRASH') null,
    constraint FK41wb6kvdemvj1602iltrfr1uo
        foreign key (user_id) references mailbox_prod.users (id)
)
    charset = utf8mb4;

create table mailbox_prod.attachments
(
    id           bigint auto_increment
        primary key,
    email_id     bigint                             not null,
    file_name    varchar(500)                       not null,
    file_size    bigint                             not null,
    content_type varchar(255)                       null,
    file_path    varchar(1000)                      not null,
    uploaded_at  datetime default CURRENT_TIMESTAMP not null,
    constraint attachments_ibfk_1
        foreign key (email_id) references mailbox_prod.emails (id)
            on delete cascade
);

create index idx_email_id
    on mailbox_prod.attachments (email_id);

create index idx_file_name
    on mailbox_prod.attachments (file_name);

create table mailbox_prod.email_bcc
(
    id       bigint auto_increment
        primary key,
    email_id bigint       not null,
    bcc      varchar(255) not null,
    constraint email_bcc_ibfk_1
        foreign key (email_id) references mailbox_prod.emails (id)
            on delete cascade
);

create index idx_bcc
    on mailbox_prod.email_bcc (bcc);

create index idx_email_id
    on mailbox_prod.email_bcc (email_id);

create table mailbox_prod.email_cc
(
    id       bigint auto_increment
        primary key,
    email_id bigint       not null,
    cc       varchar(255) not null,
    constraint email_cc_ibfk_1
        foreign key (email_id) references mailbox_prod.emails (id)
            on delete cascade
);

create index idx_cc
    on mailbox_prod.email_cc (cc);

create index idx_email_id
    on mailbox_prod.email_cc (email_id);

create table mailbox_prod.email_recipients
(
    id        bigint auto_increment
        primary key,
    email_id  bigint       not null,
    recipient varchar(255) not null,
    constraint email_recipients_ibfk_1
        foreign key (email_id) references mailbox_prod.emails (id)
            on delete cascade
);

create index idx_email_id
    on mailbox_prod.email_recipients (email_id);

create index idx_recipient
    on mailbox_prod.email_recipients (recipient);

create table mailbox_prod.mail_groups
(
    id          bigint auto_increment
        primary key,
    name        varchar(255)                       not null,
    description text                               null,
    owner_id    bigint                             not null,
    created_at  datetime default CURRENT_TIMESTAMP not null,
    constraint mail_groups_ibfk_1
        foreign key (owner_id) references mailbox_prod.users (id)
            on delete cascade
);

create table mailbox_prod.group_members
(
    id        bigint auto_increment
        primary key,
    group_id  bigint                                             not null,
    user_id   bigint                                             not null,
    role      enum ('OWNER', 'MEMBER') default 'MEMBER'          null,
    joined_at datetime                 default CURRENT_TIMESTAMP not null,
    constraint uk_group_user
        unique (group_id, user_id),
    constraint group_members_ibfk_1
        foreign key (group_id) references mailbox_prod.mail_groups (id)
            on delete cascade,
    constraint group_members_ibfk_2
        foreign key (user_id) references mailbox_prod.users (id)
            on delete cascade
);

create index idx_group_id
    on mailbox_prod.group_members (group_id);

create index idx_user_id
    on mailbox_prod.group_members (user_id);

create index idx_name
    on mailbox_prod.mail_groups (name);

create index idx_owner_id
    on mailbox_prod.mail_groups (owner_id);

create index idx_email
    on mailbox_prod.users (email);

create index idx_enabled
    on mailbox_prod.users (enabled);

create index idx_username
    on mailbox_prod.users (username);

