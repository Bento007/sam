#!/bin/bash

set -e

SAM_DIR=$1
cd $SAM_DIR

export SBT_OPTS="-Xms2g -Xmx2g -Ddirectory.url=$DIRECTORY_URL -Ddirectory.password=$DIRECTORY_PASSWORD"
sbt "testOnly -- -n org.broadinstitute.tags.SchemaInit"
