.PHONY: all check-mark check-far check-other

SHELL = /bin/bash

PGPASSFILE       := $(shell pwd)/pgpass
PP               := standalone-sample-connection
REF              := public.demo
VIEW             := ${REF}_view
STANDALONE_FLAGS := -t -A -h localhost -p 5431 standalone

USERS := mark far other
VIEW_CSVS := $(addsuffix .csv, ${USERS})

# default target sets up everything, and reads the views from
# the standalone database table.
all: demo.csv $(VIEW_CSVS) check-mark check-far check-other

check-mark: mark.csv demo.csv
	# ----- checking marketing -----
	# checking emails with '****' for the local part
	@ ! cut --delimiter='|' --fields=3 $< | egrep -v '^\*\*\*\*@[a-z]+\.[a-z]+$$'
	# checking that the user ids are 0
	@ ! cut --delimiter='|' --fields=2 $< | egrep -v '^0$$'
	# checking that the brands are Apple or Other
	@ ! cut --delimiter='|' --fields=5 $< | egrep -v '^(Apple|Other)$$'
	# checking no age under 30
	@ awk -F '|' '{if($$4 <= 30) { printf("Age %s ≤ 30\n", $$4); exit 1;}}' $<
	# checking that the number of records in $< is less than the source table ${REF}
	@ ! [[ $$(wc -l < demo.csv) -eq $$(wc -l < $<) ]]

check-far: far.csv demo.csv
	# ----- checking fraud and risk -----
	# checking that the number of records in $< is equal to the source table ${REF}
	@ [[ $$(wc -l < demo.csv) -eq $$(wc -l < far.csv) ]]
	# only the brands have been changed to Apple and Other, everything else is the same
	@ diff <(cut -f 5 -d '|' --complement demo.csv) <(cut -f 5 -d '|' --complement far.csv)

check-other: other.csv demo.csv
	# ----- checking other users -----
	# all emails are '****'
	@ ! cut --delimiter='|' --fields=3 other.csv | egrep -v '^\*\*\*\*$$'
	# checking that the user ids are 0
	@ ! cut --delimiter='|' --fields=2 other.csv | egrep -v '^0$$'
	# checking that the brands are Apple or Other
	@ ! cut --delimiter='|' --fields=5 other.csv | egrep -v '^(Apple|Other)$$'
	# checking no age under 30
	@ awk -F '|' '{if($$4 <= 30) { printf("Age %s ≤ 30\n", $$4); exit 1;}}' other.csv
	# checking that the number of records in $< is less than the source table ${REF}
	@ ! [[ $$(wc -l < demo.csv) -eq $$(wc -l < other.csv) ]]

demo.csv:
	# downloading ${REF} to $@
	@ psql -t -c 'select * from demo'  -U standalone ${STANDALONE_FLAGS} -o $@

# the user views depend on the data-policy having been applied
${VIEW_CSVS}: %.csv: applied
	# downloading $@ by querying database as user $(basename $@)
	@ psql -c "select * from ${VIEW}"  -U $(basename $@)  ${STANDALONE_FLAGS} -o $@

launch-pace:
	# launching local pace install and storing pid in $@
	@ java -jar app.jar > ./pace.log& echo $$! > $@
	@ echo " pid of PACE is $$(cat $@)"
	# Waiting for port to become available
	@ ./wait-for-port.sh
	# port 50051 is open

blueprint.yaml: launch-pace
	# getting blueprint ${REF}
	@ ./pace get data-policy --processing-platform ${PP} --blueprint ${REF} > $@

# the rulesets.patch file is part of the test and committed in git.
policy.yaml: blueprint.yaml rulesets.patch
	# Creating $@ by patching $< with rulesets.patch
	@ cp blueprint.yaml $@ ; patch -R -p1 < rulesets.patch $@

upserted.yaml: policy.yaml
	# upserting $< and creating $@
	@ ./pace upsert data-policy $< > $@

applied: upserted.yaml
	@ ./pace apply data-policy --processing-platform ${PP} ${REF} && date > $@
	@ echo "applied $< at $$(cat $@)"

clean:
	rm -f blueprint.yaml policy.yaml upserted.yaml applied
	rm -f *.csv
	psql -c 'truncate table data_policies' -S -U pace -h localhost -p 5433 pace
	psql -U standalone -c "drop view if exists ${VIEW}" ${STANDALONE_FLAGS}
	if [ -f launch-pace ] ; then cat launch-pace|xargs kill; rm launch-pace ; fi
