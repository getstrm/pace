#!/bin/bash

echo '{}' | evans -r cli \
    --package getstrm.api.data_policies.v1alpha --service DataPolicyService \
    call ListProcessingPlatforms
