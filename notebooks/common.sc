import $ivy.`org.apache.spark::spark-sql:2.4.5` 
import $ivy.`org.plotly-scala::plotly-almond:0.8.1`
import $ivy.`ch.cern.sparkmeasure:spark-measure_2.12:0.17`

import plotly._
import plotly.element._
import plotly.layout._
import plotly.Almond._

import org.apache.spark.sql.{NotebookSparkSession, SparkSession}

val spark: SparkSession = 
    NotebookSparkSession
      .builder()
      .appName("Queries Optimization")
      .master("local[*]")
      .getOrCreate()
      
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
import org.apache.spark.sql.functions.{col, to_date}

import plotly._
import plotly.element._
import plotly.layout._
import plotly.Almond._


import org.slf4j.LoggerFactory
import org.apache.log4j.{Level, Logger}
Logger.getRootLogger().setLevel(Level.ERROR)

org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this)
case class Infection(day : Int, 
                     month : Int, 
                     year : Int, 
                     nCases: Int, 
                     nDeaths : Int, 
                     country : String,  
                     continent : String) 
extends Serializable

org.apache.spark.sql.catalyst.encoders.OuterScopes.addOuterScope(this)
class AvgCollector(val tot: Int, val cnt: Int = 1) extends Serializable {
  def combine(that: AvgCollector) = new AvgCollector(tot + that.tot, cnt + that.cnt)
  def avg = tot / cnt 
}


def runWithOutput[A](code: => A): Int = {
    val start = System.currentTimeMillis()
    val res = code
    val out = System.currentTimeMillis() - start
    println(s"Took ${System.currentTimeMillis() - start}")
    out.toInt
}

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