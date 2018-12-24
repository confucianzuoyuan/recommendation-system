name := "Simple Project"

version := "1.0"

scalaVersion := "2.11.12"

libraryDependencies += "org.apache.spark" %% "spark-sql" % "2.4.0"

libraryDependencies += "org.apache.spark" %% "spark-core" % "2.4.0"

libraryDependencies += "org.apache.spark" %% "spark-mllib" % "2.4.0"

libraryDependencies += "org.apache.spark" %% "spark-streaming" % "2.4.0"

libraryDependencies += "org.apache.spark" %% "spark-streaming-kafka-0-8" % "2.4.0"

libraryDependencies += "org.mongodb" %% "casbah" % "3.0.0"

libraryDependencies += "org.jblas" % "jblas" % "1.2.4"

mergeStrategy in assembly <<= (mergeStrategy in assembly) { mergeStrategy => {
    case entry => {
      val strategy = mergeStrategy(entry)
      if (strategy == MergeStrategy.deduplicate) MergeStrategy.first
      else strategy
    }
  }
}