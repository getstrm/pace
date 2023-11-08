create extension if not exists "uuid-ossp";

create table public.demo
(
    transactionid     int     not null,
    userid            varchar     not null,
    email             varchar not null,
    age               int     not null,
    brand             varchar not null,
    transactionamount int     not null,
    postal_code       varchar not null
);

create table public.demo_token
(
    token_id varchar not null,
    userid   varchar not null
);

create user mark with encrypted password 'mark';
create user far with encrypted password 'far';
create user other with encrypted password 'other';
create role administrator;
create role marketing;
create role fraud_and_risk;
grant marketing to mark;
grant fraud_and_risk to far;
grant administrator to detokenization_user;
-- Grant select access to user 'other' on all (including future) tables
alter default privileges in schema public grant all on tables to other;

insert into public.demo (transactionid, userid, email, age, brand, transactionamount, postal_code)
values (861200791,'0e6e95e0-3d34-4073-934f-ed56ee463515','jeffreypowell@hotmail.com',33,'Lenovo',123,'8842UG'),
       (733970993,'4e0bdc70-0398-46f2-8e05-bb338f28942d','forbeserik@gmail.com',16,'Macbook',46,'8464AW'),
       (494723158,'afa2c690-0aa4-4c43-97fc-4d2a886b8072','wboone@gmail.com',64,'Lenovo',73,'4054XW'),
       (208276802,'9e47056b-6e3b-4fd6-9a9e-2d9caec884a1','oliverjulie@yahoo.com',12,'Lenovo',16,'5577OB'),
       (699389675,'da598f93-ae9e-4739-8e69-c988ab622a49','debra64@hotmail.com',79,'Macbook',186,'5104JI'),
       (174740434,'0e6e95e0-3d34-4073-934f-ed56ee463515','jeffreypowell@hotmail.com',44,'HP',232,'8842UG'),
       (970093468,'f83c124c-33f1-4640-a569-64370be15dd9','smartin@yahoo.com',32,'Lenovo',130,'1710XS'),
       (517552942,'9128f5ad-6b9d-4893-930d-992c4f19b28f','tmaynard@hotmail.com',82,'Lenovo',259,'9510EZ'),
       (537925988,'cb4aa543-09fb-4d88-a2ef-a21907347de1','vrice@yahoo.com',23,'Lenovo',134,'6314PP'),
       (132876492,'5e5c9d36-6ba6-4609-845c-85635a8a2ac0','robertflowers@hotmail.com',8,'Macbook',186,'5535XK'),
       (560312781,'831ac405-752f-416a-af0a-4d9523eebd8a','danielle87@hotmail.com',94,'Lenovo',162,'2660VN'),
       (961847769,'5cf58b77-218f-4c89-9f99-27fc7e8d2da1','tfleming@hotmail.com',21,'Acer',46,'8190MZ'),
       (423973835,'4452d316-b7f7-4fa1-b452-4ee58abaf939','obennett@hotmail.com',66,'Lenovo',179,'4313DO'),
       (719567603,'9e47056b-6e3b-4fd6-9a9e-2d9caec884a1','oliverjulie@yahoo.com',86,'HP',29,'5577OB'),
       (298794071,'7b31a6c4-bc50-4a9d-bb78-290f67e97d37','twalker@yahoo.com',69,'Lenovo',56,'9491AL'),
       (739934738,'9f4097bc-35f4-419c-8e1a-933e0d687225','heathercollins@yahoo.com',33,'Macbook',226,'8628ZB'),
       (741524747,'5f3f4fea-aca4-4fac-8ba0-0adb76b01f23','omartin@yahoo.com',55,'Acer',92,'4847RR'),
       (473108992,'ca89887d-5c47-4390-9b2e-43ec216bc7fc','kennethreid@yahoo.com',55,'HP',196,'9195SZ'),
       (601886496,'ae87fe33-2aef-4325-9a3a-40f69bcb6a5a','kthompson@gmail.com',42,'Macbook',190,'7274BH'),
       (270057253,'8c3f0723-128e-4761-85e9-f31be8ea8a5e','lyonsluis@hotmail.com',7,'HP',12,'1899DI'),
       (458977536,'f9b9aa9d-ac8f-47fb-b4d7-74fc96dfc054','stevencarr@yahoo.com',75,'Acer',65,'3988AZ'),
       (800416138,'f9b9aa9d-ac8f-47fb-b4d7-74fc96dfc054','stevencarr@yahoo.com',15,'Lenovo',77,'3988AZ'),
       (519500819,'f9b9aa9d-ac8f-47fb-b4d7-74fc96dfc054','stevencarr@yahoo.com',66,'HP',152,'3988AZ'),
       (629637561,'f9b9aa9d-ac8f-47fb-b4d7-74fc96dfc054','stevencarr@yahoo.com',1,'Acer',180,'3988AZ'),
       (534704584,'0cc90fce-c869-4f6b-8081-12100b7bede1','acole@gmail.com',4,'HP',7,'1693PH'),
       (807835672,'c4159cee-5df3-4725-aaf2-eb14cfe2b332','knappjeremy@hotmail.com',49,'Acer',10,'6613UM'),
       (467414030,'f4811257-93bc-4588-b57f-2d0ddf2a61f1','morriserin@hotmail.com',6,'Acer',277,'2735YW'),
       (994186205,'5021a194-8a93-4028-b71a-a7e68fa26a7d','wgolden@yahoo.com',68,'Lenovo',160,'2230EP'),
       (217127008,'0211b2c3-6607-45a1-8771-c56d7ea83227','nelsondaniel@hotmail.com',28,'Lenovo',263,'8588FY'),
       (142409570,'f065cdd4-0e9f-43fa-949e-6600e0e59eb4','meganriley@gmail.com',56,'Acer',296,'8826CS'),
       (444040588,'0e6e95e0-3d34-4073-934f-ed56ee463515','jeffreypowell@hotmail.com',43,'Macbook',92,'8842UG'),
       (375251092,'9820b42e-8c96-4ce4-8316-993bc7df32cd','bradychristopher@hotmail.com',1,'Macbook',26,'3554DA'),
       (478895317,'f75ad619-2f51-4acc-8046-f0408331440b','whitemichelle@gmail.com',5,'Macbook',227,'4666UR'),
       (866334544,'052a2321-a21a-4af5-bb09-a0847374bdc4','halllinda@hotmail.com',28,'Lenovo',31,'2027GY'),
       (660469432,'530adc7a-5790-40df-8554-b60d8a957d67','daniel57@hotmail.com',5,'Lenovo',185,'2931SY'),
       (674904861,'8ea9b88c-afaa-4e05-84ca-f09870a8a336','wileylisa@yahoo.com',9,'Acer',222,'1594IV'),
       (978127518,'8a54c261-716b-4908-9506-109b4bba8493','schavez@gmail.com',94,'Macbook',277,'7724RR'),
       (446146548,'baa1644f-f56d-4ede-b6f8-e6c776cf0347','jeremy25@yahoo.com',96,'Macbook',92,'4678DZ'),
       (344158962,'f9b9aa9d-ac8f-47fb-b4d7-74fc96dfc054','stevencarr@yahoo.com',47,'Macbook',321,'3988AZ'),
       (671066809,'9d00ebcb-8efb-449a-8dcf-49500d441f22','nanderson@yahoo.com',38,'HP',132,'7769PF'),
       (540486019,'9e47056b-6e3b-4fd6-9a9e-2d9caec884a1','oliverjulie@yahoo.com',65,'HP',317,'5577OB'),
       (582598465,'af73d5bf-fe6d-4051-bb29-512ecf84b5b2','bryan83@yahoo.com',8,'Lenovo',180,'4904WC'),
       (295164000,'4bfa2c5a-1cf2-4da1-815d-417fa1291929','lbarnes@gmail.com',34,'HP',274,'9421VC'),
       (159940647,'fb355b21-77f3-4308-966b-0e2376622b04','anitataylor@hotmail.com',85,'HP',203,'9892XT'),
       (920730724,'389a6dd3-cf37-474a-b34c-4e5113be4988','adamsmichelle@hotmail.com',13,'Macbook',81,'6063BO'),
       (619750821,'09bd61a8-13eb-4395-ba5a-d99d96a43e30','bowersmary@gmail.com',13,'HP',290,'7705EH'),
       (665033548,'596b545a-c2ca-4378-8cbc-f9492b9df90a','nmiller@yahoo.com',60,'Lenovo',226,'9431HO'),
       (499779796,'0e6e95e0-3d34-4073-934f-ed56ee463515','jeffreypowell@hotmail.com',39,'Acer',104,'8842UG'),
       (766331366,'e3681c56-07fd-4d76-a7bf-4f6fd08736e0','moralesariel@yahoo.com',52,'HP',99,'3073OW'),
       (597825248,'9e47056b-6e3b-4fd6-9a9e-2d9caec884a1','oliverjulie@yahoo.com',49,'HP',302,'5577OB');


insert into public.demo_token (token_id, userid)
values ('0e6e95e0-3d34-4073-934f-ed56ee463515', '533445'),
       ('4e0bdc70-0398-46f2-8e05-bb338f28942d', '468355'),
       ('afa2c690-0aa4-4c43-97fc-4d2a886b8072', '553892'),
       ('9e47056b-6e3b-4fd6-9a9e-2d9caec884a1', '774142'),
       ('da598f93-ae9e-4739-8e69-c988ab622a49', '267574'),
       ('f83c124c-33f1-4640-a569-64370be15dd9', '839306'),
       ('9128f5ad-6b9d-4893-930d-992c4f19b28f', '257977'),
       ('cb4aa543-09fb-4d88-a2ef-a21907347de1', '517692'),
       ('5e5c9d36-6ba6-4609-845c-85635a8a2ac0', '460057'),
       ('831ac405-752f-416a-af0a-4d9523eebd8a', '423577'),
       ('5cf58b77-218f-4c89-9f99-27fc7e8d2da1', '573171'),
       ('4452d316-b7f7-4fa1-b452-4ee58abaf939', '722699'),
       ('7b31a6c4-bc50-4a9d-bb78-290f67e97d37', '160160'),
       ('9f4097bc-35f4-419c-8e1a-933e0d687225', '657878'),
       ('5f3f4fea-aca4-4fac-8ba0-0adb76b01f23', '213949'),
       ('ca89887d-5c47-4390-9b2e-43ec216bc7fc', '779506'),
       ('ae87fe33-2aef-4325-9a3a-40f69bcb6a5a', '393471'),
       ('8c3f0723-128e-4761-85e9-f31be8ea8a5e', '285843'),
       ('f9b9aa9d-ac8f-47fb-b4d7-74fc96dfc054', '740948'),
       ('0cc90fce-c869-4f6b-8081-12100b7bede1', '870941'),
       ('c4159cee-5df3-4725-aaf2-eb14cfe2b332', '867943'),
       ('f4811257-93bc-4588-b57f-2d0ddf2a61f1', '251481'),
       ('5021a194-8a93-4028-b71a-a7e68fa26a7d', '500392'),
       ('0211b2c3-6607-45a1-8771-c56d7ea83227', '143855'),
       ('f065cdd4-0e9f-43fa-949e-6600e0e59eb4', '567637'),
       ('9820b42e-8c96-4ce4-8316-993bc7df32cd', '519381'),
       ('f75ad619-2f51-4acc-8046-f0408331440b', '983978'),
       ('052a2321-a21a-4af5-bb09-a0847374bdc4', '414558'),
       ('530adc7a-5790-40df-8554-b60d8a957d67', '993192'),
       ('8ea9b88c-afaa-4e05-84ca-f09870a8a336', '604463'),
       ('8a54c261-716b-4908-9506-109b4bba8493', '361916'),
       ('baa1644f-f56d-4ede-b6f8-e6c776cf0347', '371207'),
       ('9d00ebcb-8efb-449a-8dcf-49500d441f22', '610103'),
       ('af73d5bf-fe6d-4051-bb29-512ecf84b5b2', '141093'),
       ('4bfa2c5a-1cf2-4da1-815d-417fa1291929', '209187'),
       ('fb355b21-77f3-4308-966b-0e2376622b04', '365953'),
       ('389a6dd3-cf37-474a-b34c-4e5113be4988', '423225'),
       ('09bd61a8-13eb-4395-ba5a-d99d96a43e30', '270759'),
       ('596b545a-c2ca-4378-8cbc-f9492b9df90a', '970991'),
       ('e3681c56-07fd-4d76-a7bf-4f6fd08736e0', '133890');
