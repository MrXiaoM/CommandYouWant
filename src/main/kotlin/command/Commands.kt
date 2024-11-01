package top.mrxiaom.commandyouwant.command

import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.util.ConsoleExperimentalApi
import top.mrxiaom.commandyouwant.CommandYouWant
import top.mrxiaom.commandyouwant.CommandYouWant.save
import top.mrxiaom.commandyouwant.config.CommandConfig
import java.io.File

@OptIn(ConsoleExperimentalApi::class)
object Commands : CompositeCommand(
    owner = CommandYouWant,
    primaryName = "CommandYouWant",
    secondaryNames = arrayOf("cmd", "cmduw"),
    parentPermission = CommandYouWant.parentPermission
) {
    @SubCommand("create")
    @Description("以默认模板创建命令配置文件")
    suspend fun create(context: CommandContext, @Name("文件名") name: String) {
        val file = File(CommandYouWant.configFolder, "commands/$name.yml")
        if (file.exists()) {
            context.sender.sendMessage("该文件 ($name.yml) 已存在")
        } else {
            CommandConfig(name).save()
            context.sender.sendMessage("已成功创建配置文件 ${file.toPath().toUri()}")
        }
    }
    @SubCommand("reload")
    @Description("重载命令配置列表")
    suspend fun reload(context: CommandContext) {
        CommandYouWant.reloadConfig()
        context.sender.sendMessage("配置文件已重载")
    }
}