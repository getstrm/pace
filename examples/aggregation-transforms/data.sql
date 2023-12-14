create table public.salary
(
    employee VARCHAR NOT NULL PRIMARY KEY,
    city     VARCHAR NOT NULL,
    country  VARCHAR NOT NULL,
    salary   INTEGER NOT NULL
);

create user fin with encrypted password 'fin';
create user ukm with encrypted password 'ukm';
create user anna with encrypted password 'anna';
create user other with encrypted password 'other';
create role administrator;
create role finance;
create role uk_manager;
create role analytics;
grant finance to fin;
grant uk_manager to ukm;
grant analytics to anna;
grant administrator to aggregation;
-- Grant select access to user 'other' on all (including future) tables
alter default privileges in schema public grant all on tables to other;

INSERT INTO public.salary(employee, city, country, salary)
VALUES ('Courtney Chen', 'Singapore', 'Singapore', 72341)
     , ('Isabell Heinrich-Langern', 'Singapore', 'Singapore', 84525)
     , ('Vincent Regnier', 'London', 'UK', 81356)
     , ('Kristina Johnson', 'Aberdeen', 'UK', 68651)
     , ('Timothy Sampson', 'Singapore', 'Singapore', 60218)
     , ('Hans Georg Bachmann', 'Houston', 'USA', 81403)
     , ('李涛', 'London', 'UK', 66622)
     , ('Erin Dalton', 'Aberdeen', 'UK', 68388)
     , ('Margot Bourgeois', 'Singapore', 'Singapore', 68915)
     , ('अनन्या श्रीविमल', 'Houston', 'USA', 89295)
     , ('James Harris', 'Rotterdam', 'Netherlands', 63489)
     , ('अनुपम कुलकर्णी', 'Aberdeen', 'UK', 63377)
     , ('Charles Barrera', 'Houston', 'USA', 71468)
     , ('निखिल कुमार', 'London', 'UK', 67081)
     , ('Claude Delattre', 'Singapore', 'Singapore', 63550)
     , ('Caitlyn Harvey', 'Rotterdam', 'Netherlands', 65289)
     , ('Rebecca Wright', 'London', 'UK', 66962)
     , ('季帆', 'Rotterdam', 'Netherlands', 81389)
     , ('गायकवाड', 'Houston', 'USA', 73370)
     , ('Bernhard Fechner', 'Singapore', 'Singapore', 74503)
     , ('苏丽', 'Amsterdam', 'Netherlands', 79668)
     , ('Светлана Альбертовна Колобова', 'Amsterdam', 'Netherlands', 73656)
     , ('曾玲', 'Aberdeen', 'UK', 70351)
     , ('Klaus-D. Hellwig', 'Singapore', 'Singapore', 62080)
     , ('Scott Smith', 'Singapore', 'Singapore', 86708)
     , ('पार्थ गावित', 'London', 'UK', 83876)
     , ('Jamie Hodges', 'Aberdeen', 'UK', 60426)
     , ('Christopher Hernandez', 'London', 'UK', 88903)
     , ('刘慧', 'Amsterdam', 'Netherlands', 69653)
     , ('Keith King', 'Houston', 'USA', 74996)
     , ('Денисова Вероника Афанасьевна', 'Houston', 'USA', 88706)
     , ('Christiane Gauthier', 'Rotterdam', 'Netherlands', 86767)
     , ('James Anderson', 'Amsterdam', 'Netherlands', 62809)
     , ('William Phillips', 'Rotterdam', 'Netherlands', 63702)
     , ('Laurence Lecoq', 'Amsterdam', 'Netherlands', 72174)
     , ('शर्मिला कुमार', 'Amsterdam', 'Netherlands', 87211)
     , ('Крюков Кузьма Валерьянович', 'Aberdeen', 'UK', 89722)
     , ('陈瑜', 'Houston', 'USA', 81694)
     , ('Jennifer Brooks', 'London', 'UK', 85358)
     , ('Adrian Shaw', 'Rotterdam', 'Netherlands', 61560)
     , ('David Lambert', 'Rotterdam', 'Netherlands', 74908)
     , ('Julie Guerrero', 'Houston', 'USA', 73918)
     , ('Savannah Madden', 'Aberdeen', 'UK', 71893)
     , ('Miguel Schleich B.Sc.', 'Singapore', 'Singapore', 82083)
     , ('Sheila Thornton', 'Houston', 'USA', 77828)
     , ('陈霞', 'Amsterdam', 'Netherlands', 80704)
     , ('Hayley Graham DDS', 'Aberdeen', 'UK', 79985)
     , ('Michael Gonzalez', 'Rotterdam', 'Netherlands', 69308)
     , ('David Brown', 'Aberdeen', 'UK', 75569)
     , ('Marcus Robinson', 'Aberdeen', 'UK', 78831);
INSERT INTO public.salary(employee, city, country, salary)
values ('Hansjürgen Mies', 'Amsterdam', 'Netherlands', 89355)
     , ('Thibaut Pineau', 'Singapore', 'Singapore', 64422)
     , ('Elizabeth Santiago', 'Singapore', 'Singapore', 89036)
     , ('Третьякова Екатерина Вячеславовна', 'Rotterdam', 'Netherlands', 88284)
     , ('Tanya Conway', 'Houston', 'USA', 70009)
     , ('Justin Forbes', 'London', 'UK', 60861)
     , ('Julie Jackson', 'Aberdeen', 'UK', 89286)
     , ('Wesley Monroe', 'Rotterdam', 'Netherlands', 61221)
     , ('Michelle Hall', 'Aberdeen', 'UK', 69496)
     , ('Виноградов Мефодий Изотович', 'Aberdeen', 'UK', 61660)
     , ('Kiara Hogan', 'Singapore', 'Singapore', 80415)
     , ('杨晨', 'Amsterdam', 'Netherlands', 67351)
     , ('Christine King', 'Aberdeen', 'UK', 69814)
     , ('James Richards', 'Rotterdam', 'Netherlands', 68754)
     , ('Jacqueline Fisher', 'Amsterdam', 'Netherlands', 74340)
     , ('Patrick Rogers', 'London', 'UK', 83896)
     , ('Gabriel Brunet', 'Rotterdam', 'Netherlands', 82757)
     , ('John Robinson', 'Aberdeen', 'UK', 79713)
     , ('Édith Guérin', 'Singapore', 'Singapore', 81201)
     , ('Ing. Bernward Ullmann', 'Rotterdam', 'Netherlands', 79440)
     , ('Jacqueline Wilson', 'Houston', 'USA', 87651)
     , ('Benjamin Wilson', 'Aberdeen', 'UK', 75709)
     , ('Laura Duran', 'Rotterdam', 'Netherlands', 72093)
     , ('Татьяна Руслановна Стрелкова', 'Rotterdam', 'Netherlands', 84655)
     , ('Spencer Mcfarland', 'Rotterdam', 'Netherlands', 86392)
     , ('Michèle Cohen', 'Houston', 'USA', 75997)
     , ('Christina Copeland', 'Aberdeen', 'UK', 87837)
     , ('Herr Mohamed Blümel B.Sc.', 'Rotterdam', 'Netherlands', 86490)
     , ('Charles Renault', 'London', 'UK', 60393)
     , ('Синклитикия Афанасьевна Крылова', 'Amsterdam', 'Netherlands', 83181)
     , ('इन्दु दूबे', 'London', 'UK', 78374)
     , ('Loretta Morales', 'Rotterdam', 'Netherlands', 65589)
     , ('Bertrand de Leduc', 'Houston', 'USA', 71715)
     , ('Justin Gardner', 'Rotterdam', 'Netherlands', 84796)
     , ('ज़ाकिर दीक्षित', 'Amsterdam', 'Netherlands', 65589)
     , ('Kristy Maldonado', 'Houston', 'USA', 78675)
     , ('Chad Robinson', 'Amsterdam', 'Netherlands', 77941)
     , ('Michael Garcia', 'London', 'UK', 79023)
     , ('Anaïs Lejeune', 'London', 'UK', 69575)
     , ('Helen Clayton', 'Rotterdam', 'Netherlands', 83095)
     , ('गर्गी', 'Singapore', 'Singapore', 70707)
     , ('Mark Adkins', 'Amsterdam', 'Netherlands', 85618)
     , ('Hélène de la Barre', 'London', 'UK', 82203)
     , ('Courtney Andrews', 'Singapore', 'Singapore', 66555)
     , ('Angela Mueller', 'Amsterdam', 'Netherlands', 81765)
     , ('Michael Lee', 'Rotterdam', 'Netherlands', 66613)
     , ('Mark Garcia', 'London', 'UK', 61254)
     , ('Stephen Chavez', 'Rotterdam', 'Netherlands', 60784)
     , ('Ing. Sieghard Dietz B.Eng.', 'Singapore', 'Singapore', 85650)
     , ('अखिल रामलला', 'Singapore', 'Singapore', 71941);