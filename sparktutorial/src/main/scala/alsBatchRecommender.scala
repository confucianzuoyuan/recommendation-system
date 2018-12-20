import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.recommendation._
import org.apache.spark.rdd.RDD
import scala.math.sqrt
import org.jblas.DoubleMatrix

object alsBatchRecommender {
  private val minSimilarity = 0.6

  def cosineSimilarity(vector1: DoubleMatrix, vector2: DoubleMatrix): Double = vector1.dot(vector2) / (vector1.norm2() * vector2.norm2())

  def calculateAllCosineSimilarity(model: MatrixFactorizationModel, dataDir: String, dateStr: String): Unit = {
    //calculate all the similarity and store the stuff whose sim > 0.5 to Redis.
    val productsVectorRdd = model.productFeatures
      .map{case (movieId, factor) =>
      val factorVector = new DoubleMatrix(factor)
      (movieId, factorVector)
    }
    
    val productsSimilarity = productsVectorRdd.cartesian(productsVectorRdd)
      .filter{ case ((movieId1, vector1), (movieId2, vector2)) => movieId1 != movieId2 }
      .map{case ((movieId1, vector1), (movieId2, vector2)) =>
        val sim = cosineSimilarity(vector1, vector2)
        (movieId1, movieId2, sim)
      }.filter(_._3 >= minSimilarity)
    
    productsSimilarity.map{ case (movieId1, movieId2, sim) => 
      movieId1.toString + "," + movieId2.toString + "," + sim.toString
    }.saveAsTextFile(dataDir + "allSimilarity_" + dateStr)

    productsVectorRdd.unpersist()
    productsSimilarity.unpersist()
  }

  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("alsBatchRecommender").set("spark.executor.memory", "2g")
    val sc = new SparkContext(conf)
    if (args.length < 1) {
        println("USAGE:")
        println("spark-submit ... xxx.jar Date_String [Iteration]")
        println("spark-submit ... xxx.jar 20160424 10")
        sys.exit()
    }
    val dateStr = args(0)

    val iterations = if (args.length > 1) args(1).toInt else 5

    val dataDir = "/Users/yuanzuo/Desktop/sparktutorial/ml-latest-small/"

    val trainData = sc.textFile(dataDir + "trainingData.csv").map{ line =>
      val lineAttrs = line.trim.split(",")
      Rating(lineAttrs(0).toInt, lineAttrs(1).toInt, lineAttrs(2).toDouble)
    }.cache()

    val (rank, lambda) = (50, 0.01)
    val model = ALS.train(trainData, rank, iterations, lambda)

    trainData.unpersist()

    calculateAllCosineSimilarity(model, dataDir, dateStr) //save cos sim.
    model.save(sc, dataDir + "ALSmodel_" + dateStr) //save model.

    val realRatings = sc.textFile(dataDir + "realRatings.csv").map{ line =>
      val lineAttrs = line.trim.split(",")
      Rating(lineAttrs(0).toInt, lineAttrs(1).toInt, lineAttrs(2).toDouble)
    }

    val rmse = computeRmse(model, realRatings)
    println("the Rmse = " + rmse)

    sc.stop()
  }

  def parameterAdjust(trainData: RDD[Rating], realRatings: RDD[Rating]): (Int, Double, Double) = {
    val evaluations =
      for (rank   <- Array(10,  50);
           lambda <- Array(1.0, 0.0001);
           alpha  <- Array(1.0, 40.0))
        yield {
          val model = ALS.trainImplicit(trainData, rank, 10, lambda, alpha)
          val rmse = computeRmse(model, realRatings)
          unpersist(model)
          ((rank, lambda, alpha), rmse)
        }
    val ((rank, lambda, alpha), rmse) = evaluations.sortBy(_._2).head
    println("After parameter adjust, the best rmse = " + rmse)
    (rank, lambda, alpha)
  }

  def computeRmse(model: MatrixFactorizationModel, realRatings: RDD[Rating]): Double = {
    val testingData = realRatings.map{ case Rating(user, product, rate) =>
      (user, product)
    }

    val prediction = model.predict(testingData).map{ case Rating(user, product, rate) =>
      ((user, product), rate)
    }

    val realPredict = realRatings.map{case Rating(user, product, rate) =>
      ((user, product), rate)
    }.join(prediction)

    sqrt(realPredict.map{ case ((user, product), (rate1, rate2)) =>
      val err = rate1 - rate2
      err * err
    }.mean())//mean = sum(list) / len(list)
  }

  def unpersist(model: MatrixFactorizationModel): Unit = {
    // At the moment, it's necessary to manually unpersist the RDDs inside the model
    // when done with it in order to make sure they are promptly uncached
    model.userFeatures.unpersist()
    model.productFeatures.unpersist()
  }
}
