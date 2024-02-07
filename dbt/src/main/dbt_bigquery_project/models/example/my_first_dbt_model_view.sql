with
  user_groups as (
    select userGroup
    from `stream-machine-development.user_groups.user_groups`
    where userEmail = SESSION_USER()
  )
select 'banaan' `id`
from {{ ref('my_first_dbt_model') }}