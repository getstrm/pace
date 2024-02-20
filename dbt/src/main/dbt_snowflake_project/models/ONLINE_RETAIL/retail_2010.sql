{{
  config(
    materialized='table'
  )
}}

select
    CUSTOMERID,
    DESCRIPTION,
    INVOICE,
    INVOICEDATE,
    PRICE,
    QUANTITY,
    STOCKCODE,
    COUNTRY
from pace.online_retail.retail
where year(invoicedate) = 2010
