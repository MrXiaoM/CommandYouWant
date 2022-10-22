# CommandYouWant
> 「你想要的插件」系列作品

[![](https://shields.io/github/downloads/MrXiaoM/CommandYouWant/total)](https://github.com/MrXiaoM/CommandYouWant/releases) [![](https://img.shields.io/badge/mirai--console-2.12.3-blue)](https://github.com/mamoe/mirai) [![](https://img.shields.io/badge/MiraiForum-post-yellow)](https://mirai.mamoe.net/topic/)

重定向命令，统一机器人命令格式。

> 灵感来源于 Minecraft 服务端插件 [OhMyCmd](https://www.mcbbs.net/thread-1064805-1-1.html)

## 简介

各大插件作者对于机器人命令的命名方式各不相同，
同时，有的插件年久失修，且没有提供命令修改方法。
这时，CommandYouWant 派上了用场，你可以逐一部署
各个命令的别名，将这个命令的参数任意映射到另一个命令上。

## 安装

到 [Releases](https://github.com/MrXiaoM/CommandWant/releases) 下载插件并放入 plugins 文件夹进行安装

> 2.11 或以上下载 CommandYouWant-*.mirai2.jar
>
> 2.11 以下下载 CommandYouWant-legacy-*.mirai.jar

安装完毕后，编辑配置文件作出你想要的修改。在控制台执行 `/cmduw reload` 重载配置即可~

## 使用方法和示例

以调用 cssxsh 大佬的 NovelAi Helper 为例，先到文件夹 `config/top.mrxiaom.commandyouwant/commands`，第一次启动这个插件会创建这个文件夹并把示例配置 `sample.yml` 放进去。首先把 `sample.yml` 复制一份，取任意名放在同一文件夹中。然后打开编辑：

```yaml
# 触发命令所需权限
# (留空为不注册权限，若不注册权限
# 将无法限定该命令可在何处使用)
# 权限注册后无法注销
perm: test
# 权限描述
perm-desc: 获取图片
# 权限不足提示，留空不提示
deny-tips: '权限不足'
# 触发命令的关键词规则
keyword: '画画 {next}'
# 各个关键词的屏蔽规则
keyword-block: 
  1: 
    - '#'
# 各个关键词的屏蔽规则
keyword-block-tips: 你输入的参数不合规
# 执行命令
actions: 
  - '/nai {0} #seed=114514 #step=3 #width=512 #height=512'
# 在执行命令时，是否检查权限，若不检查将忽略权限强制执行
is-action-check-perm: true
```
perm 为权限，填写后用户需要有权限 `top.mrxiaom.commandyouwant:command.你设定的权限` 才能执行这个命令。

用户在聊天中发送的内容如果满足 keyword 提供的规则就能执行命令。
其中，没有用 `{}` 包住的就是必填的普通文字，用 `{}` 包住的是特殊参数，要求用户填写特殊类型的内容(比如文字、AT、图片)才符合规则。
`{}` 里面不是随便写的，目前的写法如下

| 内容       | 解释                          |
|----------|-----------------------------|
| {arg}    | 单个文字参数，识别到空格就终止             |
| {next}   | 长段文字参数，识别到其他类型的消息元素或消息末尾就终止 |
| {at}     | @某人，@所有人都可以                 |
| {at:QQ号} | @某人，只能@特定某个人                |
| {at:bot} | @机器人                        |
| {img}    | 图片                          |
| {face}   | 表情                          |

在解析到命令准备执行时，所有的 `{}` 参数都会按 keyword 中的顺序被添加到参数列表，然后替换掉 actions 中 `{索引}` 的内容再将替换后的内容作为命令，模拟用户执行。

假如 keyword: `登录 {arg} {arg}`
action: `login {1} {0}`

则有权限的人对机器人发送 `登录 114514 1919810` 就相当于执行了 `login 1919810 114514`

## 没写完