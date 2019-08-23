### 它是什么？

- 它是一款建立在android设备上的本地服务器。

### 它有什么功能？

- 自定义Response。
- 支持GET, POST 请求，以及文件上传。
- 内置了响应APK ICON，以及图片缩略图。
- 支持文件下载。

### 如何使用它？

#### 依赖

1. 在项目gradle添加

```groovy
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```

2. 在模块gradle添加

```groovy
dependencies {
	        implementation 'com.github.ventsea:Letter:1.0.1'
	}
```

#### 创建服务器需要响应的地址集合

当你希望服务器响应哪些uri，或者你希望自定义哪些uri的Response时，你就需要创建这些uri集合，例如：

```java
UriComponent uriComponent = new UriCOmponent();
uriComponent.add("/index");
uriComponent.add("/hello_world");
```

#### 初始化

在需要启动服务器之前调用

```java
CoreServer.getInstance().initServer(context, uriComponent);
```

#### 监听服务器状态

在需要获取服务器状态之前调用

```java
CoreServer.getInstance().setListener(new IServerHandlerListener());
```

#### 启动服务

启动服务（需要传入一个服务器端口），服务器监听开始生效（如果你设置了监听的话）

```java
CoreServer.getInstance().startServer(port);
```

#### 关闭服务

在不需要的时候，及时关闭服务

```java
CoreServer.getInstance().closeServer();
```

#### 通讯

以下示例中包含的规则用**粗体**标明，请求url中需要包含该字段。

1. Q：如何请求缩略图？

   A：图片缩略图url示例：http://192.168.43.119:8010/**thumb_img**?dir=filepath;

   ​	视频缩略图url示例：http://192.168.43.119:8010/**thumb_video**?dir=filepath;

   ​	应用图标url示例：http://192.168.43.119:8010/**icon**?dir=apkpath;

2. Q：如何请求下载？

   A：下载示例：http://192.168.43.119:8010/**file**?dir=filepath;

3. Q：如何上传？

   A：上传示例：http://192.168.43.119:8010/**upload**?fn=filename;

除了以上已经内置的关键url，你也可以在UriComponent中添加自定义url。

### 重要的接口与类

#### IServerListener

接口IServerListener提供了多个状态回调（UI 线程回调）：

1. 服务器已启动：```onServerStart();```
2. 服务器已关闭：```onServerStop();```
3. 开始发送文件：```onSendStarted(String remoteAddress, Uri uri);```
4. 文件发送进度：```onSendProgress(String remoteAddress, Uri uri, long progress, long total);```
5. 文件发送完成：```onSendCompleted(String remoteAddress, Uri uri);```
6. 文件发送失败：```onSendError(String remoteAddress, Uri uri);```

#### IServerHandlerListener

接口IServerHandlerListener 继承了 IServerListener，新增了

```java
onServerRead(String url, CustomResponse response);
```

该方法在子线程回调，请勿进行UI操作，也不建议进行UI操作。

服务器捕获到客户端访问了UriComponent里的某一个Url地址，会将该访问过滤出来，创建默认的CustomResponse，连同Url回调出来提供自定义操作，其中，CustomResponse中的responseContent就是将要返回给客户端的http响应体。

#### UriComponent

- addurl()：添加自定义url
- getUrl()：获取所有的自定义url

#### CustomResponse

字段解析

- remoteIp：远程IP
- request：http请求
- responseContent：响应体
- httpBody：请求体
- attribute：请求表格

### 混淆

```xml
-dontwarn io.netty.**
-dontwarn sun.**
-keepattributes Signature,InnerClasses
-keepclasseswithmembers class io.netty.** {
    *;
}
```

### 简单的示例

在项目的app模块中提供了简单的请求与响应的示例。
