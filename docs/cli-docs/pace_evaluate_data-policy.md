## pace evaluate data-policy

Evaluate an existing data policy by applying it to sample data provided in a csv file

### Synopsis

Evaluates an existing data policy by applying it to sample data provided in a csv file.
You can use this to test the correctness of your field transforms and filters.
The csv file should contain a header row with the column names, matching the fields in the data policy.
A comma should be used as the delimiter.
Currently, only standard SQL data types are supported. For platform-specific transforms, test on the platform itself.

```
pace evaluate data-policy (policy-id) [flags]
```

### Examples

```
pace evaluate data-policy public.demo --processing-platform example-platform --sample-data sample.csv
Results for rule set with target: public.demo_view
group: administrator

 TRANSACTIONID   USERID   EMAIL                      AGE   BRAND    TRANSACTIONAMOUNT 

 534704584       870941   acole@gmail.com            4     HP       7                 
 807835672       867943   knappjeremy@hotmail.com    49    Acer     10                
 467414030       251481   morriserin@hotmail.com     6     Acer     277               
 994186205       500392   wgolden@yahoo.com          68    Lenovo   160               
 217127008       143855   nelsondaniel@hotmail.com   28    Lenovo   263               
 142409570       567637   meganriley@gmail.com       56    Acer     296               

group: marketing

 TRANSACTIONID   USERID   EMAIL              AGE   BRAND   TRANSACTIONAMOUNT 

 807835672       0        ****@hotmail.com   49    Other   10                
 994186205       0        ****@yahoo.com     68    Other   160               
 217127008       0        ****@hotmail.com   28    Other   263               
 142409570       0        ****@gmail.com     56    Other   296               

All other principals

 TRANSACTIONID   USERID   EMAIL   AGE   BRAND   TRANSACTIONAMOUNT 

 807835672       0        ****    49    Other   10                
 994186205       0        ****    68    Other   160               
 217127008       0        ****    28    Other   263               
 142409570       0        ****    56    Other   296
```

### Options

```
  -h, --help                         help for data-policy
  -o, --output string                output formats [table, yaml, json, json-raw] (default "table")
  -p, --processing-platform string   id of processing platform
      --sample-data string           path to a csv file containing sample data to evaluate a data policy
```

### Options inherited from parent commands

```
      --api-host string                         api host (default "localhost:50051")
      --telemetry-upload-interval-seconds int   Upload usage statistics every so often. Use -1 to disable (default 3600)
```

### SEE ALSO

* [pace evaluate](pace_evaluate.md)	 - Evaluate a specification

