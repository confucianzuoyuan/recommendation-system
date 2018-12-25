部署步骤

使用sbt-assembly将所有的jar包打包成一个fat jar。

```
$ sbt assembly
```

如果想要重新部署，需要将所有文件夹中的`target`文件夹删除掉。

```
$ ./bin/spark-submit --class "alsBatchRecommender" /Users/yuanzuo/Desktop/recommendation-system/sparktutorial/target/scala-2.11/Simple\ Project-assembly-1.0.jar
```
