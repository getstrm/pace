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

create user mark with encrypted password 'mark';
create user far with encrypted password 'far';
create role marketing;
create role fraud_and_risk;
grant marketing to mark;
grant fraud_and_risk to far;

insert into public.demo (transactionid, userid, email, age, transactionamount, brand)
values (861200791, 533445, 'jeffreypowell@hotmail.com', 33, 123, 'Lenovo')
     , (733970993, 468355, 'forbeserik@gmail.com', 16, 46, 'Macbook')
     , (494723158, 553892, 'wboone@gmail.com', 64, 73, 'Lenovo')
     , (208276802, 774142, 'oliverjulie@yahoo.com', 12, 16, 'Lenovo')
     , (699389675, 267574, 'debra64@hotmail.com', 79, 186, 'Macbook')
     , (174740434, 844701, 'blewis@yahoo.com', 44, 232, 'HP')
     , (970093468, 839306, 'smartin@yahoo.com', 32, 130, 'Lenovo')
     , (517552942, 257977, 'tmaynard@hotmail.com', 82, 259, 'Lenovo')
     , (537925988, 517692, 'vrice@yahoo.com', 23, 134, 'Lenovo')
     , (132876492, 460057, 'robertflowers@hotmail.com', 8, 186, 'Macbook')
     , (560312781, 423577, 'danielle87@hotmail.com', 94, 162, 'Lenovo')
     , (961847769, 573171, 'tfleming@hotmail.com', 21, 46, 'Acer')
     , (423973835, 722699, 'obennett@hotmail.com', 66, 179, 'Lenovo')
     , (719567603, 403972, 'goodmangail@hotmail.com', 86, 29, 'HP')
     , (298794071, 160160, 'twalker@yahoo.com', 69, 56, 'Lenovo')
     , (739934738, 657878, 'heathercollins@yahoo.com', 33, 226, 'Macbook')
     , (741524747, 213949, 'omartin@yahoo.com', 55, 92, 'Acer')
     , (473108992, 779506, 'kennethreid@yahoo.com', 55, 196, 'HP')
     , (601886496, 393471, 'kthompson@gmail.com', 42, 190, 'Macbook')
     , (270057253, 285843, 'lyonsluis@hotmail.com', 7, 12, 'HP')
     , (458977536, 740948, 'stevencarr@yahoo.com', 75, 65, 'Acer')
     , (800416138, 883485, 'allenrobert@gmail.com', 15, 77, 'Lenovo')
     , (519500819, 192420, 'rogerselizabeth@hotmail.com', 66, 152, 'HP')
     , (629637561, 728380, 'tinawhite@gmail.com', 1, 180, 'Acer')
     , (534704584, 870941, 'acole@gmail.com', 4, 7, 'HP')
     , (807835672, 867943, 'knappjeremy@hotmail.com', 49, 10, 'Acer')
     , (467414030, 251481, 'morriserin@hotmail.com', 6, 277, 'Acer')
     , (994186205, 500392, 'wgolden@yahoo.com', 68, 160, 'Lenovo')
     , (217127008, 143855, 'nelsondaniel@hotmail.com', 28, 263, 'Lenovo')
     , (142409570, 567637, 'meganriley@gmail.com', 56, 296, 'Acer')
     , (444040588, 946416, 'spierce@hotmail.com', 43, 92, 'Macbook')
     , (375251092, 519381, 'bradychristopher@hotmail.com', 1, 26, 'Macbook')
     , (478895317, 983978, 'whitemichelle@gmail.com', 5, 227, 'Macbook')
     , (866334544, 414558, 'halllinda@hotmail.com', 28, 31, 'Lenovo')
     , (660469432, 993192, 'daniel57@hotmail.com', 5, 185, 'Lenovo')
     , (674904861, 604463, 'wileylisa@yahoo.com', 9, 222, 'Acer')
     , (978127518, 361916, 'schavez@gmail.com', 94, 277, 'Macbook')
     , (446146548, 371207, 'jeremy25@yahoo.com', 96, 92, 'Macbook')
     , (344158962, 915477, 'oscott@yahoo.com', 47, 321, 'Macbook')
     , (671066809, 610103, 'nanderson@yahoo.com', 38, 132, 'HP')
     , (540486019, 582702, 'jrodriguez@gmail.com', 65, 317, 'HP')
     , (582598465, 141093, 'bryan83@yahoo.com', 8, 180, 'Lenovo')
     , (295164000, 209187, 'lbarnes@gmail.com', 34, 274, 'HP')
     , (159940647, 365953, 'anitataylor@hotmail.com', 85, 203, 'HP')
     , (920730724, 423225, 'adamsmichelle@hotmail.com', 13, 81, 'Macbook')
     , (619750821, 270759, 'bowersmary@gmail.com', 13, 290, 'HP')
     , (665033548, 970991, 'nmiller@yahoo.com', 60, 226, 'Lenovo')
     , (499779796, 235458, 'aprilwhite@yahoo.com', 39, 104, 'Acer')
     , (766331366, 133890, 'moralesariel@yahoo.com', 52, 99, 'HP')
     , (597825248, 230479, 'nbrandt@yahoo.com', 49, 302, 'HP');
