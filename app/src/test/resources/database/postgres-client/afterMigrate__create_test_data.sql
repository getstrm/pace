drop table if exists public.demo;

create table public.demo
(
    transactionid     int     not null,
    userid            int     not null,
    email             varchar not null,
    age               int     not null,
    brand             varchar not null,
    transactionamount int     not null
);

