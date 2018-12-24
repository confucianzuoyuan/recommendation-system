部署步骤

使用sbt-assembly将所有的jar包打包成一个fat jar。

```
$ sbt assembly
```

如果想要重新部署，需要将所有文件夹中的`target`文件夹删除掉。
