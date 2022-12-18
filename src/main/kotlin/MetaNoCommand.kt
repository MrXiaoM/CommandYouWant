package top.mrxiaom.commandyouwant

import net.mamoe.mirai.message.data.*

object MetaNoCommand : Message, MessageMetadata, ConstrainSingle {
    override val key: MessageKey<MetaNoCommand> = Key
    object Key : MessageKey<MetaNoCommand> {
        override val safeCast: (SingleMessage) -> MetaNoCommand? = { it as? MetaNoCommand }
    }
    override fun toString(): String = contentToString()
}
