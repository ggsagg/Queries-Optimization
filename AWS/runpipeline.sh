#!/bin/bash

CLUSTER_ID=$1
MAIN_JAR=s3://$2/jars/SparkScalaTest-assembly-1.0.jar
PROFILE=default

aws emr add-steps \
  --cluster-id $CLUSTER_ID \
  --steps Type=Spark,Name="My program",ActionOnFailure=CONTINUE,Args=[--class,covid19.Main,$MAIN_JAR] \
  --profile $PROFILE
