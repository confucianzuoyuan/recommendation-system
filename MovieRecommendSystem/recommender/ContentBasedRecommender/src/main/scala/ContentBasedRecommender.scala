package com.atguigu.contentbased

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.ml.feature.{HashingTF, IDF, Tokenizer}
import org.apache.spark.ml.linalg.{SparseVector, DenseVector}
import org.apache.spark.sql.functions.udf
import org.jblas.DoubleMatrix
import scala.collection.mutable.WrappedArray

/**
  * MongoDB的连接配置
  * @param uri   MongoDB的连接
  * @param db    MongoDB要操作数据库
  */
case class MongoConfig(val uri:String, val db:String)

case class Movie(val mid: Int, val name: String, val descri: String, val timelong: String, val issue: String,
                 val shoot: String, val language: String, val genres: String, val actors: String, val directors: String)

//推荐
case class Recommendation(rid:Int, r:Double)

// 用户的推荐
case class UserRecs(uid:Int, recs:Seq[Recommendation])

//电影的相似度
case class MovieRecs(mid:Int, recs:Seq[Recommendation])

object ContentBasedRecommender {
  val MONGODB_MOVIE_COLLECTION = "Movie"
  val MOVIE_RECS = "ContentBasedMovieRecs"

  def consinSim(movie1: DoubleMatrix, movie2:DoubleMatrix) : Double ={
    movie1.dot(movie2) / ( movie1.norm2()  * movie2.norm2() )
  }


  def main(args: Array[String]): Unit = {

    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://localhost:27017/recommender",
      "mongo.db" -> "reommender"
    )

    //创建一个SparkConf配置
    val sparkConf = new SparkConf().setAppName("ContentBasedRecommender").setMaster(config("spark.cores")).set("spark.executor.memory","6G").set("spark.driver.memory","2G")

    //基于SparkConf创建一个SparkSession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    //创建一个MongoDBConfig
    val mongoConfig = MongoConfig(config("mongo.uri"),config("mongo.db"))

    import spark.implicits._

    //电影数据集 RDD[Int]
    val movieRDD = spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]
      .rdd
      .map(x => (x.mid, x.name, x.genres.map(c => if(c == '|') ' ' else c)))

    val movieSeq = movieRDD.collect()

    val tagsData = spark.createDataFrame(movieSeq).toDF("mid", "name", "genres")

    val tokenizer = new Tokenizer().setInputCol("genres").setOutputCol("words")

    val wordsData = tokenizer.transform(tagsData)

    val hashingTF = new HashingTF().setInputCol("words").setOutputCol("rawFeatures").setNumFeatures(20)

    val featurizedData = hashingTF.transform(wordsData)

    val idf = new IDF().setInputCol("rawFeatures").setOutputCol("features")

    val idfModel = idf.fit(featurizedData)

    val rescaledData = idfModel.transform(featurizedData)

    val toArr: Any => String = _.asInstanceOf[SparseVector].toArray.mkString(",")

    val toArrUdf = udf(toArr)

    val new_rescaledData = rescaledData.withColumn("new_features", toArrUdf(rescaledData("features")))

    val movieFeatures = new_rescaledData
      .select("new_features", "mid")
      .rdd
      .map(x => {
        (x(1).toString.toInt, new DoubleMatrix(x(0).toString.split(",").map(_.toDouble)))
      })

    val movieRecs = movieFeatures.cartesian(movieFeatures)
      .filter{case (a,b) => a._1 != b._1}
      .map{case (a,b) =>
        val simScore = this.consinSim(a._2,b._2)
        (a._1,(b._1,simScore))
      }.filter(_._2._2 > 0.6)
      .groupByKey()
      .map{case (mid,items) =>
        MovieRecs(mid,items.toList.map(x => Recommendation(x._1,x._2)))
      }.toDF()

    movieRecs.show(5)

    movieRecs
      .write
      .option("uri", mongoConfig.uri)
      .option("collection",MOVIE_RECS)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

//    rescaledData.select("features", "mid").take(3).foreach(println)



    //关闭Spark
    spark.close()
  }
}
