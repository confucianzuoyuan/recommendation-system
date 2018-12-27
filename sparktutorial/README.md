部署步骤

使用sbt-assembly将所有的jar包打包成一个fat jar。

```
$ sbt assembly
```

如果想要重新部署，需要将所有文件夹中的`target`文件夹删除掉。

```
$ ./bin/spark-submit --class "alsBatchRecommender" /Users/yuanzuo/Desktop/recommendation-system/sparktutorial/target/scala-2.11/Simple\ Project-assembly-1.0.jar
```

```
$ ./bin/spark-submit --class "streamingRecommender" /Users/yuanzuo/Desktop/recommendation-system/sparktutorial/target/scala-2.11/Simple\ Project-assembly-1.0.jar 2
```

启动zookeeper

```
$ ./bin/zookeeper-server-start.sh config/zookeeper.properties
```

启动kafka

```
$ ./bin/kafka-server-start.sh config/server.properties
```

启动发送kafka消息的命令

```
$ bin/kafka-console-producer.sh --broker-list localhost:9092 --topic netflix-recommending-system-topic
```

mongodb表结构

userId | movieId | rate | timestamp

db_name: RecommendingSystem
collection_name: ratingsCollection