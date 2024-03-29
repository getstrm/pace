.PHONY: all check-mark check-far check-other global-transform-check done list-resources

SHELL = /bin/bash
# github actions downloads a `pace` cli, and puts it in this directory.
# PACE developers will probably already have `pace` somewhere on their path
# we don't check for the os type (Darwin for instance) because only github actions will use
# this local `pace` cli.
export PATH := .:${PATH}

PSQL             = PGPASSFILE=pgpass psql -h localhost -q
# the processing platform
PP               = standalone-sample-connection
TABLE            = demo
SCHEMA           = public
REF              = ${SCHEMA}.${TABLE}
VIEW             = ${REF}_view
STANDALONE_FLAGS = -t -A -p 5431 standalone # psql flags non-aligned tuples
PACE_CLI         = PACE_OUTPUT=yaml pace

# These are the login users defined in data-sql. We download the
# view for each one, and execute check-<user>
USERS            = mark far other
VIEW_CSVS        = $(addsuffix .csv, ${USERS})
CHECKS           = $(addprefix check-, ${USERS})

RESOURCES        = resources-0.yaml resources-1.yaml resources-2.yaml resources-3.yaml 
# default target sets up everything, reads the views from
# the standalone database table and executes the checks.
all: tables.yaml ${CHECKS} global-transform-check list-resources done

list-resources: ${RESOURCES}

resources-0.yaml:  load-db-data
	@ echo -n checking 'pace list resources'
	@ ${PACE_CLI} list resources > $@
	@ diff -q $@ resources-0-expected.yaml || (echo "$@ not as expected"; cat $@; exit 1)
	@ echo " ok"

resources-1.yaml:  load-db-data
	@ echo -n checking "pace list resources ${PP}"
	@ ${PACE_CLI} list resources ${PP}> $@
	@ diff -q $@ resources-1-expected.yaml || (echo "$@ not as expected"; cat $@; exit 1)
	@ echo " ok"
resources-2.yaml:  load-db-data
	@ echo -n checking "list resources ${PP}/standalone"
	@ ${PACE_CLI} list resources ${PP}/standalone > $@
	@ diff -q $@ resources-2-expected.yaml || (echo "$@ not as expected"; cat $@; exit 1)
	@ echo " ok"
resources-3.yaml:  load-db-data
	@ echo -n checking "list resources ${PP}/standalone/public"
	@ ${PACE_CLI} list resources ${PP}/standalone/public > $@
	@ diff -q $@ resources-3-expected.yaml || (echo "$@ not as expected"; cat $@; exit 1)
	@ echo " ok"

tables.yaml: applied
	@ echo "checking 'pace list tables'"
	@ ${PACE_CLI} list tables --processing-platform ${PP} --database standalone --schema ${SCHEMA} > $@
	@ diff -q $@ tables-expected.yaml || (echo "list tables command produced wrong output"; \
		cat $@; exit 1)
	@ echo "list tables ok"


global-transform-check: blueprint_with_global_rules.yaml
	@ diff -q $< blueprint_with_global_rules-expected.yaml || (echo "blueprint with global ruleset unexpected output"; \
		cat $<; exit 1)
	@ echo "blueprint with global transforms ok"


blueprint_with_global_rules.yaml: upserted-global-tag-transform.yaml
	@ echo getting blueprint global_test, expecting a rulesets section
	@ pace get data-policy --blueprint --processing-platform ${PP} \
		--database standalone --schema ${SCHEMA} \
		global_test > $@ && \
		echo "retrieved $@"


upserted-global-tag-transform.yaml: launch-pace
	@ echo upserting global-tag-transform.yaml
	@ pace upsert global-transform global-tag-transform.yaml > $@

check-mark: mark.csv demo.csv awk cut
	@ echo ----- checking marketing with user mark -----
	@ echo checking emails to have '****' for the local part
	@ ! cut --delimiter='|' --fields=3 $< | egrep -v '^\*\*\*\*@[a-z]+\.[a-z]+$$'
	@ echo checking that the user ids are 0
	@ ! cut --delimiter='|' --fields=2 $< | egrep -v '^0$$'
	@ echo checking that the brands are Apple or Other
	@ ! cut --delimiter='|' --fields=5 $< | egrep -v '^(Apple|Other)$$'
	@ echo checking no age under 30
	@ awk -F '|' '{if($$4 <= 30) { printf("Age %s ≤ 30\n", $$4); exit 1;}}' $<
	@ echo checking that the number of records in $< is less than the source table ${REF}
	@ ! [[ $$(wc -l < demo.csv) -eq $$(wc -l < $<) ]]
	@ echo ----- marketing view verified -----

check-far: far.csv demo.csv awk cut
	@ echo ----- checking fraud and risk with user far -----
	@ echo checking that the number of records in $< is equal to the source table ${REF}
	@ [[ $$(wc -l < demo.csv) -eq $$(wc -l < far.csv) ]]
	@ echo only the brands have been changed to Apple and Other, everything else is the same
	@ diff <(cut -f 5 -d '|' --complement demo.csv) <(cut -f 5 -d '|' --complement far.csv)
	@ echo ----- fraud and risk verified-----

check-other: other.csv demo.csv awk cut
	@ echo ----- checking other users with user 'other' -----
	@ echo all emails are '****'
	@ ! cut --delimiter='|' --fields=3 other.csv | egrep -v '^\*\*\*\*$$'
	@ echo checking that the user ids are 0
	@ ! cut --delimiter='|' --fields=2 other.csv | egrep -v '^0$$'
	@ echo checking that the brands are Apple or Other
	@ ! cut --delimiter='|' --fields=5 other.csv | egrep -v '^(Apple|Other)$$'
	@ echo checking no age under 30
	@ awk -F '|' '{if($$4 <= 30) { printf("Age %s ≤ 30\n", $$4); exit 1;}}' other.csv
	@ echo checking that the number of records in $< is less than the source table ${REF}
	@ ! [[ $$(wc -l < demo.csv) -eq $$(wc -l < other.csv) ]]
	@ echo ----- other users verified -----

done: check-mark check-far check-other
	@ echo ----- all checks passed -----

demo.csv: load-db-data
	@ echo downloading ${REF} to $@ as user standalone
	@ ${PSQL} -t -c "select * from ${REF}"  -U standalone ${STANDALONE_FLAGS} -o $@

# the user views depend on the data-policy having been applied
${VIEW_CSVS}: %.csv: applied
	@ echo downloading $@ by querying database as user $*
	@ ${PSQL} -c "select * from ${VIEW}"  -U $*  ${STANDALONE_FLAGS} -o $@

blueprint.yaml: launch-pace load-db-data
	@ echo getting blueprint ${REF}
	@ ${PACE_CLI} get data-policy --processing-platform ${PP} --database standalone --schema ${SCHEMA} --blueprint ${TABLE} > $@

# the rulesets.patch file is part of the test and committed in git.
policy.yaml: blueprint.yaml rulesets.patch
	@ echo Creating $@ by patching $< with rulesets.patch
	@ cp blueprint.yaml $@ ; patch -R -p1 < rulesets.patch $@

upserted.yaml: policy.yaml
	@ echo upserting $< and creating $@
	@ ${PACE_CLI} upsert data-policy $< > $@

applied: upserted.yaml
	@ ${PACE_CLI} apply data-policy --processing-platform ${PP} ${REF} > $@
	@ echo applied $<

load-db-data:
	@ echo Loading demo data into postgres
	@ ./wait-for-port.sh 5431
	@ ${PSQL} -f data.sql -p 5431 -U standalone standalone > load-db-data

awk cut:
	@ echo creating GNU coreutils symlinks
	@ ./gnu-coreutils-symlinks.sh

kill-pace:
	@ if [ -f launch-pace ] ; then \
		echo killing pace pid $$(cat launch-pace); \
		cat launch-pace|xargs kill; rm -f launch-pace; \
	fi

pace:
	@ echo downloading latest pace cli release
	@ ./download-cli.sh
	@ echo pace version: $$(${PACE_CLI} version)

app.jar:
	@ echo building $@ and copying it here.
	@ export gradle_options=$([[ $CI == 'true' ]] && echo '-x test' || echo '')
	@ (cd .. ; ./gradlew build $$(echo $$gradle_options)) && rm -f app.jar && cp ../server/build/libs/app.jar $@

launch-pace: app.jar
	@ echo checking postgres ports 5431 and 5433
	@ nc -z localhost 5431
	@ nc -z localhost 5433
	@ echo checking that port 50051 is free
	@ ! nc -z localhost 50051
	@ java -jar app.jar -Dapp.expose-application-exceptions=true > pace.log& echo $$! > $@
	@ echo launched local pace app.jar and stored pid $$(cat $@) in $@
	@ ./wait-for-port.sh
	@ echo port 50051 is open
	@ chmod 0600 pgpass

clean_files:
	@ rm -f blueprint.yaml policy.yaml upserted.yaml applied upserted-global-tag-transform.yaml
	@ rm -f blueprint_with_global_rules.yaml
	@ rm -f resources-?.*
	@ rm -f *.csv

clean: clean_files
	@ echo Cleaning all generated resources, pace policies and the ${REF} table.
	@ rm -f load-db-data pace
	@ rm -f awk cut
	@ rm -f app.jar
	@ rm -f pace.log
	@ ${PSQL} -U pace -c 'truncate table data_policies' -S -p 5433 pace 2> /dev/null || true
	@ ${PSQL} -U standalone -c "drop view if exists ${VIEW}" ${STANDALONE_FLAGS} 2> /dev/null
	@ ${PSQL} -U standalone -c "drop table if exists ${REF}" ${STANDALONE_FLAGS} 2> /dev/null
	@ ${MAKE} -s kill-pace
