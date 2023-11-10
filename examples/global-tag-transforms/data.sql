create table public.demo
(
    transactionid     int     not null,
    userid            int     not null,
    name              varchar not null,
    email             varchar not null,
    age               int     not null,
    salary            int     not null,
    postalcode        varchar not null,
    brand             varchar not null,
    transactionamount int     not null
);
comment on column public.demo.email IS 'This is a user email which should be considered as such. pace::pii-email';
create role administrator;
create role marketing;
create role fraud_and_risk;
