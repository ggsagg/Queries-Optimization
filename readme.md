# Querys Optimization

## Using Apache Spark and AWS to measure and observe different querys optimizations using Covid19 DATA

This project studies different querys under a Covid19 dataset as a case study to observe different optimizations and architectures in Apache Spark.

## Project Structure

### AWS

SBT Project and Shell script to be deployed on an AWS EMR Cluster

### Datasets

Under this folder are allocated the diverse datasets (csv files) which will be used to obtain the querys

### Notebooks

Jupyter Notebooks with the different querys and their optimizations. The standard use case will be the query in spark.RDD, in spark.Dataset and in spark.Dataframe.
Finally the execution times will be shown in a plotly chart bar.

### Parquet Files

Data stored and partitioned in parquet format

## USAGE

1. To start this project you can either download and run the sbt project located in /AWS with the data from /Datasets. Here you will find a clean execution with execution times shown in stdout.

2. To have a closer look to the project download and run each of the notebooks.

3. To deploy on AWS create an EMR Cluster, upload the data and the .jar file from the sbt project to S3, and use the runpipeline.sh script modifying it with your account and cluster information.
