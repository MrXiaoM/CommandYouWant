package top.mrxiaom.commandyouwant.config

import net.mamoe.mirai.console.command.CommandSender
import net.mamoe.mirai.message.data.*
import top.mrxiaom.commandyouwant.CommandYouWant
import top.mrxiaom.commandyouwant.split
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf


val regex0 = Regex("\\{[A-Za-z0-9_:-]+}")
internal fun parseCommandArgument(s: String): CommandArgumentsDefector =
    CommandArgumentsDefector(regex0.split(s) { text, isMatched ->
        if (!isMatched) {
            val str = text.trim()
            if (str.isEmpty()) return@split null
            else return@split CommandArgumentPlainText(str)
        }
        fun checkSpecial(): ICommandArgument? {
            val special = text.substring(1, text.length - 1)
            if (special == "arg") return CommandArgument()
            if (special == "next") return CommandArgumentNext()
            if (special == "at") return CommandArgumentAtAny()
            if (special.startsWith("at:")) {
                val target = special.substring(3)
                if (target == "bot") return CommandArgumentAtBot()
                return CommandArgumentAt(target.toLongOrNull() ?: return null)
            }
            if (special == "img") return CommandArgumentImage()
            if (special == "face") return CommandArgumentFaceAny()
            return null
        }

        val result = checkSpecial()
        if (result == null) CommandYouWant.logger.warning("无法解析 “$s” 的语句 “$text”")
        return@split result
    })

class CommandArgumentsDefector(
    val args: List<ICommandArgument>
) {
    fun findArguments(sender: CommandSender, message: List<SingleMessage>): List<SingleMessage>? {
        val msg = message.toMutableList()
        val argsResult = mutableListOf<SingleMessage>()
        for (arg in args) {
            // 如果有一个参数不符合条件，换下一个命令
            val single = arg.check(sender, msg) ?: return null
            if (single is PlainText && single.content.isEmpty()) {
                continue
            }
            argsResult.add(single)
        }
        return argsResult
    }
}

interface ICommandArgument {
    fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage?
}

/**
 * 固定格式文字
 */
class CommandArgumentPlainText(val content: String) : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is PlainText && m.content.startsWith(content)) {
            // 吃掉挖出来的字符串
            msg[0] = PlainText(m.content.substring(content.length).trimStart())
            if (msg[0].content.isEmpty()) {
                // 吃空之后移除
                msg.removeFirstOrNull()
            }
            return PlainText("")
        }
        return null
    }

    override fun toString(): String = content
}

/**
 * 空格间隔参数
 */
class CommandArgument : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is PlainText) {
            val arg = if (m.content.contains(" ")) m.content.substringBefore(" ") else m.content
            // 吃掉挖出来的字符串
            msg[0] = PlainText(m.content.substring(arg.length).trimStart())
            if (msg[0].content.isEmpty()) {
                // 吃空之后移除
                msg.removeFirstOrNull()
            }
            return PlainText(arg)
        }
        return null
    }

    override fun toString(): String = "<文字>"
}

/**
 * 泛消息类型参数
 */
class CommandArgumentNext : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        // 吃掉挖出来的消息类型
        msg.removeFirstOrNull()
        return m
    }

    override fun toString(): String = "<文字>"
}

class CommandArgumentFaceAny : CommandArgumentTypeCheck(Face::class){
    override fun toString(): String = "<表情>"
}
class CommandArgumentImage : CommandArgumentTypeCheck(Image::class) {
    override fun toString(): String = "<图片>"
}
class CommandArgumentAtAny : CommandArgumentTypeCheck(At::class) {
    override fun toString(): String = "<@任意群员>"
}
class CommandArgumentAtBot : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is At && m.target == sender.bot?.id) {
            // 吃掉挖出来的At消息
            msg.removeFirstOrNull()
            return m
        }
        return null
    }

    override fun toString(): String = "<@机器人>"
}

class CommandArgumentAt(val target: Long) : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m is At && m.target == target) {
            // 吃掉挖出来的At消息
            msg.removeFirstOrNull()
            return m
        }
        return null
    }

    override fun toString(): String = "<@特定人:$target>"
}

abstract class CommandArgumentTypeCheck(
    private val type: KClass<out SingleMessage>
) : ICommandArgument {
    override fun check(sender: CommandSender, msg: MutableList<SingleMessage>): SingleMessage? {
        val m = msg.firstOrNull() ?: return null
        if (m::class.isSuperclassOf(type).not()) return null
        // 吃掉挖出来的指定类型消息
        msg.removeFirstOrNull()
        return m
    }
}
