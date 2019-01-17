# 程序部署步骤

## recommender

修改dataloader中相关配置

1. 在`dataloader/src/main/scala/com/atguigu/dataloader/DataLoader.scala`中: 修改`MOVIE_DATA_PATH`, `RATING_DATA_PATH`, `TAG_DATA_PATH`三个常量为数据的绝对路径，例如我的配置如下：

```scala
  val MOVIE_DATA_PATH = "/Users/yuanzuo/Desktop/recommendation-system/RecommendSystem/recommender/dataloader/src/main/resources/movies.csv"
  val RATING_DATA_PATH = "/Users/yuanzuo/Desktop/recommendation-system/RecommendSystem/recommender/dataloader/src/main/resources/ratings.csv"
  val TAG_DATA_PATH = "/Users/yuanzuo/Desktop/recommendation-system/RecommendSystem/recommender/dataloader/src/main/resources/tags.csv"
```

数据在`dataloader/src/main/resources`里面。

2. 程序中相关服务配置：

- mongo: `localhost:27017`
- redis: `localhost:6379`
- es.httpHosts: `localhost:9200`
- es.transportHosts: `localhost:9300`
- es.index: `recommender`
- es.cluster.name: `elasticsearch`
- zookeeper: `localhost:2181`
- kafka: `localhost:9092`

3. 使用软件的版本：

- mongo: 最新即可
- redis: 最新即可
- es: 5.6.2
- kafka: 最新即可，zookeeper包含在kafka中

4. 服务启动命令：

- es: `./bin/elasticsearch`
- mongo: `mongod --dbpath ~/data/db`(可能需要在home目录新建一个data文件夹)
- redis: `./redis-5.0.3/src/redis-server`
- zookeeper: `./bin/zookeeper-server-start.sh config/zookeeper.properties`
- kafka: `./bin/kafka-server-start.sh config/server.properties`

5. flume收集的日志文件的位置为`businessServer/src/log/agent.log`

6. 日志收集工具flume的使用。

- 版本: 最新即可
- 配置文件编写，放在conf下面，名字是log-kafka.properties:

```properties
agent.sources = exectail
agent.channels = memoryChannel
agent.sinks = kafkasink

# For each one of the sources, the type is defined
agent.sources.exectail.type = exec
# 下面这个路径是需要收集日志的绝对路径
agent.sources.exectail.command = tail -f /Users/yuanzuo/Desktop/RecommendSystem/businessServer/src/main/log/agent.log
agent.sources.exectail.interceptors=i1
agent.sources.exectail.interceptors.i1.type=regex_filter
agent.sources.exectail.interceptors.i1.regex=.+MOVIE_RATING_PREFIX.+
# The channel can be defined as follows.
agent.sources.exectail.channels = memoryChannel

# Each sink's type must be defined
agent.sinks.kafkasink.type = org.apache.flume.sink.kafka.KafkaSink
agent.sinks.kafkasink.kafka.topic = log
agent.sinks.kafkasink.kafka.bootstrap.servers = localhost:9092
agent.sinks.kafkasink.kafka.producer.acks = 1
agent.sinks.kafkasink.kafka.flumeBatchSize = 20


#Specify the channel the sink should use
agent.sinks.kafkasink.channel = memoryChannel

# Each channel's type is defined.
agent.channels.memoryChannel.type = memory

# Other config values specific to each type of channel(sink or source)
# can be defined as well
# In this case, it specifies the capacity of the memory channel
agent.channels.memoryChannel.capacity = 10000
```

启动flume

```
$ ./bin/flume-ng agent -c ./conf/ -f ./conf/log-kafka.properties -n agent
```

7. website的构建

```
$ cd website/website
$ npm install
```

需要修改源码, 路径：`node_modules/ng2-echarts/directives/ng2-echarts-base.d.ts`

将`differ: KeyValueDiffer`改为`differ: KeyValueDiffer<number, number>`

```
$ ./node_modules/@angular/cli/bin/ng build
```

将构建好的文件移动到`businessServer/src/main/webapp`下面

```
$ cd dist
$ mv * ../../../businessServer/src/main/webapp/.
```

将`images`文件夹拷贝到`businessServer/src/main/webapp`下面

执行

使用idea中的maven projects中的tomcat run来启动webapp。