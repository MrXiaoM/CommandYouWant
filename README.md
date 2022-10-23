# CommandYouWant
> 「你想要的插件」系列作品

[![](https://shields.io/github/downloads/MrXiaoM/CommandYouWant/total)](https://github.com/MrXiaoM/CommandYouWant/releases) [![](https://img.shields.io/badge/mirai--console-2.12.3-blue)](https://github.com/mamoe/mirai) [![](https://img.shields.io/badge/MiraiForum-post-yellow)](https://mirai.mamoe.net/topic/1703)

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
# 是否使用事件模式
# 有些插件并不是标准地注册命令，故需要伪造发送消息事件让插件响应
# 警告: 这可能会干扰聊天记录类的插件
event-mode: false
perm: test
perm-desc: 获取图片
deny-tips: '权限不足'
keyword: '{at:bot}画画 {next}'
# 参数屏蔽词
keyword-block:
  # 第二个参数 {next} 中禁止出现的字符列表
  1: 
    - '#'
keyword-block-tips: 你输入的参数不合规
actions: 
  - '/nai {1}'
  - '可以像这样一次执行多条指令'
# false 的时候忽略执行以上两条命令的权限检查
is-action-check-perm: false
```
首先我们设置权限，比如将 `perm` 设为 test，填写后用户需要有权限 `top.mrxiaom.commandyouwant:command.test` 才能执行这个命令。

然后，我们需要定义用户要怎样才能触发这个命令，将格式写到 `keyword` 里

在 `keyword` 中插入 `{}` 包围的内容作为命令参数，如图中的例子，有两个参数 `{at:bot}` 和 `{next}` (都能填什么参数可以在后面的表格看到)，用户需要发「@机器人 画画 初音未来 可爱(任意字符都行)」，插件就会把其中的参数提取出来，按顺序排好，这里的参数是

| 索引 | 内容         |
| --- |------------|
| 0 | @机器人       |
| 1 | "初音未来 可爱(任意字符都行)" |

然后，插件会将这些参数替换掉 actions 里的 `{索引}` 然后模拟用户执行命令。

说白了，就是给其他插件的命令套壳。

在完成了命令的基础设置后，你可以更改一些进阶设置，注释在默认配置文件里都有。


所有可用的参数如下

| 内容       | 解释                          |
|----------|-----------------------------|
| {arg}    | 单个文字参数，识别到空格就终止             |
| {next}   | 长段文字参数，识别到其他类型的消息元素或消息末尾就终止 |
| {at}     | @某人，@所有人都可以                 |
| {at:QQ号} | @某人，只能@特定某个人                |
| {at:bot} | @机器人                        |
| {img}    | 图片                          |
| {face}   | 表情                          |

希望你能看懂这份教程。
