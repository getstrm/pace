./gradlew -Pagent :dbt:run --args "/Users/bobvandenhoogen/getstrm/pace/dbt/src/main/dbt_bigquery_project"
./gradlew metadataCopy --task run --dir src/main/resources/META-INF/native-image
./gradlew :dbt:nativeCompile -Papp.dataPolicyValidator.skipTypeCheck=true
