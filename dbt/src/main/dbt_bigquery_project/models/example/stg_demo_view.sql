with
  user_groups as (
    select userGroup
    from `stream-machine-development.user_groups.user_groups`
    where userEmail = SESSION_USER()
  )
select
  `transactionid`,
  case
    when (
      ('fraud_and_risk' IN ( SELECT `userGroup` FROM `user_groups` ))
      or ('administrator' IN ( SELECT `userGroup` FROM `user_groups` ))
    ) then `userid`
    else 42
  end `userid`,
  case
    when ('administrator' IN ( SELECT `userGroup` FROM `user_groups` )) then `email`
    else 'banaan@banaan.com'
  end `email`,
  `age`,
  `transactionamount`,
  `brand`
from {{ ref('stg_demo') }}
where case
  when (
    ('administrator' IN ( SELECT `userGroup` FROM `user_groups` ))
    or ('fraud_and_risk' IN ( SELECT `userGroup` FROM `user_groups` ))
  ) then true
  else age > 8
end