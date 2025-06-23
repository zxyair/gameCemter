-- auto-generated definition
create table game_flow_record
(
    id             bigint auto_increment
        primary key,
    room_id        varchar(64)   not null,
    round          int           not null,
    user_id        varchar(64)   not null,
    winning_begin  tinyint       not null,
    winning_end    tinyint       not null,
    bet_amount     int default 0 not null,
    winning_random tinyint       not null,
    pool_balance   int           null,
    round_result   tinyint           null,
    operate_time   datetime      not null
);

create index idx_room
    on game_flow_record (room_id);

create index idx_room_round
    on game_flow_record (room_id, round);

create index idx_user
    on game_flow_record (user_id);

