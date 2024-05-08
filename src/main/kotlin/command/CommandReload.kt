package top.mrxiaom.commandyouwant.command

import net.mamoe.mirai.console.command.CommandContext
import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.console.command.CompositeCommand
import net.mamoe.mirai.console.command.SimpleCommand
import top.mrxiaom.commandyouwant.CommandYouWant

object CommandReload : CompositeCommand(
    owner = CommandYouWant,
    primaryName = "CommandYouWant",
    secondaryNames = arrayOf("cmd", "cmduw"),
    parentPermission = CommandYouWant.parentPermission
) {
    @SubCommand("reload")
    suspend fun reload(context: CommandContext) {
        CommandYouWant.reloadConfig()
        context.sender.sendMessage("配置文件已重载")
    }
}