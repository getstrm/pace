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

-- INSERT INTO public.demo (transactionid,userid,email,age,"size",haircolor,transactionamount,items,itemcount,"date",purpose) VALUES
-- (861200791,533445,'jeffreypowell@hotmail.com',33,'XS','red',123,'[19063]',1,'2022-08-30 15:44:44.000',1),
-- (733970993,468355,'forbeserik@gmail.com',16,'S','brown',46,'[13342, 12309, 13755, 10134]',4,'2022-07-19 15:44:44.000',2),
-- (494723158,553892,'wboone@gmail.com',64,'XS','black',73,'[13342, 10773, 12442]',3,'2022-06-18 15:44:44.000',2),
-- (208276802,774142,'oliverjulie@yahoo.com',12,'XL','brown',16,'[16966, 11368, 15673]',3,'2022-06-06 15:44:44.000',1),
-- (699389675,267574,'debra64@hotmail.com',79,'XS','red',186,'[14225, 11722, 16127, 16629]',4,'2022-06-15 15:44:44.000',1),
-- (174740434,844701,'blewis@yahoo.com',44,'S','black',232,'[18583, 13973]',2,'2022-10-24 15:44:44.000',0),
-- (970093468,839306,'smartin@yahoo.com',32,'XL','blonde',130,'[13638]',1,'2022-08-22 15:44:44.000',0),
-- (517552942,257977,'tmaynard@hotmail.com',82,'M','black',259,'[19047, 11368, 14086, 19606, 18263]',5,'2022-08-18 15:44:44.000',1),
-- (537925988,517692,'vrice@yahoo.com',23,'L','red',134,'[10134, 19732, 15552, 13863, 13152]',5,'2022-08-21 15:44:44.000',1),
-- (132876492,460057,'robertflowers@hotmail.com',8,'XL','blonde',186,'[13755, 13632, 18431, 12442]',4,'2022-10-14 15:44:44.000',0);
-- INSERT INTO public.demo (transactionid,userid,email,age,"size",haircolor,transactionamount,items,itemcount,"date",purpose) VALUES
-- (560312781,423577,'danielle87@hotmail.com',94,'XS','red',162,'[17616, 10134, 13638, 17466, 13632]',5,'2022-11-26 15:44:44.000',0),
-- (961847769,573171,'tfleming@hotmail.com',21,'L','brown',46,'[14569, 16006, 18263, 11368]',4,'2022-07-28 15:44:44.000',2),
-- (423973835,722699,'obennett@hotmail.com',66,'XS','brown',179,'[13342]',1,'2022-06-06 15:44:44.000',1),
-- (719567603,403972,'goodmangail@hotmail.com',86,'XS','red',29,'[10134, 16866]',2,'2022-07-10 15:44:44.000',0),
-- (298794071,160160,'twalker@yahoo.com',69,'M','blonde',56,'[15673]',1,'2022-08-30 15:44:44.000',2),
-- (739934738,657878,'heathercollins@yahoo.com',33,'S','black',226,'[13638, 13973]',2,'2022-08-27 15:44:44.000',0),
-- (741524747,213949,'omartin@yahoo.com',55,'M','brown',92,'[15894, 11722, 13152]',3,'2022-09-21 15:44:44.000',1),
-- (473108992,779506,'kennethreid@yahoo.com',55,'XS','black',196,'[19606, 18583, 16127, 14225]',4,'2022-06-26 15:44:44.000',0),
-- (601886496,393471,'kthompson@gmail.com',42,'M','blonde',190,'[16473]',1,'2022-11-19 15:44:44.000',2),
-- (270057253,285843,'lyonsluis@hotmail.com',7,'XS','brown',12,'[13342, 18583, 13638, 19732, 13152]',5,'2022-08-18 15:44:44.000',1);
-- INSERT INTO public.demo (transactionid,userid,email,age,"size",haircolor,transactionamount,items,itemcount,"date",purpose) VALUES
-- (458977536,740948,'stevencarr@yahoo.com',75,'M','blonde',65,'[13638, 19063, 10773]',3,'2022-06-21 15:44:44.000',2),
-- (800416138,883485,'allenrobert@gmail.com',15,'XS','black',77,'[16127, 19994, 16866, 19732]',4,'2022-07-06 15:44:44.000',2),
-- (519500819,192420,'rogerselizabeth@hotmail.com',66,'M','red',152,'[19732, 14086, 13638]',3,'2022-09-23 15:44:44.000',2),
-- (629637561,728380,'tinawhite@gmail.com',1,'M','brown',180,'[13863]',1,'2022-09-08 15:44:44.000',1),
-- (534704584,870941,'acole@gmail.com',4,'XL','red',7,'[19910, 10733, 12309, 13863, 13638, 11402]',6,'2022-06-08 15:44:44.000',2),
-- (807835672,867943,'knappjeremy@hotmail.com',49,'S','black',10,'[13152, 13638, 15323, 13963]',4,'2022-07-25 15:44:44.000',2),
-- (467414030,251481,'morriserin@hotmail.com',6,'M','black',277,'[13340, 17749, 13342, 18431]',4,'2022-10-02 15:44:44.000',0),
-- (994186205,500392,'wgolden@yahoo.com',68,'XL','blonde',160,'[13340, 13755, 11368, 10134, 10733]',5,'2022-08-19 15:44:44.000',2),
-- (217127008,143855,'nelsondaniel@hotmail.com',28,'M','red',263,'[17749, 15894]',2,'2022-10-29 15:44:44.000',0),
-- (142409570,567637,'meganriley@gmail.com',56,'M','blonde',296,'[12309]',1,'2022-08-28 15:44:44.000',1);
-- INSERT INTO public.demo (transactionid,userid,email,age,"size",haircolor,transactionamount,items,itemcount,"date",purpose) VALUES
-- (444040588,946416,'spierce@hotmail.com',43,'M','brown',92,'[15894, 13963, 17616, 16006, 13340, 11722]',6,'2022-11-01 15:44:44.000',2),
-- (375251092,519381,'bradychristopher@hotmail.com',1,'S','brown',26,'[19063, 16966, 14225, 11722, 16006, 13342]',6,'2022-10-05 15:44:44.000',2),
-- (478895317,983978,'whitemichelle@gmail.com',5,'S','blonde',227,'[17616]',1,'2022-09-04 15:44:44.000',1),
-- (866334544,414558,'halllinda@hotmail.com',28,'XS','red',31,'[19047, 10571, 12304, 16127]',4,'2022-09-06 15:44:44.000',0),
-- (660469432,993192,'daniel57@hotmail.com',5,'S','blonde',185,'[16629, 15323, 14225, 19732]',4,'2022-07-22 15:44:44.000',0),
-- (674904861,604463,'wileylisa@yahoo.com',9,'M','blonde',222,'[19994, 13973, 11402, 10773, 19910]',5,'2022-11-10 15:44:44.000',1),
-- (978127518,361916,'schavez@gmail.com',94,'XS','blonde',277,'[13963, 16629, 13973, 17749]',4,'2022-11-02 15:44:44.000',2),
-- (446146548,371207,'jeremy25@yahoo.com',96,'XL','brown',92,'[10134, 16127]',2,'2022-08-02 15:44:44.000',2),
-- (344158962,915477,'oscott@yahoo.com',47,'L','black',321,'[19919, 14569, 11368, 10571]',4,'2022-06-21 15:44:44.000',0),
-- (671066809,610103,'nanderson@yahoo.com',38,'XL','black',132,'[18431, 19910, 10773, 16866, 11368, 17616]',6,'2022-11-12 15:44:44.000',1);
-- INSERT INTO public.demo (transactionid,userid,email,age,"size",haircolor,transactionamount,items,itemcount,"date",purpose) VALUES
-- (540486019,582702,'jrodriguez@gmail.com',65,'XL','black',317,'[13863, 16473, 16473, 19047]',4,'2022-10-11 15:44:44.000',2),
-- (582598465,141093,'bryan83@yahoo.com',8,'XL','brown',180,'[13963, 19107, 13632, 10733, 10773]',5,'2022-07-17 15:44:44.000',1),
-- (295164000,209187,'lbarnes@gmail.com',34,'S','black',274,'[13342]',1,'2022-06-19 15:44:44.000',2),
-- (159940647,365953,'anitataylor@hotmail.com',85,'M','brown',203,'[19047, 13973]',2,'2022-08-15 15:44:44.000',1),
-- (920730724,423225,'adamsmichelle@hotmail.com',13,'XL','red',81,'[13342, 14086]',2,'2022-06-10 15:44:44.000',0),
-- (619750821,270759,'bowersmary@gmail.com',13,'L','brown',290,'[14225, 16629, 19047]',3,'2022-06-03 15:44:44.000',1),
-- (665033548,970991,'nmiller@yahoo.com',60,'XL','red',226,'[18583, 17749]',2,'2022-06-15 15:44:44.000',0),
-- (499779796,235458,'aprilwhite@yahoo.com',39,'XS','blonde',104,'[19994]',1,'2022-07-02 15:44:44.000',0),
-- (766331366,133890,'moralesariel@yahoo.com',52,'L','blonde',99,'[14211, 10773, 16629]',3,'2022-09-23 15:44:44.000',2),
-- (597825248,230479,'nbrandt@yahoo.com',49,'M','red',302,'[13638, 19063, 10134]',3,'2022-09-26 15:44:44.000',1);
