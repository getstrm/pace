.PHONY: all check-mark check-far check-other

SHELL = /bin/bash
# github actions downloads a `pace` cli, and puts it in this directory.
# PACE developers will probably already have `pace` somewhere on their path
# we don't check for the os type (Darwin for instance) because only github actions will use
# this local `pace` cli.
export PATH := .:${PATH}
CLI_URL          = https://github.com/getstrm/cli/releases/latest/download/pace_linux_amd64.tar.gz

PSQL             = PGPASSFILE=pgpass psql -h localhost -q
PP               = standalone-sample-connection # the processing platform
REF              = public.demo
VIEW             = ${REF}_view
STANDALONE_FLAGS = -t -A -p 5431 standalone # psql flags non-aligned tuples

# These are the login users defined in data-sql. We download the
# view for each one, and execute check-<user>
USERS            = mark far other
VIEW_CSVS        = $(addsuffix .csv, ${USERS})
CHECKS           = $(addprefix check-, ${USERS})

# default target sets up everything, reads the views from
# the standalone database table and executes the checks.
all: ${CHECKS}

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

demo.csv: load-db-data
	@ echo downloading ${REF} to $@ as user standalone
	@ ${PSQL} -t -c "select * from ${REF}"  -U standalone ${STANDALONE_FLAGS} -o $@

# the user views depend on the data-policy having been applied
${VIEW_CSVS}: %.csv: applied
	@ echo downloading $@ by querying database as user $*
	@ ${PSQL} -c "select * from ${VIEW}"  -U $*  ${STANDALONE_FLAGS} -o $@

blueprint.yaml: launch-pace load-db-data
	@ echo getting blueprint ${REF}
	@ pace get data-policy --processing-platform ${PP} --blueprint ${REF} > $@

# the rulesets.patch file is part of the test and committed in git.
policy.yaml: blueprint.yaml rulesets.patch
	@ echo Creating $@ by patching $< with rulesets.patch
	@ cp blueprint.yaml $@ ; patch -R -p1 < rulesets.patch $@

upserted.yaml: policy.yaml
	@ echo upserting $< and creating $@
	@ pace upsert data-policy $< > $@

applied: upserted.yaml
	@ pace apply data-policy --processing-platform ${PP} ${REF} && date > $@
	@ echo applied $< at $$(cat $@)

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
		cat launch-pace|xargs kill; rm launch-pace ; \
	fi

pace:
	@ echo downloading latest pace cli release
	@ curl --silent -L -o - ${CLI_URL} | tar xz > $@ && chmod +x $@
	@ echo pace version: $$(pace version)

app.jar:
	@ echo building $@ and symlinking it here.
	@ (cd .. ; gw build ) && ln -sf ../app/build/libs/app.jar $@

launch-pace: app.jar
	@ echo checking postgres ports 5431 and 5433
	@ nc -z localhost 5431
	@ nc -z localhost 5433
	@ echo checking that port 50051 is free
	@ ! nc -z localhost 50051
	@ java -jar app.jar > pace.log& echo $$! > $@
	@ echo launched local pace app.jar and stored pid $$(cat $@) in $@
	@ ./wait-for-port.sh
	@ echo port 50051 is open

clean:
	@ echo Cleaning all generated resources, pace policies and the ${REF} table.
	@ rm -f blueprint.yaml policy.yaml upserted.yaml applied
	@ rm -f *.csv
	@ rm -f load-db-data pace
	@ rm -f awk cut
	@ ${PSQL} -U pace -c 'truncate table data_policies' -S -p 5433 pace 2> /dev/null || true
	@ ${PSQL} -U standalone -c "drop view if exists ${VIEW}" ${STANDALONE_FLAGS} 2> /dev/null
	@ ${PSQL} -U standalone -c "drop table if exists ${REF}" ${STANDALONE_FLAGS} 2> /dev/null
	@ ${MAKE} -s kill-pace
