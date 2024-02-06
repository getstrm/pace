
select
    transactionid,
    userid,
    email,
    age,
    brand,
    transactionamount
from {{ ref('demo') }}
