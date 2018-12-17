## Step 1 — Import packages, load, parse, and explore the movie and rating dataset

启动spark-shell

```shell
$ ./bin/spark-shell --driver-memory 8g
```

堆内存感觉设置至少4G，要不会OOM。

```scala
// 导包
import org.apache.spark.sql.SparkSession
import org.apache.spark.mllib.recommendation.ALS
import org.apache.spark.mllib.recommendation.MatrixFactorizationModel
import org.apache.spark.mllib.recommendation.Rating
import scala.Tuple2
import org.apache.spark.rdd.RDD

// 读取评分数据
val ratigsFile = "data/ratings.csv"

// 按照固定格式读取
val df1 = spark.read.format("com.databricks.spark.csv").option("header", true).load(ratigsFile)

// 选取四列数据：userId，movieId，rating，timestamp
val ratingsDF = df1.select(df1.col("userId"), df1.col("movieId"), df1.col("rating"), df1.col("timestamp"))

// 显示数据，false为不截断
ratingsDF.show(false)

// 电影数据
val moviesFile = "data/movies.csv"

// 按照固定格式读取
val df2 = spark.read.format("com.databricks.spark.csv").option("header", "true").load(moviesFile)

// 选取三列数据：movieId，title，genres
val moviesDF = df2.select(df2.col("movieId"), df2.col("title"), df2.col("genres"))
```

## Step 2 — Register both DataFrames as temp tables to make querying easier

将DataFrames数据注册为临时表，使得查询更容易。

```scala
// 注册评分表
ratingsDF.createOrReplaceTempView("ratings")

// 注册电影表
moviesDF.createOrReplaceTempView("movies")
```

## Step 3 — Explore and query for related statistics

做一些数据分析

```scala
// 评分数量计数
val numRatings = ratingsDF.count()

// 多少不同的用户
val numUsers = ratingsDF.select(ratingsDF.col("userId")).distinct().count()

// 电影的数量
val numMovies = ratingsDF.select(ratingsDF.col("movieId")).distinct().count()

// 打印数据
println("Got " + numRatings + " ratings from " + numUsers + " users on " + numMovies + " movies.")

// 按照电影的用户评分数量降序排序
val results = spark.sql("select movies.title, movierates.maxr, movierates.minr, movierates.cntu "

+ "from(SELECT ratings.movieId, max(ratings.rating) as maxr,"

+ "min(ratings.rating) as minr, count(distinct userId) as cntu "

+ "FROM ratings group by ratings.movieId) movierates "

+ "join movies on movierates.movieId=movies.movieId "

+ "order by movierates.cntu desc")

results.show(false)

// 最活跃用户
val mostActiveUsersSchemaRDD = spark.sql("SELECT ratings.userId, count(*) as ct from ratings " + "group by ratings.userId order by ct desc limit 10")

mostActiveUsersSchemaRDD.show(false)

// 找出userId=668且打分4分以上的电影
val results2 = spark.sql(

"SELECT ratings.userId, ratings.movieId, "

+ "ratings.rating, movies.title FROM ratings JOIN movies "

+ "ON movies.movieId=ratings.movieId "

+ "where ratings.userId=668 and ratings.rating > 4")

results2.show(false)
```

## Step 4 — Prepare training and test rating data and check the counts

```scala
// Split ratings RDD into training RDD (75%) & test RDD (25%)

val splits = ratingsDF.randomSplit(Array(0.75, 0.25), seed = 12345L)

val (trainingData, testData) = (splits(0), splits(1))

val numTraining = trainingData.count()

val numTest = testData.count()

println("Training: " + numTraining + " test: " + numTest)
```

## Step 5 — Prepare the data for building the recommendation model using ALS

```scala
val ratingsRDD = trainingData.rdd.map(row => {

val userId = row.getString(0)

val movieId = row.getString(1)

val ratings = row.getString(2)

Rating(userId.toInt, movieId.toInt, ratings.toDouble)

})

val testRDD = testData.rdd.map(row => {

val userId = row.getString(0)

val movieId = row.getString(1)

val ratings = row.getString(2)

Rating(userId.toInt, movieId.toInt, ratings.toDouble)

})
```

## Step 6 — Build an ALS user product matrix

```scala
val rank = 20

val numIterations = 15

val lambda = 0.10

val alpha = 1.00

val block = -1

val seed = 12345L

val implicitPrefs = false

val model = new ALS().setIterations(numIterations).setBlocks(block).setAlpha(alpha).setLambda(lambda).setRank(rank).setSeed(seed).setImplicitPrefs(implicitPrefs).run(ratingsRDD)
```

## Step 7 — Making predictions

```scala
// Making Predictions. Get the top 6 movie predictions for user 668

println("Rating:(UserID, MovieID, Rating)")

println("----------------------------------")

val topRecsForUser = model.recommendProducts(668, 6)

for (rating <- topRecsForUser) { println(rating.toString()) }

println("----------------------------------")
```

## Step 8 — Evaluating the model

```scala
def computeRmse(model: MatrixFactorizationModel, data: RDD[Rating], implicitPrefs: Boolean): Double = { 
    val predictions: RDD[Rating] = model.predict(data.map(x => (x.user, x.product)))
    val predictionsAndRatings = predictions
        .map(x => ((x.user, x.product), x.rating))
        .join(data.map(x => ((x.user, x.product), x.rating)))
        .values

    if (implicitPrefs) {
        println("(Prediction, Rating)")
        println(predictionsAndRatings.take(5).mkString("n"))
    }
    math.sqrt(predictionsAndRatings.map(x => (x._1 - x._2) * (x._1 - x._2)).mean())
}

val rmseTest = computeRmse(model, testRDD, true)

println("Test RMSE: = " + rmseTest) //Less is better


println("Recommendations: (MovieId => Rating)")

println("----------------------------------")

val recommendationsUser = model.recommendProducts(668, 6)

recommendationsUser.map(rating => (rating.product, rating.rating)).foreach(println)

println("----------------------------------")
```