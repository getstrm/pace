truncate table pace.global_transforms;

insert into pace.global_transforms (ref, transform_type, created_at, updated_at, transform, active)
values ( 'email', 'TAG_TRANSFORM', '2023-11-08T14:23:47+0000', '2023-11-08T14:23:47+0000'
       , '{"ref": "email", "description": "A default transform that should be applied to fields tagged with ''email''.", "tag_transform": {"tag_content": "email", "transforms": [{"fixed": {"value": "***@***.***"}, "principals": []}]}}'
       , true)
     , ( 'name', 'TAG_TRANSFORM', '2023-11-08T14:23:59+0000', '2023-11-08T14:23:59+0000'
       , '{"ref": "name", "description": "A default transform that should be applied to fields tagged with ''name''.", "tag_transform": {"tag_content": "name", "transforms": [{"nullify": {}, "principals": []}]}}'
       , true);
