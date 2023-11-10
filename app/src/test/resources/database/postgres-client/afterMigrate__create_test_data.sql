drop table if exists public.demo;
drop role if exists mark;
drop role if exists far;
drop role if exists marketing;
drop role if exists fraud_and_risk;

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

comment on column public.demo.email IS 'This is a user email pace::pii pace::email and considered sensitive and pace::"with whitespace" and more';
create user mark with encrypted password 'mark';
create user far with encrypted password 'far';
create role marketing;
create role fraud_and_risk;
grant marketing to mark;
grant fraud_and_risk to far;

insert into public.demo (transactionid, userid, email, name, salary, postalcode, age, transactionamount, brand)
values (861200791, 533445, 'jeffreypowell@hotmail.com', 'Beth Brady', 25543, '9412DP',  33, 123, 'Lenovo')
     , (733970993, 468355, 'forbeserik@gmail.com', 'Lee Andrews', 25657, '5314UV', 16, 46, 'Macbook')
     , (494723158, 553892, 'wboone@gmail.com', 'Marshall Vega', 45448, '3515WG', 64, 73, 'Lenovo')
     , (208276802, 774142, 'oliverjulie@yahoo.com', 'Hugh Hunt', 60461, '6535LO', 12, 16, 'Lenovo')
     , (699389675, 267574, 'debra64@hotmail.com', 'Ross Harrington', 32242, '5587XS', 79, 186, 'Macbook')
     , (174740434, 844701, 'blewis@yahoo.com', 'Jeannie Washington', 26162, '5610AD', 44, 232, 'HP')
     , (970093468, 839306, 'smartin@yahoo.com', 'Jan Gutierrez', 28662, '1022HB', 32, 130, 'Lenovo')
     , (517552942, 257977, 'tmaynard@hotmail.com', 'Charlene Hayes', 83871, '2085SC', 82, 259, 'Lenovo')
     , (537925988, 517692, 'vrice@yahoo.com', 'Beulah Tate', 74602, '9834EX', 23, 134, 'Lenovo')
     , (132876492, 460057, 'robertflowers@hotmail.com', 'Adam Osborne', 94554, '1729XW', 8, 186, 'Macbook')
     , (560312781, 423577, 'danielle87@hotmail.com', 'Isaac Barber', 12876, '9997FT', 94, 162, 'Lenovo')
     , (961847769, 573171, 'tfleming@hotmail.com', 'Saul Baldwin', 96834, '3226DS', 21, 46, 'Acer')
     , (423973835, 722699, 'obennett@hotmail.com', 'Rafael Riley', 22400, '7297TC', 66, 179, 'Lenovo')
     , (719567603, 403972, 'goodmangail@hotmail.com', 'Tom Reid', 14452, '2696ZZ', 86, 29, 'HP')
     , (298794071, 160160, 'twalker@yahoo.com', 'Mathew Peters', 80960, '9519CW', 69, 56, 'Lenovo')
     , (739934738, 657878, 'heathercollins@yahoo.com', 'Forrest Grant', 78058, '5569MJ', 33, 226, 'Macbook')
     , (741524747, 213949, 'omartin@yahoo.com', 'Blanche Byrd', 53654, '8142AV', 55, 92, 'Acer')
     , (473108992, 779506, 'kennethreid@yahoo.com', 'Belinda Gonzales', 18274, '9124VM', 55, 196, 'HP')
     , (601886496, 393471, 'kthompson@gmail.com', 'Heidi Parks', 54568, '8731TM', 42, 190, 'Macbook')
     , (270057253, 285843, 'lyonsluis@hotmail.com', 'Irma Snyder', 31412, '3091DQ', 7, 12, 'HP')
     , (458977536, 740948, 'stevencarr@yahoo.com', 'Alejandro Cobb', 48825, '1197RE', 75, 65, 'Acer')
     , (800416138, 883485, 'allenrobert@gmail.com', 'Paula Munoz', 34115, '9069WQ', 15, 77, 'Lenovo')
     , (519500819, 192420, 'rogerselizabeth@hotmail.com', 'Bonnie Ramos', 54061, '9168JX', 66, 152, 'HP')
     , (629637561, 728380, 'tinawhite@gmail.com', 'Al Carpenter', 43037, '4242AT', 1, 180, 'Acer')
     , (534704584, 870941, 'acole@gmail.com', 'Craig Hunter', 88853, '2602UD', 4, 7, 'HP')
     , (807835672, 867943, 'knappjeremy@hotmail.com', 'Norman Farmer', 65178, '1737HY', 49, 10, 'Acer')
     , (467414030, 251481, 'morriserin@hotmail.com', 'Michael Walton', 47587, '5922RD', 6, 277, 'Acer')
     , (994186205, 500392, 'wgolden@yahoo.com', 'Robin Barnes', 25227, '6791YP', 68, 160, 'Lenovo')
     , (217127008, 143855, 'nelsondaniel@hotmail.com', 'Sonja Mclaughlin', 82038, '9562XP', 28, 263, 'Lenovo')
     , (142409570, 567637, 'meganriley@gmail.com', 'Marie Warner', 43223, '4174CD', 56, 296, 'Acer')
     , (444040588, 946416, 'spierce@hotmail.com', 'Noah Scott', 41584, '9339JS', 43, 92, 'Macbook')
     , (375251092, 519381, 'bradychristopher@hotmail.com', 'Tommie Mcguire', 22100, '7003WL', 1, 26, 'Macbook')
     , (478895317, 983978, 'whitemichelle@gmail.com', 'Kirk Thornton', 65003, '2794KU', 5, 227, 'Macbook')
     , (866334544, 414558, 'halllinda@hotmail.com', 'Dana Matthews', 18170, '5573AK', 28, 31, 'Lenovo')
     , (660469432, 993192, 'daniel57@hotmail.com', 'Dianne Willis', 61586, '4015VG', 5, 185, 'Lenovo')
     , (674904861, 604463, 'wileylisa@yahoo.com', 'Seth Graham', 61996, '2861CF', 9, 222, 'Acer')
     , (978127518, 361916, 'schavez@gmail.com', 'Robyn Knight', 71360, '4039SQ', 94, 277, 'Macbook')
     , (446146548, 371207, 'jeremy25@yahoo.com', 'Inez Harper', 20698, '1669EJ', 96, 92, 'Macbook')
     , (344158962, 915477, 'oscott@yahoo.com', 'Sheldon Nelson', 68524, '7405DJ', 47, 321, 'Macbook')
     , (671066809, 610103, 'nanderson@yahoo.com', 'Philip Taylor', 40201, '4254QU', 38, 132, 'HP')
     , (540486019, 582702, 'jrodriguez@gmail.com', 'Rene Garza', 15498, '7830YR', 65, 317, 'HP')
     , (582598465, 141093, 'bryan83@yahoo.com', 'Rudy Luna', 48914, '3650AX', 8, 180, 'Lenovo')
     , (295164000, 209187, 'lbarnes@gmail.com', 'Gerald Walsh', 73355, '8247TJ', 34, 274, 'HP')
     , (159940647, 365953, 'anitataylor@hotmail.com', 'Tyler Rice', 54051, '5371BO', 85, 203, 'HP')
     , (920730724, 423225, 'adamsmichelle@hotmail.com', 'Conrad Guzman', 92299, '2923EU', 13, 81, 'Macbook')
     , (619750821, 270759, 'bowersmary@gmail.com', 'Toni Wilkerson', 16403, '7231UD', 13, 290, 'HP')
     , (665033548, 970991, 'nmiller@yahoo.com', 'Paulette Lane', 52183, '8474PZ', 60, 226, 'Lenovo')
     , (499779796, 235458, 'aprilwhite@yahoo.com', 'Pedro Howard', 90262, '3009MW', 39, 104, 'Acer')
     , (766331366, 133890, 'moralesariel@yahoo.com', 'Julio Stanley', 70843, '5258IU', 52, 99, 'HP')
     , (597825248, 230479, 'nbrandt@yahoo.com', 'Shawn Moran', 35080, '9008VR', 49, 302, 'HP');
