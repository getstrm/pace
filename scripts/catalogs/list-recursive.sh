#!/bin/bash
SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

re="^(.*)X_A_X(.*)$"

$SCRIPT_DIR/list-catalogs.sh | jq -r '.catalogs[]|[.id,.type]|join("X_A_X")' | while read s ; do
    [[ $s =~ $re ]] && catalog_id=${BASH_REMATCH[1]} && type=${BASH_REMATCH[2]}
    echo "Catalog id=$catalog_id type=$type"

    $SCRIPT_DIR/list-databases.sh \
        -c $catalog_id \
        | jq -r '.databases[]|[.id,.displayName]|join("X_A_X")' | while read s ; do
        [[ $s =~ $re ]] && db_id=${BASH_REMATCH[1]} && db_name=${BASH_REMATCH[2]}

        echo "  Database id=$db_id   $db_name"

        $SCRIPT_DIR/list-schemas.sh \
            -c $catalog_id -d $db_id \
            | jq -r '.schemas[]|[.id,.name]|join("X_A_X")' | while read s ; do
            [[ $s =~ $re ]] && schema_id=${BASH_REMATCH[1]} && schema_name=${BASH_REMATCH[2]}
            echo "    Schema id=$schema_id   $schema_name"
            $SCRIPT_DIR/list-tables.sh \
                -c $catalog_id -d $db_id -s $schema_id \
                | jq -r '.tables[]|[.id,.name]|join("X_A_X")' | while read s ; do
                [[ $s =~ $re ]] && table_id=${BASH_REMATCH[1]} && table_name=${BASH_REMATCH[2]}
                echo "      Table id=$table_id $table_name"
            done
        done
    done
done
