import $ivy.`org.apache.spark::spark-sql:2.4.5` 

import org.apache.spark.sql.{NotebookSparkSession, SparkSession}

val spark: SparkSession = 
    NotebookSparkSession
      .builder()
      .appName("Queries Optimization")
      .master("local[*]")
      .getOrCreate()


import $ivy.`org.plotly-scala::plotly-almond:0.8.1`

import plotly._
import plotly.element._
import plotly.layout._
import plotly.Almond._

import $ivy.`ch.cern.sparkmeasure:spark-measure_2.12:0.17`

import org.slf4j.LoggerFactory
import org.apache.log4j.{Level, Logger}
Logger.getRootLogger().setLevel(Level.ERROR)

import spark.implicits._
import spark.sqlContext.implicits._
import org.apache.spark.sql._
import org.apache.spark.sql.{functions => func, _}
import org.apache.spark.sql.types._
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{IntegerType, StringType, StructType}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark._
import org.apache.spark.sql.types._, func._

org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this)
case class Infection(day : Int, 
                     month : Int, 
                     year : Int, 
                     nCases: Int, 
                     nDeaths : Int, 
                     country : String,  
                     continent : String) 
extends Serializable

def run[A](code: => A): A = {
    val start = System.currentTimeMillis()
    val res = code
    println(s"Took ${System.currentTimeMillis() - start}")
    res
}

def runWithOutput[A](code: => A): Int = {
    val start = System.currentTimeMillis()
    val res = code
    val out = System.currentTimeMillis() - start
    println(s"Took ${System.currentTimeMillis() - start}")
    out.toInt
}

// Credit to Aivean
implicit class RichDF(val ds:DataFrame) {
    def showHTML(limit:Int = 20, truncate: Int = 20) = {
        import xml.Utility.escape
        val data = ds.take(limit)
        val header = ds.schema.fieldNames.toSeq        
        val rows: Seq[Seq[String]] = data.map { row =>
          row.toSeq.map { cell =>
            val str = cell match {
              case null => "null"
              case binary: Array[Byte] => binary.map("%02X".format(_)).mkString("[", " ", "]")
              case array: Array[_] => array.mkString("[", ", ", "]")
              case seq: Seq[_] => seq.mkString("[", ", ", "]")
              case _ => cell.toString
            }
            if (truncate > 0 && str.length > truncate) {
              // do not show ellipses for strings shorter than 4 characters.
              if (truncate < 4) str.substring(0, truncate)
              else str.substring(0, truncate - 3) + "..."
            } else {
              str
            }
          }: Seq[String]
        }
publish.html(s""" <table>
                <tr>
                 ${header.map(h => s"<th>${escape(h)}</th>").mkString}
                </tr>
                ${rows.map { row =>
                  s"<tr>${row.map{c => s"<td>${escape(c)}</td>" }.mkString}</tr>"
                }.mkString}
            </table>
        """)        
    }
}

val infectionData = spark.sparkContext.textFile("data.csv")

def infections(lines : RDD[String]) : RDD[Infection] =
    lines.map(line => {
      val arr = line.split(",")
      Infection(
        day = arr(1).toInt,
        month = arr(2).toInt,
        year = arr(3).toInt,
        nCases = arr(4).toInt,
        nDeaths = arr(5).toInt,
        country = arr(6),
        continent = arr(10)
      )
    })

  def infectionGrowthAverage(infections : RDD[Infection]) : RDD[(String, Int)]= {

    val countriesAndCases : RDD[(String, Iterable[Int])] = 
      infections.map(x => (x.country,x.nCases))
      .groupByKey()
      
    countriesAndCases.mapValues(x => (x.sum / x.size)).sortBy(_._2)
  }

val infectionRDD = infections(infectionData)
val infectionAvgRDD = infectionGrowthAverage(infectionRDD)

val timeRDD = spark.time(infectionAvgRDD.collect())

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(infectionAvgRDD.collect())

val infectionDF = spark.createDataFrame(infectionRDD)

val infAvgOrDf = infectionDF.
    groupBy("country")
    .avg("nCases")
    .orderBy(desc("avg(nCases)"))

infAvgOrDf.showHTML()

spark.time(infAvgOrDf.count())

val timeDF = spark.time(infAvgOrDf.collect)

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(infAvgOrDf.collect)

val dfCovid = spark.read
.option("header", "true")
.option("charset", "UTF8")
.option("delimiter",",")
.option("inferSchema", "true")
.csv("covidworldwide.csv")

dfCovid.schema

dfCovid.explain

run(dfCovid.toDF.groupBy("countriesAndTerritories")
    .agg(mean("cases")).orderBy("avg(cases)")).show(500)

//Defino el esquema manualmente pero podría verlo importando el csv y viendo como lo hace de base spark

val schema = new StructType()
    .add("dateRep",StringType,true)
    .add("day",IntegerType,true)
    .add("month",IntegerType,true)
    .add("year",IntegerType,true)
    .add("cases",IntegerType,true)
    .add("deaths",IntegerType,true)
    .add("countriesAndTerritories",StringType,true)
    .add("geoId",StringType,true)
    .add("countryterritoryCode",StringType,true)
    .add("popData2018",IntegerType,true)
    .add("continentExp",StringType,true)

val df = spark.read
.format("csv")
.option("header","true")
.schema(schema)
.load("data.csv")

df.printSchema()

df.show()

val infectionDS = spark.read
.option("header", "true")
.option("charset", "UTF8")
.option("delimiter",",")
.csv("covidworldwide.csv")
.as[(String,String,String,String,String,String,String,String,String,String,String,String)]

val avgDS = 
    infectionDS.groupBy($"countriesAndTerritories")
    .agg(avg($"cases").as[Double])
    .orderBy("avg(cases)")

spark.time(avgDS.count)

val timeDS = spark.time(avgDS.collect)

val timeDataSet = runWithOutput(avgDS.collect)

val infectionDataset = spark.createDataset(infectionRDD)

infectionDataset
    .groupBy($"country")
    .agg(avg($"nCases").as[Double])
    .orderBy("avg(nCases)")

val populationData = spark.sparkContext.textFile("population_by_country_2020.csv")

org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this)
case class Population(
    country : String, 
    population : Int, 
    density : Int, 
    land_area: Int, 
    ) 
extends Serializable

val header = populationData.first() 

def population(lines : RDD[String]) : RDD[Population] =
    lines.filter(x => x != header)
    .map(line => {
      val arr = line.split(",")
      Population(
        country = arr(0),
        population = arr(1).toInt,
        density = arr(4).toInt,
        land_area = arr(5).toInt,
      )
    })

val populationRDD = population(populationData)
populationRDD.toDF.showHTML()

populationRDD.join(infectionRDD)

val populationByCountry = populationRDD.map(
    x => (x.country,x))

val infectionByCountry = 
      infectionRDD.map(x => (x.country,x))

val megaRDD = infectionByCountry.join(populationByCountry).groupByKey()

megaRDD.mapValues(
    x => x.map( 
        line => line._1.nCases.toFloat / line._2.land_area.toFloat
    )).mapValues(
    x => x.sum / x.size
).collect()

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(
    infectionByCountry.join(populationByCountry)
    .groupByKey()
    .mapValues(
    x => x.map( 
        line => line._1.nCases.toFloat / line._2.land_area.toFloat)
    ).mapValues(
        x => x.sum / x.size
    ).collect()
)

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(
    populationByCountry.join(infectionByCountry)
    .groupByKey()
    .mapValues(
    x => x.map( 
        line => line._1.land_area.toFloat / line._2.nCases.toFloat)
    ).mapValues(
        x => x.sum / x.size
    ).collect()
)

val countriesAndLandArea = populationRDD.map(
    x => (x.country,x.land_area))

val countriesAndCases = 
      infectionRDD.map(x => (x.country,x.nCases))
      .groupByKey()

val average = countriesAndCases.join(countriesAndLandArea)

average.mapValues(
    x => x._1.map(
        y => (y.toFloat / x._2.toFloat)
    )).mapValues(
    x => x.sum/x.size
).collect()

val querie1 =
countriesAndCases.join(countriesAndLandArea)   
.mapValues(
    x => x._1.map(
        y => (y.toFloat / x._2.toFloat)
    )).mapValues(
    x => x.sum / x.size
)

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(
countriesAndCases.join(countriesAndLandArea)   
.mapValues(
    x => x._1.map(
        y => (y.toFloat / x._2.toFloat)
    )).mapValues(
    x => x.sum / x.size
).collect())

val infectionDS = spark.read
.option("header", "true")
.option("charset", "UTF8")
.option("delimiter",",")
.option("inferSchema", "true")
.csv("covidworldwide.csv")
.withColumnRenamed("countriesAndTerritories","Country")
.as[(String,String,String,String,Double,Double,String,String,String,String,String,String)]

val dsPopulation = spark.read
.option("header", "true")
.option("charset", "UTF8")
.option("delimiter",",")
.option("inferSchema", "true")
.csv("population_by_country_2020.csv")
.withColumnRenamed("Country (or dependency)","Country")
.withColumnRenamed("Population (2020)","Population")
.as[(String,Float,String,Float,Float,Float,Double,String,String,String,String)]

val querie2_1 = 
infectionDS.join(dsPopulation, "Country")
        .select($"Country",
                $"dateRep" as "date",
                $"cases",
                $"Land Area (Km\u00b2)",
                $"cases" / $"Land Area (Km\u00b2)" as "infection Per Km\u00b2")
        .groupBy("Country")
        .agg(round(avg("infection Per Km\u00b2"),10).as[Float])
        .orderBy(desc("round(avg(infection Per Km²), 10)"))
        .collect()

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(
infectionDS.join(dsPopulation, "Country")
        .select($"Country",
                $"dateRep" as "date",
                $"cases",
                $"Land Area (Km\u00b2)",
                $"cases" / $"Land Area (Km\u00b2)" as "infection Per Km\u00b2")
        .groupBy("Country")
        .agg(round(avg("infection Per Km\u00b2"),10).as[Float])
        .orderBy(desc("round(avg(infection Per Km²), 10)"))
        .collect())

val querie2 = 
infectionDS.join(dsPopulation, "Country")
        .select($"Country",
                $"dateRep" as "date",
                $"cases",
                $"Land Area (Km\u00b2)",
               $"cases" / $"Population" as "infection Per Population")
        .groupBy("country")
        .avg("infection Per Population")
        .orderBy(desc("avg(infection Per Population)"))

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(querie2.collect)

val dfCovid = spark.read
.option("header", "true")
.option("charset", "UTF8")
.option("delimiter",",")
.option("inferSchema", "true")
.csv("covidworldwide.csv")

val dfMeasures = spark.read
.option("header", "true")
.option("charset", "UTF8")
.option("delimiter",",")
.option("inferSchema", "true")
.csv("response_graphs_data_2021-04-15.csv")
dfMeasures.show
dfMeasures.schema

val dfPopulation = spark.read
.option("header", "true")
.option("charset", "UTF8")
.option("delimiter",",")
.option("inferSchema", "true")
.csv("population_by_country_2020.csv")
.withColumnRenamed("Country (or dependency)","Country")
.withColumnRenamed("Population (2020)","Population")
dfPopulation.showHTML()
dfPopulation.schema

val dfCovidClean = dfCovid.select($"*",$"dateRep",translate($"dateRep","/","-").as("new-date")).drop("dateRep").show()

val spainCovid = dfCovid.select("dateRep","cases").where("countriesAndTerritories == 'Spain'").toDF

run(spainCovid.agg(avg("cases"))).show

val megaDF = dfCovid.join(dfMeasures, $"Country" === $"countriesAndTerritories")

megaDF.select("cases","deaths","dateRep","Response_measure")
    .where("countriesAndTerritories == 'Spain'").show

run(dfCovid.join(dfMeasures, $"Country" === $"countriesAndTerritories")
        .select("cases","deaths","dateRep","Response_measure")
        .where("countriesAndTerritories == 'Spain'").collect())

val querie3 = 
dfCovid.join(dfPopulation, $"country" === $"countriesAndTerritories")
        .select($"country",
                $"dateRep" as "date",
                $"cases",
                $"Land Area (Km\u00b2)",
                $"cases" / $"Land Area (Km\u00b2)" as "infection Per Km\u00b2")
        .groupBy("country")
        .avg("infection Per Km\u00b2")
        .orderBy(desc("avg(infection Per Km²)"))

ch.cern.sparkmeasure.StageMetrics(spark).runAndMeasure(
dfCovid.join(dfPopulation, $"country" === $"countriesAndTerritories")
        .select($"country",
                $"dateRep" as "date",
                $"cases",
                $"Land Area (Km\u00b2)",
                $"cases" / $"Land Area (Km\u00b2)" as "infection Per Km\u00b2")
        .groupBy("country")
        .avg("infection Per Km\u00b2")
        .orderBy(desc("avg(infection Per Km²)"))
        .collect()
    )

val infectionsPerPopulation = dfCovid.join(dfPopulation, $"country" === $"countriesAndTerritories")
        .select($"country",
                $"dateRep" as "date",
                $"cases",
                $"Population",
                $"cases" / $"Population" as "infection Per Population")
        .groupBy("country")
        .avg("infection Per Population")
        .orderBy(desc("avg(infection Per Population)"))
        .collect

val (x, y) = Seq(
  "Banana" -> 10,
  "Apple" -> 8,
  "Grapefruit" -> 5
).unzip

Bar(x, y).plot()

val (x,y) = infAvgOrDf.collect.map(r=>(r(0).toString, r(1).toString.toDouble)).toList.unzip
Bar(x, y).plot()

val (x,y) = querie3.map(r=>(r(0).toString, r(1).toString.toFloat)).toList.unzip
Bar(x, y).plot()

val (x,y) = infectionsPerPopulation.map(r=>(r(0).toString, r(1).toString)).toList.unzip
Bar(x, y).plot()

val (x, y) = Seq(
  "RDD" -> runWithOutput(infectionAvgRDD.collect),
  "DataSet" -> runWithOutput(infAvgOrDf.collect),
  "DataFrame" -> runWithOutput(avgDS.collect)
).unzip

Bar(x, y).plot()

val (x, y) = Seq(
  "RDD" -> runWithOutput(querie1.collect),
  "DataSet" -> runWithOutput(querie2.collect),
  "DataFrame" -> runWithOutput(querie3.collect)
).unzip

Bar(x, y).plot()
