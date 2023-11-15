create extension if not exists "uuid-ossp";

create table public.transactions
(
    card_holder_name   varchar not null,
    card_number        varchar not null,
    transaction_id     varchar not null,
    transaction_amount integer not null,
    transaction_type   varchar not null,
    region             varchar not null,
    date               varchar not null
);

create table public.tokens
(
    token    varchar not null,
    value    varchar not null
);

create user far with encrypted password 'far';
create user fin with encrypted password 'fin';
create user other with encrypted password 'other';
create role administrator;
create role fraud_and_risk;
create role fraud_investigation;
grant fraud_and_risk to far;
grant fraud_investigation to fin;
grant administrator to detokenization;
-- Grant select access to user 'other' on all (including future) tables
alter default privileges in schema public grant all on tables to other;

insert into public.transactions (card_holder_name, transaction_id, transaction_amount, transaction_type, region, date, card_number)
values  ('Maria Gonzalez', '637246159', '-1394', 'payment', 'Middle East/Africa', '2023-10-02 15:27:04', 'f431ec17-f1d8-498a-8773-41a2c689d527'),
        ('Shawn Lopez', '120990465', '4885', 'refund', 'Europe', '2023-10-01 15:27:04', '9437a24a-6ecf-4e0b-b2ec-b7cb44ed49ee'),
        ('Ronald Skinner', '214706393', '3318', 'bank_transfer', 'Asia Pacific', '2023-09-16 15:27:04', '810f9606-aa81-4aa6-8dc0-46ceeb176358'),
        ('Meghan Knight', '338527142', '3691', 'refund', 'Americas', '2023-10-14 15:27:04', '078793cf-9fcd-475a-9cd0-ad7e125c3f71'),
        ('Gary Castillo', '904246603', '3297', 'bank_transfer', 'Asia Pacific', '2023-06-22 15:27:04', '6d96495a-da9b-4098-9839-4ce29534246f'),
        ('Lisa Soto', '726862990', '3541', 'bank_transfer', 'Middle East/Africa', '2023-08-29 15:27:04', '311fb9d7-527e-493a-a4e0-e0aea7befff5'),
        ('Nicole Lee', '190468602', '-673', 'bank_transfer', 'Asia Pacific', '2023-09-23 15:27:04', '0373f559-46f9-44c6-8f62-e05d44eee197'),
        ('Dr. Sara Robinson', '959381975', '-1013', 'bank_transfer', 'Americas', '2023-06-09 15:27:04', '9bd8066f-e96c-41a1-bf96-ae6599f49c08'),
        ('Jacob Sandoval', '662625154', '-489', 'withdrawal', 'Americas', '2023-07-29 15:27:04', 'e54c4d94-7ad4-41bc-ba75-bca15d35feb7'),
        ('Rebecca Odom', '185399893', '-741', 'withdrawal', 'Middle East/Africa', '2023-07-18 15:27:04', '2147d057-c1fe-4913-b841-0e5034c3ef3b'),
        ('Nicole Lee', '557990943', '-1137', 'payment', 'Asia Pacific', '2023-05-26 15:27:04', '0373f559-46f9-44c6-8f62-e05d44eee197'),
        ('Joseph Hebert', '653364138', '-1525', 'payment', 'Asia Pacific', '2023-07-15 15:27:04', '97b8882c-69e2-4266-9d5f-df0238d98a5e'),
        ('Jordan Jackson', '492349894', '-3134', 'withdrawal', 'Middle East/Africa', '2023-08-26 15:27:04', 'fb56ff4a-f0e5-4b29-afb4-fac3e0483438'),
        ('Kathleen Gibson', '892398800', '196', 'refund', 'Middle East/Africa', '2023-08-04 15:27:04', '6af2e834-438b-404a-a357-76bcd3e7ce12'),
        ('Russell Johnson', '518809552', '-3657', 'withdrawal', 'Midle East/Africa', '2023-09-09 15:27:04', '2b9c33ee-9b76-416c-b979-b326819298fe'),
        ('William Chandler Jr.', '910069465', '-2132', 'withdrawal', 'Asia Pacific', '2023-07-29 15:27:04', '8dfa60a3-d1ae-4494-9550-5e4fe6130e8f'),
        ('Meghan Knight', '161308368', '1793', 'refund', 'Americas', '2023-06-09 15:27:04', '078793cf-9fcd-475a-9cd0-ad7e125c3f71'),
        ('William Chandler Jr.', '840377772', '2447', 'refund', 'Asia Pacific', '2023-06-17 15:27:04', '8dfa60a3-d1ae-4494-9550-5e4fe6130e8f'),
        ('Kimberly Perez', '818969714', '4013', 'refund', 'Asia Pacific', '2023-05-16 15:27:04', '36c44dfe-003f-4691-b83c-36992afc0497'),
        ('Ryan Wong', '780007425', '-181', 'payment', 'Asia Pacific', '2023-08-05 15:27:04', '1cf488f6-ebb8-4b82-997f-6e2dd95e8606'),
        ('Lori Weiss', '728945317', '-2788', 'bank_transfer', 'Europe', '2023-06-08 15:27:04', 'e324fb58-b383-4037-a21b-f2df0f0f15a4'),
        ('Kathleen Gibson', '381978913', '2553', 'refund', 'Middle East/Africa', '2023-07-26 15:27:04', '6af2e834-438b-404a-a357-76bcd3e7ce12'),
        ('Kimberly Perez', '828359902', '3819', 'refund', 'Asia Pacific', '2023-10-28 15:27:04', '36c44dfe-003f-4691-b83c-36992afc0497'),
        ('Jennifer Garcia', '427396304', '3678', 'bank_transfer', 'Europe', '2023-08-26 15:27:04', '5052fdc5-af7c-4407-b979-f3b9ec816d40'),
        ('Joseph Hebert', '493088676', '-506', 'payment', 'Asia Pacific', '2023-08-20 15:27:04', '97b8882c-69e2-4266-9d5f-df0238d98a5e'),
        ('Joseph Hebert', '976093536', '-1288', 'withdrawal', 'Asia Pacific', '2023-10-27 15:27:04', '97b8882c-69e2-4266-9d5f-df0238d98a5e'),
        ('Wanda Valencia', '958640801', '-3342', 'payment', 'Asia Pacific', '2023-09-12 15:27:04', 'f7bdde48-ec39-4eb1-8bb0-9d6657949ade'),
        ('Nathaniel Orr', '817521593', '-62', 'payment', 'Americas', '2023-08-01 15:27:04', '31a2b56e-8a71-4a5f-b8c6-00483bc5324d'),
        ('Gary Castillo', '121898818', '-4494', 'withdrawal', 'Asia Pacific', '2023-06-17 15:27:04', '6d96495a-da9b-4098-9839-4ce29534246f'),
        ('Jacob Sandoval', '783368925', '-4670', 'payment', 'Americas', '2023-05-27 15:27:04', 'e54c4d94-7ad4-41bc-ba75-bca15d35feb7'),
        ('Lori Weiss', '302485564', '-1761', 'withdrawal', 'Europe', '2023-10-06 15:27:04', 'e324fb58-b383-4037-a21b-f2df0f0f15a4'),
        ('Nicole Lee', '337643701', '3471', 'bank_transfer', 'Asia Pacific', '2023-09-29 15:27:04', '0373f559-46f9-44c6-8f62-e05d44eee197'),
        ('Ryan Wright', '936943623', '4323', 'refund', 'Middle East/Africa', '2023-10-11 15:27:04', '277f6e7c-7c90-4211-9886-7e73b7f284d7'),
        ('Ernest Reyes', '997961248', '-1024', 'payment', 'Asia Pacific', '2023-05-29 15:27:04', '35e4dcaf-b3fc-4ff4-8f99-ffee20c534ee'),
        ('Dr. Sara Robinson', '357236378', '-2732', 'payment', 'Americas', '2023-08-18 15:27:04', '9bd8066f-e96c-41a1-bf96-ae6599f49c08'),
        ('Nathaniel Cameron', '396680545', '819', 'refund', 'Asia Pacific', '2023-06-08 15:27:04', '730d9e5a-fe25-4702-af21-7d2fa2adbd83'),
        ('Dr. Sara Robinson', '512164283', '-4818', 'withdrawal', 'Americas', '2023-07-02 15:27:04', '9bd8066f-e96c-41a1-bf96-ae6599f49c08'),
        ('Nathaniel Orr', '168857826', '-1667', 'bank_transfer', 'Americas', '2023-05-16 15:27:04', '31a2b56e-8a71-4a5f-b8c6-00483bc5324d'),
        ('Gary Castillo', '149108869', '3415', 'refund', 'Asia Pacific', '2023-08-21 15:27:04', '6d96495a-da9b-4098-9839-4ce29534246f'),
        ('Kathleen Gibson', '577619902', '-2903', 'bank_transfer', 'Middle East/Africa', '2023-07-25 15:27:04', '6af2e834-438b-404a-a357-76bcd3e7ce12'),
        ('Valerie Woodard', '729114579', '-2486', 'withdrawal', 'Americas', '2023-10-16 15:27:04', 'e0f88d0d-352c-4ef0-8a30-6142f910458a'),
        ('Nathaniel Orr', '709621034', '2427', 'refund', 'Americas', '2023-06-01 15:27:04', '31a2b56e-8a71-4a5f-b8c6-00483bc5324d'),
        ('Maria Gonzalez', '285293025', '-4817', 'payment', 'Middle East/Africa', '2023-09-14 15:27:04', 'f431ec17-f1d8-498a-8773-41a2c689d527'),
        ('Lisa Soto', '223095972', '1019', 'bank_transfer', 'Middle East/Africa', '2023-05-21 15:27:04', '311fb9d7-527e-493a-a4e0-e0aea7befff5'),
        ('John Ochoa', '212939253', '1720', 'bank_transfer', 'Americas', '2023-11-01 15:27:04', '07ed4ab5-f177-4dde-afe5-8956803fbfcb'),
        ('John Ochoa', '584834761', '3470', 'refund', 'Americas', '2023-10-01 15:27:04', '07ed4ab5-f177-4dde-afe5-8956803fbfcb'),
        ('Kimberly Perez', '448927024', '-3558', 'withdrawal', 'Asia Pacific', '2023-10-18 15:27:04', '36c44dfe-003f-4691-b83c-36992afc0497'),
        ('Vanessa Meyer', '627775876', '-1736', 'withdrawal', 'Middle East/Africa', '2023-06-07 15:27:04', '7a923fed-03ec-4917-b36a-e9f95544cdc6'),
        ('Kathleen Gibson', '639595801', '-2099', 'withdrawal', 'Middle East/Africa', '2023-10-31 15:27:04', '6af2e834-438b-404a-a357-76bcd3e7ce12'),
        ('Nathaniel Orr', '357904300', '2214', 'refund', 'Americas', '2023-10-03 15:27:04', '31a2b56e-8a71-4a5f-b8c6-00483bc5324d'),
        ('Vanessa Meyer', '661147565', '-4210', 'payment', 'Middle East/Africa', '2023-10-09 15:27:04', '7a923fed-03ec-4917-b36a-e9f95544cdc6'),
        ('Ernest Reyes', '470538514', '4598', 'refund', 'Asia Pacific', '2023-10-12 15:27:04', '35e4dcaf-b3fc-4ff4-8f99-ffee20c534ee'),
        ('Nicole Lee', '831405755', '-2573', 'bank_transfer', 'Asia Pacific', '2023-06-27 15:27:04', '0373f559-46f9-44c6-8f62-e05d44eee197'),
        ('Joseph Hebert', '682691501', '1068', 'refund', 'Asia Pacific', '2023-07-26 15:27:04', '97b8882c-69e2-4266-9d5f-df0238d98a5e'),
        ('Charles Page', '849639902', '3200', 'refund', 'Middle East/Africa', '2023-06-10 15:27:04', 'ace95c87-caf5-4ba6-b95d-2f64119e3dd0'),
        ('Ernest Reyes', '156664852', '-4887', 'withdrawal', 'Asia Pacific', '2023-06-22 15:27:04', '35e4dcaf-b3fc-4ff4-8f99-ffee20c534ee'),
        ('Ernest Reyes', '678319873', '-4003', 'payment', 'Asia Pacific', '2023-10-15 15:27:04', '35e4dcaf-b3fc-4ff4-8f99-ffee20c534ee'),
        ('Nathaniel Cameron', '551889823', '2160', 'refund', 'Asia Pacific', '2023-07-30 15:27:04', '730d9e5a-fe25-4702-af21-7d2fa2adbd83'),
        ('Maria Gonzalez', '575060296', '-2991', 'withdrawal', 'Middle East/Africa', '2023-09-19 15:27:04', 'f431ec17-f1d8-498a-8773-41a2c689d527'),
        ('Nathaniel Cameron', '932723822', '-683', 'payment', 'Asia Pacific', '2023-09-16 15:27:04', '730d9e5a-fe25-4702-af21-7d2fa2adbd83'),
        ('Meghan Knight', '362185131', '4992', 'refund', 'Americas', '2023-07-07 15:27:04', '078793cf-9fcd-475a-9cd0-ad7e125c3f71'),
        ('Charles Page', '915701558', '4070', 'refund', 'Middle East/Africa', '2023-05-13 15:27:04', 'ace95c87-caf5-4ba6-b95d-2f64119e3dd0'),
        ('Ryan Wright', '237383037', '-1801', 'withdrawal', 'Middle East/Africa', '2023-07-25 15:27:04', '277f6e7c-7c90-4211-9886-7e73b7f284d7'),
        ('Joseph Hebert', '209562750', '45', 'refund', 'Asia Pacific', '2023-08-31 15:27:04', '97b8882c-69e2-4266-9d5f-df0238d98a5e'),
        ('Joseph Hebert', '132054574', '260', 'refund', 'Asia Pacific', '2023-06-17 15:27:04', '97b8882c-69e2-4266-9d5f-df0238d98a5e'),
        ('Lori Weiss', '397923036', '3481', 'bank_transfer', 'Europe', '2023-05-24 15:27:04', 'e324fb58-b383-4037-a21b-f2df0f0f15a4'),
        ('Vanessa Meyer', '130335172', '-4270', 'withdrawal', 'Middle East/Africa', '2023-10-16 15:27:04', '7a923fed-03ec-4917-b36a-e9f95544cdc6'),
        ('Gary Pitts', '512665765', '-1819', 'payment', 'Americas', '2023-10-04 15:27:04', 'cb460531-b9b9-4bbc-b519-857a1c64701e'),
        ('Shawn Lopez', '222771294', '680', 'refund', 'Europe', '2023-05-16 15:27:04', '9437a24a-6ecf-4e0b-b2ec-b7cb44ed49ee'),
        ('Jordan Jackson', '464058805', '2550', 'bank_transfer', 'Middle East/Africa', '2023-09-13 15:27:04', 'fb56ff4a-f0e5-4b29-afb4-fac3e0483438'),
        ('Gary Pitts', '279066014', '-211', 'payment', 'Americas', '2023-08-27 15:27:04', 'cb460531-b9b9-4bbc-b519-857a1c64701e'),
        ('Russell Johnson', '377324419', '-1232', 'payment', 'Middle East/Africa', '2023-06-26 15:27:04', '2b9c33ee-9b76-416c-b979-b326819298fe'),
        ('Maria Gonzalez', '241292933', '4192', 'bank_transfer', 'Middle East/Africa', '2023-10-28 15:27:04', 'f431ec17-f1d8-498a-8773-41a2c689d527'),
        ('Steven Ryan', '702707707', '4915', 'bank_transfer', 'Americas', '2023-10-26 15:27:04', '4e2aea7b-3c57-4737-9a27-89e92e638047'),
        ('John Ochoa', '121775532', '2305', 'refund', 'Americas', '2023-05-13 15:27:04', '07ed4ab5-f177-4dde-afe5-8956803fbfcb'),
        ('Charles Page', '863644248', '-1777', 'bank_transfer', 'Middle East/Africa', '2023-05-21 15:27:04', 'ace95c87-caf5-4ba6-b95d-2f64119e3dd0'),
        ('Joseph Hebert', '147699141', '-4223', 'withdrawal', 'Asia Pacific', '2023-07-16 15:27:04', '97b8882c-69e2-4266-9d5f-df0238d98a5e'),
        ('Steven Ryan', '115061272', '-4731', 'withdrawal', 'Americas', '2023-07-25 15:27:04', '4e2aea7b-3c57-4737-9a27-89e92e638047'),
        ('Gary Castillo', '927510934', '-4628', 'withdrawal', 'Asia Pacific', '2023-07-23 15:27:04', '6d96495a-da9b-4098-9839-4ce29534246f'),
        ('Joshua Mayo', '417609167', '-2932', 'payment', 'Americas', '2023-08-16 15:27:04', '32a86565-a0d1-42ce-b381-78672b32d45a'),
        ('Richard Jackson', '530244887', '-1500', 'payment', 'Asia Pacific', '2023-08-20 15:27:04', '34b9a6c8-d95a-4fe9-8df3-8bc6aa31df99'),
        ('Kimberly Perez', '585827718', '4153', 'bank_transfer', 'Asia Pacific', '2023-08-01 15:27:04', '36c44dfe-003f-4691-b83c-36992afc0497'),
        ('Wanda Valencia', '466868974', '79', 'refund', 'Asia Pacific', '2023-07-08 15:27:04', 'f7bdde48-ec39-4eb1-8bb0-9d6657949ade'),
        ('Lori Weiss', '386868059', '-1925', 'withdrawal', 'Europe', '2023-06-19 15:27:04', 'e324fb58-b383-4037-a21b-f2df0f0f15a4'),
        ('Kimberly Perez', '352113022', '2651', 'refund', 'Asia Pacific', '2023-09-03 15:27:04', '36c44dfe-003f-4691-b83c-36992afc0497'),
        ('John Ochoa', '116020092', '-1465', 'withdrawal', 'Americas', '2023-07-09 15:27:04', '07ed4ab5-f177-4dde-afe5-8956803fbfcb'),
        ('Joshua Mayo', '478940421', '-1532', 'withdrawal', 'Americas', '2023-09-04 15:27:04', '32a86565-a0d1-42ce-b381-78672b32d45a'),
        ('David Davis', '483867679', '-621', 'bank_transfer', 'Europe', '2023-08-16 15:27:04', '3fd1ff5c-fea7-4f00-9434-82816465c004'),
        ('Chris Santos', '501563971', '-1638', 'payment', 'Americas', '2023-09-28 15:27:04', 'd8506910-d940-41b1-b9b6-940502f5233c'),
        ('Gary Pitts', '518131881', '2215', 'bank_transfer', 'Americas', '2023-10-22 15:27:04', 'cb460531-b9b9-4bbc-b519-857a1c64701e'),
        ('Ryan Wong', '167599746', '4173', 'bank_transfer', 'Asia Pacific', '2023-10-17 15:27:04', '1cf488f6-ebb8-4b82-997f-6e2dd95e8606'),
        ('Jordan Jackson', '117781334', '1735', 'refund', 'Middle East/Africa', '2023-09-25 15:27:04', 'fb56ff4a-f0e5-4b29-afb4-fac3e0483438'),
        ('Jacob Foster', '693193871', '-3067', 'payment', 'Europe', '2023-06-25 15:27:04', '66f0bd02-4f36-4521-ac41-2d41ff8a7255'),
        ('Ryan Wong', '969635290', '3954', 'refund', 'Asia Pacific', '2023-08-16 15:27:04', '1cf488f6-ebb8-4b82-997f-6e2dd95e8606'),
        ('Charles Page', '146836995', '-493', 'withdrawal', 'Middle East/Africa', '2023-10-05 15:27:04', 'ace95c87-caf5-4ba6-b95d-2f64119e3dd0'),
        ('Gary Pitts', '554299827', '-2539', 'withdrawal', 'Americas', '2023-07-25 15:27:04', 'cb460531-b9b9-4bbc-b519-857a1c64701e'),
        ('William Chandler Jr.', '371580029', '-884', 'bank_transfer', 'Asia Pacific', '2023-09-17 15:27:04', '8dfa60a3-d1ae-4494-9550-5e4fe6130e8f'),
        ('Lori Weiss', '989557967', '3480', 'bank_transfer', 'Europe', '2023-09-19 15:27:04', 'e324fb58-b383-4037-a21b-f2df0f0f15a4'),
        ('Maria Gonzalez', '358959117', '-1094', 'bank_transfer', 'Middle East/Africa', '2023-11-01 15:27:04', 'f431ec17-f1d8-498a-8773-41a2c689d527'),
        ('Chris Santos', '959996002', '-3608', 'payment', 'Americas', '2023-08-05 15:27:04', 'd8506910-d940-41b1-b9b6-940502f5233c');

insert into public.tokens (token, value)
values  ('f431ec17-f1d8-498a-8773-41a2c689d527', '676249732592'),
        ('9437a24a-6ecf-4e0b-b2ec-b7cb44ed49ee', '502069034259'),
        ('810f9606-aa81-4aa6-8dc0-46ceeb176358', '676273039476'),
        ('078793cf-9fcd-475a-9cd0-ad7e125c3f71', '676162902214'),
        ('6d96495a-da9b-4098-9839-4ce29534246f', '571421649641'),
        ('311fb9d7-527e-493a-a4e0-e0aea7befff5', '574485553915'),
        ('0373f559-46f9-44c6-8f62-e05d44eee197', '501855662356'),
        ('9bd8066f-e96c-41a1-bf96-ae6599f49c08', '571325467397'),
        ('e54c4d94-7ad4-41bc-ba75-bca15d35feb7', '564750983913'),
        ('2147d057-c1fe-4913-b841-0e5034c3ef3b', '564473041015'),
        ('97b8882c-69e2-4266-9d5f-df0238d98a5e', '639054578377'),
        ('fb56ff4a-f0e5-4b29-afb4-fac3e0483438', '501888715700'),
        ('6af2e834-438b-404a-a357-76bcd3e7ce12', '676235790307'),
        ('2b9c33ee-9b76-416c-b979-b326819298fe', '676139248790'),
        ('8dfa60a3-d1ae-4494-9550-5e4fe6130e8f', '639079605460'),
        ('36c44dfe-003f-4691-b83c-36992afc0497', '573438513620'),
        ('1cf488f6-ebb8-4b82-997f-6e2dd95e8606', '586091303550'),
        ('e324fb58-b383-4037-a21b-f2df0f0f15a4', '570173509334'),
        ('5052fdc5-af7c-4407-b979-f3b9ec816d40', '584233562456'),
        ('f7bdde48-ec39-4eb1-8bb0-9d6657949ade', '639034271762'),
        ('31a2b56e-8a71-4a5f-b8c6-00483bc5324d', '574011268533'),
        ('277f6e7c-7c90-4211-9886-7e73b7f284d7', '060490074626'),
        ('35e4dcaf-b3fc-4ff4-8f99-ffee20c534ee', '579095161660'),
        ('730d9e5a-fe25-4702-af21-7d2fa2adbd83', '501805019228'),
        ('e0f88d0d-352c-4ef0-8a30-6142f910458a', '675925802208'),
        ('07ed4ab5-f177-4dde-afe5-8956803fbfcb', '501870715171'),
        ('7a923fed-03ec-4917-b36a-e9f95544cdc6', '675935446889'),
        ('ace95c87-caf5-4ba6-b95d-2f64119e3dd0', '560266430399'),
        ('cb460531-b9b9-4bbc-b519-857a1c64701e', '501866758821'),
        ('4e2aea7b-3c57-4737-9a27-89e92e638047', '060471097125'),
        ('32a86565-a0d1-42ce-b381-78672b32d45a', '676101329032'),
        ('34b9a6c8-d95a-4fe9-8df3-8bc6aa31df99', '581103944194'),
        ('3fd1ff5c-fea7-4f00-9434-82816465c004', '676201440085'),
        ('66f0bd02-4f36-4521-ac41-2d41ff8a7255', '501876699635'),
        ('d8506910-d940-41b1-b9b6-940502f5233c', '502057191939');
