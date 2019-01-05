package com.atguigu.statistics

import java.text.SimpleDateFormat
import java.util.Date

import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession


/**
  * Movie数据集，数据集字段通过分割
  *
  * 151^                          电影的ID
  * Rob Roy (1995)^               电影的名称
  * In the highlands ....^        电影的描述
  * 139 minutes^                  电影的时长
  * August 26, 1997^              电影的发行日期
  * 1995^                         电影的拍摄日期
  * English ^                     电影的语言
  * Action|Drama|Romance|War ^    电影的类型
  * Liam Neeson|Jessica Lange...  电影的演员
  * Michael Caton-Jones           电影的导演
  *
  * tag1|tag2|tag3|....           电影的Tag
  **/

case class Movie(val mid: Int, val name: String, val descri: String, val timelong: String, val issue: String,
                 val shoot: String, val language: String, val genres: String, val actors: String, val directors: String)

/**
  * Rating数据集，用户对于电影的评分数据集，用，分割
  *
  * 1,           用户的ID
  * 31,          电影的ID
  * 2.5,         用户对于电影的评分
  * 1260759144   用户对于电影评分的时间
  */
case class Rating(val uid: Int, val mid: Int, val score: Double, val timestamp: Int)

/**
  * MongoDB的连接配置
  * @param uri   MongoDB的连接
  * @param db    MongoDB要操作数据库
  */
case class MongoConfig(val uri:String, val db:String)

/**
  * 推荐对象
  * @param rid   推荐的Movie的mid
  * @param r     Movie的评分
  */
case class Recommendation(rid:Int, r:Double)

/**
  * 电影类别的推荐
  * @param genres   电影的类别
  * @param recs     top10的电影的集合
  */
case class GenresRecommendation(genres:String, recs:Seq[Recommendation])


object StatisticsRecommender {

  val MONGODB_RATING_COLLECTION = "Rating"
  val MONGODB_MOVIE_COLLECTION = "Movie"

  //统计的表的名称
  val RATE_MORE_MOVIES = "RateMoreMovies"
  val RATE_MORE_RECENTLY_MOVIES = "RateMoreRecentlyMovies"
  val AVERAGE_MOVIES = "AverageMovies"
  val GENRES_TOP_MOVIES = "GenresTopMovies"

  // 入口方法
  def main(args: Array[String]): Unit = {

    val config = Map(
      "spark.cores" -> "local[*]",
      "mongo.uri" -> "mongodb://linux:27017/recommender",
      "mongo.db" -> "recommender"
    )

    //创建SparkConf配置
    val sparkConf = new SparkConf().setAppName("StatisticsRecommender").setMaster(config("spark.cores"))

    //创建SparkSession
    val spark = SparkSession.builder().config(sparkConf).getOrCreate()

    val mongoConfig = MongoConfig(config("mongo.uri"),config("mongo.db"))

    //加入隐式转换
    import spark.implicits._

    //数据加载进来
    val ratingDF = spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_RATING_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Rating]
      .toDF()

    val movieDF = spark
      .read
      .option("uri",mongoConfig.uri)
      .option("collection",MONGODB_MOVIE_COLLECTION)
      .format("com.mongodb.spark.sql")
      .load()
      .as[Movie]
      .toDF()

    //创建一张名叫ratings的表
    ratingDF.createOrReplaceTempView("ratings")

    //统计所有历史数据中每个电影的评分数
    //数据结构 -》  mid,count

    val rateMoreMoviesDF = spark.sql("select mid, count(mid) as count from ratings group by mid")

    rateMoreMoviesDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",RATE_MORE_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //统计以月为单位拟每个电影的评分数
    //数据结构 -》 mid,count,time

    //创建一个日期格式化工具
    val simpleDateFormat = new SimpleDateFormat("yyyyMM")

    //注册一个UDF函数，用于将timestamp装换成年月格式   1260759144000  => 201605
    spark.udf.register("changeDate",(x:Int) => simpleDateFormat.format(new Date(x * 1000L)).toInt)

    // 将原来的Rating数据集中的时间转换成年月的格式
    val ratingOfYeahMouth = spark.sql("select mid, score, changeDate(timestamp) as yeahmouth from ratings")

    // 将新的数据集注册成为一张表
    ratingOfYeahMouth.createOrReplaceTempView("ratingOfMouth")

    val rateMoreRecentlyMovies = spark.sql("select mid, count(mid) as count ,yeahmouth from ratingOfMouth group by yeahmouth,mid")

    rateMoreRecentlyMovies
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",RATE_MORE_RECENTLY_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //统计每个电影的平均评分
    val averageMoviesDF = spark.sql("select mid, avg(score) as avg from ratings group by mid")

    averageMoviesDF
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",AVERAGE_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //统计每种电影类型中评分最高的10个电影
       //需要用left join，应为只需要有评分的电影数据集
    val movieWithScore = movieDF.join(averageMoviesDF,Seq("mid","mid"))

    //所有的电影类别
    val genres = List("Action","Adventure","Animation","Comedy","Ccrime","Documentary","Drama","Family","Fantasy","Foreign","History","Horror","Music","Mystery"
      ,"Romance","Science","Tv","Thriller","War","Western")

    //将电影类别转换成RDD
    val genresRDD = spark.sparkContext.makeRDD(genres)


    //!!!!! 通过explode来实现
    /*import org.apache.spark.sql.functions._

    spark.udf.register("splitGe", (x:String) => x.split("\\|"))
    val singleGenresMovie = movieWithScore.select($"mid", $"avg", explode(callUDF("splitGe",$"genres")).as("gen"))
    singleGenresMovie.show
    val genrenTopMovies = singleGenresMovie.rdd
      .map(row => (row.getAs[String]("gen"), (row.getAs[Int]("mid"), row.getAs[Double]("avg"))))
      .groupByKey()
      .map{
        case (genres, items) => GenresRecommendation(genres,items.toList.sortWith(_._2 > _._2).take(10).map(item => Recommendation(item._1,item._2)))
      }.toDF()*/

    //计算电影类别top10
    val genrenTopMovies = genresRDD.cartesian(movieWithScore.rdd)  //将电影类别和电影数据进行笛卡尔积操作
      .filter{
        // 过滤掉电影的类别不匹配的电影
        case (genres,row) => row.getAs[String]("genres").toLowerCase.contains(genres.toLowerCase)
      }
      .map{
        // 将整个数据集的数据量减小，生成RDD[String,Iter[mid,avg]]
        case (genres,row) => {
          (genres,(row.getAs[Int]("mid"), row.getAs[Double]("avg")))
        }
      }.groupByKey()   //将genres数据集中的相同的聚集
      .map{
        // 通过评分的大小进行数据的排序，然后将数据映射为对象
        case (genres, items) => GenresRecommendation(genres,items.toList.sortWith(_._2 > _._2).take(10).map(item => Recommendation(item._1,item._2)))
      }.toDF()

    // 输出数据到MongoDB
    genrenTopMovies
      .write
      .option("uri",mongoConfig.uri)
      .option("collection",GENRES_TOP_MOVIES)
      .mode("overwrite")
      .format("com.mongodb.spark.sql")
      .save()

    //关闭Spark
    spark.stop()
  }

}
