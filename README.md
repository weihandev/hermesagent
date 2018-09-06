# hermesagent

#### 项目介绍
android群控系统，使用xposed+RPC实现方法级别的群控

hermesagent是hermes系统的客户端模块，也是系统最核心的模块了，他是种植在手机里面的一个agent，同时他也是一个xposed的模块插件，agent本身启动了一个service，agent插件模块将会自动注册钩子函数，并且和service通信。Android设备外部请求可以通过暴露在agent上面的一个http端口，和agent通信，然后agent和目标apkRPC。
如此实现外部请求到任何一个app的任何功能的外部调用


#### 关联项目
HermesAdmin ：https://gitee.com/virjar/hermesadmin
hermesAdmin用来管理多个hermesAgent，进行简单的服务治理和agent运维工作

#### 软件架构
软件架构说明
![termesAgent](img/termesAgent.png)


#### 安装教程

1. 修改server服务器地址
2. 编译app正式版
3. 安装app到手机上面
4. Android手机安装xposed环境，并且启用我们的xposed模块，然后重启xposed（xposed项目的标准流程）
5. 书写目标app插件代码，实现 com.virjar.hermes.hermesagent.plugin.AgentCallback
6. 安装目标app到手机，并启动目标app
7. 通过浏览器访问app所在ip的5597端口，查看服务列表
8. 通过invoke接口，调用服务api

#### 使用说明

1. 要安装xposed
2. xposed启用本模块之后，第一次需要重启，后续不需要重启了
3. 钩子函数写到com.virjar.hermes.hermesagent.hookagent路径下，能够被框架自动识别，其他地方不会识别
4. agent必须提供空参构造（我们是类扫描机制实现的）
5. 开启网络访问权限，有些手机在后台运行之后，将会禁止后台访问网络。请放开这个配置
```
设置——无线和网络——WLAN设置——高级设置——WLAN休眠策略——永不休眠

     小米手机：
     1、设置--其他高级设置--电源和性能--神隐模式
     2、标准(限制后台应用的网络和定位功能)
     3、关闭(不限制后台应用的功能)
     4、默认是标准,在屏保后4分钟左右会限制后台应用的网络功能
```

6. 允许程序开机自启
为了让app全自动提供服务，需要让手机开机便启动agent，有些系统会禁止该行为。如果你的手机有存在该行为的话，请放开自启动限制
[stackoverflow](https://stackoverflow.com/questions/32032329/process-is-not-permitted-to-autostart-boot-complete-broadcast-receiver)


### 演示
1. 查看首页，观察可以提供的接口
![index.png](img/index.png)

2.观察已经注册成功，可以提供调用的服务
![servicelist.png](img/servicelist.png)

3.调用目标接口
![invoke.png](img/invoke.png)

其中，本demo提供了微视的话题搜索接口破解，可以通过一个关键字搜索微视的视频数据。微视demo的地址为：

https://gitee.com/virjar/hermesagent/blob/master/app/src/main/java/com/virjar/hermes/hermesagent/hookagent/WeiShiAgent.java

#### 参与贡献

1. Fork 本项目
2. 新建 Feat_xxx 分支
3. 提交代码
4. 新建 Pull Request

#### 合作

开源即免费，我不限制你们拿去搞事情，但是开源并不代表义务解答问题。如果你发现了有意思的bug，或者有建设性意见，我乐意参与讨论。
如果你想寻求解决方案，但是又没有能力驾驭这个项目，欢迎走商务合作通道。联系qq：819154316，或者加群：569543649。
拒绝回答常见问题！！！


#### 捐赠
如果你觉得作者辛苦了，可以的话请我喝杯咖啡
![alipay](img/reward.jpg)