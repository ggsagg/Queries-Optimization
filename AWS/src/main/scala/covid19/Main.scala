package covid19

import org.apache.log4j.{Level, Logger}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions._
object Main extends App {

  val spark =
    SparkSession
      .builder()
      .appName("Queries Optimization")
      .master("local[*]")
      .getOrCreate()
  import spark.implicits._

  Logger.getRootLogger().setLevel(Level.ERROR)

  org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this)
  case class Infection(day: Int,
                       month: Int,
                       year: Int,
                       nCases: Int,
                       nDeaths: Int,
                       country: String,
                       continent: String)
    extends Serializable

  def runWithOutput[A](code: => A): Int = {
    val start = System.currentTimeMillis()
    val res = code
    val out = System.currentTimeMillis() - start
    println(s"Took ${System.currentTimeMillis() - start}")
    out.toInt
  }
//to load data from AWS S3 = s3://spark-aws-tfg/datasets/data.csv
  val infectionData = spark.sparkContext.textFile("s3://spark-aws-tfg/datasets/data.csv")

  def infections(lines: RDD[String]): RDD[Infection] =
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

  def infectionGrowthAverage(infections: RDD[Infection]): RDD[(String, Int)] = {

    val countriesAndCases: RDD[(String, Iterable[Int])] =
      infections.map(x => (x.country, x.nCases))
        .groupByKey()

    countriesAndCases.mapValues(x => (x.sum / x.size)).sortBy(_._2)
  }

  def infectionRDD = infections(infectionData)
  def infectionAvgRDD = infectionGrowthAverage(infectionRDD)


  def dfCovid2 = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter", ",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/covidworldwide.csv")

  def dfCovidWithSchema = dfCovid2.toDF
    .groupBy("countriesAndTerritories")
    .agg(mean("cases"))
    .orderBy("avg(cases)")

  org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this)
  def infectionDS = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter", ",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/covidworldwide.csv")
    .withColumnRenamed("countriesAndTerritories", "Country")
    .as[(String, String, String, String, Double, Double, String, String, String, String, String, String)]

  def avgDS =
    infectionDS.groupBy($"Country")
      .agg(avg($"cases").as[Double])
      .orderBy("avg(cases)")


  val populationData = spark.sparkContext.textFile("s3://spark-aws-tfg/datasets/population_by_country_2020.csv")

  org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this)

  case class Population(country: String,
                         population: Int,
                         density: Int,
                         land_area: Int) extends Serializable

//  val header = populationData.first()

  def population(lines: RDD[String]): RDD[Population] = {
    val h = lines.first()
    lines.filter(x => x != h)
      .map(line => {
        val arr = line.split(",")
        Population(
          country = arr(0),
          population = arr(1).toInt,
          density = arr(4).toInt,
          land_area = arr(5).toInt)})
  }

  def populationRDD = population(populationData)

  //RDD sin optimizar querie 2

 // populationRDD.join(infectionRDD)

  def populationByCountry = populationRDD.map(
    x => (x.country, x))

  def infectionByCountry =
    infectionRDD.map(x => (x.country, x))

  def joinedRDD = infectionByCountry.join(populationByCountry).groupByKey()

  def finalRDD = joinedRDD.mapValues(
    x => x.map(
      line => line._1.nCases.toFloat / line._2.land_area.toFloat
    )).mapValues(
    x => x.sum / x.size
  )

  def notOptimizedRDD =
    infectionByCountry.join(populationByCountry)
      .groupByKey()
      .mapValues(
        x => x.map(
          line => line._1.nCases.toFloat / line._2.land_area.toFloat)
      ).mapValues(
      x => x.sum / x.size
    )

  //Optimizando el RDD

  def countriesAndLandArea = populationRDD.map(
    x => (x.country, x.land_area))

  def countriesAndCases =
    infectionRDD.map(x => (x.country, x.nCases))
      .groupByKey()

  def meanInfectionsRDD =
    countriesAndCases.join(countriesAndLandArea)
      .mapValues(
        x => x._1.map(
          y => (y.toDouble / x._2.toDouble)
        )).mapValues(
      x => x.sum / x.size
    )

  //CONSULTA CON DS

  def dsPopulation = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter", ",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/population_by_country_2020.csv")
    .withColumnRenamed("Country (or dependency)", "Country")
    .withColumnRenamed("Population (2020)", "Population")
    .as[(String, Float, String, Float, Float, Float, Double, String, String, String, String)]

  def meanInfectionsperKM2DS =
    infectionDS.join(dsPopulation, "Country")
      .select($"Country",
        $"dateRep" as "date",
        $"cases",
        $"Land Area (Km\u00b2)",
        $"cases" / $"Land Area (Km\u00b2)" as "infection Per Km\u00b2")
      .groupBy("Country")
      .agg(round(avg("infection Per Km\u00b2"),10).as[Float])
      .orderBy(desc("round(avg(infection Per Km²), 10)"))
      .as[(String,Double)]


  //CONSULTA CON DF
  def dfCovid = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter", ",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/covidworldwide.csv")


  def dfPopulation = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter", ",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/population_by_country_2020.csv")
    .withColumnRenamed("Country (or dependency)", "Country")
    .withColumnRenamed("Population (2020)", "Population")

  def dfCovidClean = dfCovid
    .select($"*",$"dateRep",translate($"dateRep","/","-").as("date"))
    .drop("dateRep")

  def dfCovidDate = dfCovidClean
    .select($"*",col("date"),to_date(col("date"),"dd-MM-yyyy").as("to_date"))

  def meanInfectionsperKM2DF =
    dfCovid.join(dfPopulation, $"country" === $"countriesAndTerritories")
      .select($"country",
        $"dateRep" as "date",
        $"cases",
        $"Land Area (Km\u00b2)",
        $"cases" / $"Land Area (Km\u00b2)" as "infection Per Km\u00b2")
      .groupBy("country")
      .avg("infection Per Km\u00b2")
      .orderBy(desc("avg(infection Per Km²)"))

  // CONSULTA 3
 //  consulta con dataset
  def meanInfectionPerPopulationDS =
    infectionDS.join(dsPopulation, "Country")
      .select($"Country",
        $"dateRep" as "date",
        $"cases",
        $"Land Area (Km\u00b2)",
        $"cases" / $"Population" as "infection Per Population")
      .groupBy("country")
      .avg("infection Per Population")
      .orderBy(desc("avg(infection Per Population)"))
      .as[(String,Double)]

  def diaryInfectionPerPopulationDS =
    infectionDS.join(dsPopulation, "Country")
      .select($"Country",
        $"dateRep" as "date",
        $"cases",
        $"Land Area (Km\u00b2)",
        $"cases" / $"Population" as "infection Per Population")
      .orderBy($"date")
      .as[(String,String,Int,String,Double)]

  //consulta con dataframe

  def infectionsPerPopulation = dfCovid.join(dfPopulation, $"country" === $"countriesAndTerritories")
    .select($"country",
      $"dateRep" as "date",
      $"cases",
      $"Population",
      $"cases" / $"Population" as "infection Per Population")
    .groupBy("country")
    .avg("infection Per Population")
    .orderBy(desc("avg(infection Per Population)"))

  def diaryInfectionsDF =
    dfCovidDate.join(dfPopulation, $"country" === $"countriesAndTerritories")
      .select($"country",
        $"to_date",
        $"day",
        $"month",
        $"cases",
        $"Population",
        $"cases" / $"Population" as "infection Per Population")
      .orderBy($"to_date".asc)

  //QUERIE 5 : TASA DE INFECCIONES FRENTE A VACUNACIONES

  def newInfectionDS = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter",",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/covid_19_data.csv")
    .as[(Int,String,String,String,String,Double,Double,Double)]

  def vaccinationsDS = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter",",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/country_vaccinations.csv")
    .as[(String,String,java.sql.Timestamp,Double,Double,Double,Double,Double,Double,Double,Double,Double,String,String,String)]

  val vaccinationsClean = vaccinationsDS
    .select($"*",col("date"),to_date(col("date"),"MM-dd-yyyy")
      .as("dateVaccinated"))
    .drop("date")

  val dateInfectionsDS = newInfectionDS
    .select($"*",$"ObservationDate",translate($"ObservationDate","/","-")
      .as("date1"))
    .drop("ObservationDate")
    .select($"*",col("date1"),to_date(col("date1"),"MM-dd-yyyy")
      .as("date"))
    .drop("date1")
    .as[(Int,String,String,String,Double,Double,Double,java.sql.Timestamp)]

  def megaDS = dateInfectionsDS.join(
    vaccinationsClean,$"date" === $"dateVaccinated"
      && dateInfectionsDS("Country/Region") <=> vaccinationsClean("country"))
    .join(dsPopulation,"Country")
    .select($"country",
      $"date",
      $"confirmed",
      $"people_vaccinated",
      $"Population",
      $"confirmed" / $"Population" as "infection Per Population",
      $"people_vaccinated"/ $"Population" as "vaccination Per Population",
      $"people_vaccinated" / $"confirmed" as "infection-vaccination rate")
    .orderBy($"date".asc)
    .na.fill(0.0)
    .withColumn("infection-vaccination rate", round($"infection-vaccination rate",8))
    .withColumn("vaccination Per Population", round($"vaccination Per Population",8))
    .as[(String,java.sql.Timestamp,Double,Double,Int,Double,Double,Double)]

  def infectionsDF = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter",",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/covid_19_data.csv")

  def vaccinationsDF = spark.read
    .option("header", "true")
    .option("charset", "UTF8")
    .option("delimiter",",")
    .option("inferSchema", "true")
    .csv("s3://spark-aws-tfg/datasets/country_vaccinations.csv")

  val vaccinationsCleanDF = vaccinationsDF
    .select($"*",col("date"),to_date(col("date"),"MM-dd-yyyy")
      .as("dateVaccinated"))
    .drop("date")

  val dateInfectionsDF = infectionsDF
    .select($"*",$"ObservationDate",translate($"ObservationDate","/","-")
      .as("date1"))
    .drop("ObservationDate")
    .select($"*",col("date1"),to_date(col("date1"),"MM-dd-yyyy")
      .as("date"))
    .drop("date1")

  def megaDF = dateInfectionsDF.join(
    vaccinationsCleanDF,$"date" === $"dateVaccinated"
      && dateInfectionsDF("Country/Region") <=> vaccinationsCleanDF("country"))
    .join(dfPopulation,"Country")
    .select($"country",
      $"date",
      $"confirmed",
      $"people_vaccinated",
      $"Population",
      $"confirmed" / $"Population" as "infection Per Population",
      $"people_vaccinated"/ $"Population" as "vaccination Per Population",
      $"people_vaccinated" / $"confirmed" as "infection-vaccination rate")
    .orderBy($"date".asc)
    .na.fill(0.0)
    .withColumn("infection-vaccination rate", round($"infection-vaccination rate",8))
    .withColumn("vaccination Per Population", round($"vaccination Per Population",8))

//PARQUET FILES
  val parqDF = spark.read.parquet("s3://spark-aws-tfg/parquet_files/covid_countries.parquet")
  val parqPopDF = spark.read.parquet("s3://spark-aws-tfg/parquet_files/covid_population.parquet")

  val csvPopulation = dfPopulation
    .withColumnRenamed("Density (P/Km²)","Density")
    .withColumnRenamed("Land Area (Km²)","Area")
    .withColumnRenamed("Migrants (net)", "Migrants")
    .withColumnRenamed("Fert. Rate", "Fertility")
    .withColumnRenamed("Med. Age","Med_age")
    .withColumnRenamed("Urban Pop %","urban_population")
    .withColumnRenamed("World Share","World_share")
    .withColumnRenamed("Country (or dependency)","Country")
    .withColumnRenamed("Population (2020)","Population")

  def parqMeanDF = parqDF.toDF
    .where("countriesAndTerritories == 'Spain'")
    .agg(mean("cases"))
    .orderBy("avg(cases)")

  def csvMeanDF = dfCovid.toDF
    .where("countriesAndTerritories == 'Spain'")
    .agg(mean("cases"))
    .orderBy("avg(cases)")

  def csvCasesKM2 =
    dfCovid.join(csvPopulation, $"country" === $"countriesAndTerritories")
      .where("countriesAndTerritories == 'Spain'")
      .select($"country",
        $"dateRep" as "date",
        $"cases",
        $"Area",
        $"cases" / $"Area" as "infection Per Km\u00b2")
      .groupBy("country")
      .avg("infection Per Km\u00b2")
      .orderBy(desc("avg(infection Per Km²)"))

  def parquetCasesKM2 =
    parqDF.join(parqPopDF, $"country" === $"countriesAndTerritories")
      .where("countriesAndTerritories == 'Spain'")
      .select($"country",
        $"dateRep" as "date",
        $"cases",
        $"Area",
        $"cases" / $"Area" as "infection Per Km\u00b2")
      .groupBy("country")
      .avg("infection Per Km\u00b2")
      .orderBy(desc("avg(infection Per Km²)"))

  def csvCasesPopulation =
    parqDF.join(parqPopDF, $"country" === $"countriesAndTerritories")
      .where("countriesAndTerritories == 'Chile'")
      .select($"country",
        $"dateRep" as "date",
        $"cases",
        $"Population",
        $"cases" / $"Population" as "infection Per Population")
      .groupBy("country")
      .avg("infection Per Population")
      .orderBy(desc("avg(infection Per Population)"))

  def parquetCasesPopulation =
    dfCovid.join(csvPopulation, $"country" === $"countriesAndTerritories")
      .where("countriesAndTerritories == 'Chile'")
      .select($"country",
        $"dateRep" as "date",
        $"cases",
        $"Population",
        $"cases" / $"Population" as "infection Per Population")
      .groupBy("country")
      .avg("infection Per Population")
      .orderBy(desc("avg(infection Per Population)"))

  def csvDailyCasesRate =
    dfCovid.join(csvPopulation, $"country" === $"countriesAndTerritories")
      .select($"country",
        $"dateRep",
        $"day",
        $"month",
        $"cases",
        $"Population",
        $"cases" / $"Population" as "infection Per Population")
      .orderBy($"dateRep".asc)

  def parquetDailyCasesRate =
    parqDF.join(parqPopDF, $"country" === $"countriesAndTerritories")
      .select($"country",
        $"dateRep",
        $"day",
        $"month",
        $"cases",
        $"Population",
        $"cases" / $"Population" as "infection Per Population")
      .orderBy($"dateRep".asc)

  println("Querie 1 Media diaria de infecciones:")

  println("RDD Media de casos diarios")

  val timeRDD = spark.time(infectionAvgRDD.collect())
  println("DF Media de casos diarios")
  val timeDF = spark.time(dfCovidWithSchema.collect)
  println("DS Media de casos diarios")
  val timeDS = spark.time(avgDS.collect)
  println("\n")

  println("Querie 2 infecciones por Km2:")
  println("RDD no optimizado infecciones por Km2")
  spark.time(notOptimizedRDD.collect)
  println("RDD optimizado infecciones por Km2")
  spark.time(meanInfectionsRDD.collect)
  println("DS infecciones por Km2")
  spark.time(meanInfectionsperKM2DS.collect)
  println("DF infecciones por Km2")
  spark.time(meanInfectionsperKM2DF.collect)
  println("\n")

  println("Querie 3: tasa de infecciones diarias por densidad de poblacion:")

  println("DS")
  spark.time(diaryInfectionPerPopulationDS)
  println("DF")
  spark.time(diaryInfectionsDF)
  println("\n")

  println("Querie 4: media de infecciones diarias por densidad de poblacion:")

  println("DS")
  spark.time(meanInfectionPerPopulationDS)
  println("DF")
  spark.time(infectionsPerPopulation)
  println("\n")

  println("Querie 5: tasa de infecciones frente a vacunaciones:")

  println("DS")
  spark.time(megaDS.collect)
  println("DF")
  spark.time(megaDF.collect)
  println("\n")

  println("Queries con parquet:")
  println("Querie 1 Parquet Media diaria de infecciones en España")
  spark.time(parqMeanDF.collect)
  println("\n")

  println("Querie 1 CSV Media diaria de infecciones en España")
  spark.time(csvMeanDF.collect)
  println("\n")

  println("Querie 2 parquet infecciones por Km2 en España")
  spark.time(parquetCasesKM2.collect)
  println("\n")

  println("Querie 2 csv infecciones por Km2 en España")
  spark.time(csvCasesKM2.collect)
  println("\n")

  println("Querie 3 parquet casos por densidad de población en Chile")
  spark.time(parquetCasesPopulation.collect)
  println("\n")

  println("Querie 3 csv casos por densidad de población en Chile")
  spark.time(csvCasesPopulation.collect)
  println("\n")

  println("Querie 4 parquet porcentaje diario de infectados")
  spark.time(parquetCasesPopulation.collect)
  println("\n")

  println("Querie 4 csv porcentaje diario de infectados")
  spark.time(csvCasesPopulation.collect)
  println("\n")

}
