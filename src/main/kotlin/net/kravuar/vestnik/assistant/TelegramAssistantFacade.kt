package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.destination.ChannelPlatform
import net.kravuar.vestnik.destination.ChannelsFacade
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.AIArticleProcessingFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.telegram.abilitybots.api.bot.AbilityBot
import org.telegram.abilitybots.api.bot.BaseAbilityBot
import org.telegram.abilitybots.api.db.MapDBContext
import org.telegram.abilitybots.api.objects.Ability
import org.telegram.abilitybots.api.objects.Ability.AbilityBuilder
import org.telegram.abilitybots.api.objects.Ability.builder
import org.telegram.abilitybots.api.objects.Flag
import org.telegram.abilitybots.api.objects.Locality
import org.telegram.abilitybots.api.objects.MessageContext
import org.telegram.abilitybots.api.objects.Privacy
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import java.time.Duration
import java.util.Optional
import java.util.function.Predicate

private enum class CommandWithInput {
    ADD_SOURCE {
        override fun input(): Any = SourcesFacade.SourceInput(
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        )
    };

    abstract fun input(): Any
}

private data class CommandInAction(
    val input: Any,
    val messageId: Int
)

internal class TelegramAssistantFacade(
    token: String,
    private val name: String,
    private val adminChannel: Long,
    private val admins: Set<Long>,
    private val owner: Long,
    private val sourcesFacade: SourcesFacade,
    private val channelsFacade: ChannelsFacade,
    private val articlesFacade: ArticlesFacade,
    private val postsFacade: PostsFacade,
    private val aiArticleProcessingFacade: AIArticleProcessingFacade
) : AbilityBot(
    token,
    name,
    MapDBContext.offlineInstance("contextDB")
        .also { db -> db.getSet<Long>(BaseAbilityBot.ADMINS).addAll(admins) }
) {

    fun showCommands(): Ability = nonInputCommandAbility(
        "showCommands",
        "Показать список команд",
        {},
        { _, _ ->
            val commands = writeForMessage(
                mapOf(
                    "showCommands" to "показать список команд",
                    "showSources" to "показать список источников"
                )
            )
            """Список команд:
                    
            $commands
            """
        },
        { "Не удалось получить список команд" }
    )

    fun showSources(): Ability = nonInputCommandAbility(
        "showSources",
        "Показать список источников",
        { sourcesFacade.getSources() },
        { _, sources ->
            val sourcesAsString = writeForMessage(sources.map {
                    mapOf(
                        "Id" to it.id,
                        "Name" to it.name,
                        "URL" to it.url,
                        "Периодичность" to it.scheduleDelay,
                        "Приостановлен" to it.suspended
                    )
                })
            """Список источников:

            $sourcesAsString
            """
        },
        { "Не удалось получить источники" }
    )

    fun showSource(): Ability = nonInputCommandAbility(
        "showSource",
        "Показать источник",
        { ctx -> sourcesFacade.getSource(ctx.firstArg()) },
        { ctx, source ->
            val sourceAsString = source.let { it ->
                writeForMessage(
                    mapOf(
                        "Id" to it.id,
                        "Name" to it.name,
                        "URL" to it.url,
                        "Периодичность" to it.scheduleDelay,
                        "XPATH к контенту" to it.contentXPath,
                        "Целевые Каналы" to it.channels.joinToString { channel -> channel.name },
                        "Приостановлен" to it.suspended
                    )
                )
            }
            """Источник ${ctx.firstArg()}:

            $sourceAsString
            """
        },
        { ctx -> "Не удалось найти источник ${ctx.firstArg()}" }
    )

    fun addSource(): Ability = commandWithInputAbility(
        "addSource",
        "Добавить источник",
        """Введите следующие данные для добавления источника: 
                    
        ${
            writeForMessage(
                mapOf(
                    "name" to "Имя",
                    "url" to "URL",
                    "schedule" to "Периодичность (в минутах)",
                    "xpath" to "XPATH к контенту",
                    "channels" to "Целевые каналы (имена через запятую)",
                    "suspended" to "Приостановлен (опционально)",
                )
            )
        }
        """,
        CommandWithInput.ADD_SOURCE,
        { _, values, input -> sourcesFacade.addSource((input as SourcesFacade.SourceInput).apply {
                values["name"]?.let {
                    name = Optional.of(it)
                }
                values["url"]?.let {
                    url = Optional.of(it)
                }
                values["schedule"]?.let {
                    scheduleDelay = Optional.of(Duration.ofMinutes(it.toLong()))
                }
                values["xpath"]?.let {
                    contentXPath = Optional.of(it)
                }
                values["channels"]?.let {
                    channels = Optional.of(it.split(",").map {
                            name -> channelsFacade.getChannelByName(name.trim())
                    }.toMutableSet())
                }
                values["suspended"]?.toBoolean()?.let {
                    suspended = Optional.of(it)
                }
        })},
        { _, source -> "Источник ${source.name} добавлен" },
        { "Не удалось добавить источник" }
    )

    fun deleteSource(): Ability = nonInputCommandAbility(
        "deleteSource",
        "Удалить источник по имени",
        { ctx -> sourcesFacade.deleteSource(ctx.firstArg()) },
        { _, source -> "Источник ${source.name} удален" },
        { ctx -> "Не удалось удалить источник ${ctx.firstArg()}" },
    )

    fun updateSource(): Ability = TODO()

    fun showChannels(): Ability = TODO()

    fun addChannel(input: ChannelsFacade.ChannelInput): Ability = TODO()

    fun deleteChannel(id: String): Ability = TODO()

    fun showChains(): Ability = TODO()

    fun showModes(sourceName: String): Ability = TODO()

    fun showChain(sourceName: String, mode: String): Ability = TODO()

    fun addChain(sourceName: String, mode: String): Ability = TODO()

    fun deleteChain(sourceName: String, mode: String): Ability = TODO()

    fun addNode(prevNodeId: Long, input: AIArticleProcessingFacade.AIArticleProcessingNodeInput): Ability = TODO()

    fun deleteNode(nodeId: Long): Ability = TODO()

    fun updateNode(nodeId: Long, input: AIArticleProcessingFacade.AIArticleProcessingNodeInput): Ability = TODO()

    fun showArticles(): Ability = TODO()

    fun showArticle(articleId: Long): Ability = TODO()

    private fun <T> nonInputCommandAbility(
        abilityName: String,
        abilityInfo: String,
        action: (MessageContext) -> T,
        successMessage: (MessageContext, T) -> String,
        errorMessage: (MessageContext) -> String,
        input: Int = 0
    ): Ability {
        return adminBuilder()
            .name(abilityName)
            .info(abilityInfo)
            .action { ctx ->
                handleNonInputCommand(
                    ctx.update().message.messageId,
                    { action(ctx) },
                    { result -> successMessage(ctx, result) },
                    errorMessage(ctx)
                )
            }.build()
    }

    private fun <T> handleNonInputCommand(
        userMessageId: Int,
        action: () -> T,
        successMessage: (T) -> String,
        errorMessage: String
    ) {
        try {
            action().also {
                sendApiMethod(sendMessageToAdmins(
                    successMessage(it),
                    userMessageId
                ))
            }
        } catch (e: Exception) {
            sendApiMethod(sendMessageToAdmins(
                "$errorMessage: ${e.message}",
                userMessageId
            ))
        }
    }

    private fun <T> commandWithInputAbility(
        abilityName: String,
        abilityInfo: String,
        schemaMessage: String,
        command: CommandWithInput,
        action: (Update, Map<String, String>, Any) -> T,
        successMessage: (Update, T) -> String,
        errorMessage: (Map<String, String>) -> String
    ): Ability {
        return adminBuilder()
            .name(abilityName)
            .info(abilityInfo)
            .action { ctx ->
                // Send form message
                val message = sendApiMethod(
                    sendMessageToAdmins(
                        schemaMessage,
                        replyToMessageId = ctx.update().message.messageId
                    )
                )

                // Init input
                with(db.getMap<String, CommandInAction>(COMMANDS_IN_ACTION)) {
                    // Remove old input if exists
                    get(command.name)?.run {
                        sendApiMethod(sendDeleteMessageToAdmins(messageId))
                        remove(command.name)
                    }
                    // Put new one
                    put(command.name, CommandInAction(command.input(), message.messageId))
                }
            }.reply(
                { _, upd ->
                    handleCommandWithInputResponse(
                        command.name,
                        upd.message,
                        { values, input -> action(upd, values, input) },
                        { result -> successMessage(upd, result) },
                        errorMessage
                    )
                },
                Flag.MESSAGE,
                Flag.REPLY,
                isReplyToActiveCommand(command.name)
            ).build()
    }

    private fun <T> handleCommandWithInputResponse(
        commandName: String,
        userMessage: Message,
        action: (Map<String, String>, Any) -> T,
        successMessage: (T) -> String,
        errorMessage: (Map<String, String>) -> String
    ) {
        val values = parseStringToMap(userMessage.text)
        with(db.getMap<String, CommandInAction>(COMMANDS_IN_ACTION)) {
            requireNotNull(get(commandName)) {
                "Непредвиденная ошибка, input не найден"
            }.run {
                try {
                    action(values, input).also {
                        sendApiMethod(sendMessageToAdmins(
                            successMessage(it),
                            userMessage.messageId
                        ))
                        sendApiMethod(sendDeleteMessageToAdmins(messageId))
                        remove(commandName)
                    }
                } catch (e: Exception) {
                    sendApiMethod(sendMessageToAdmins(
                        "${errorMessage(values)}: ${e.message}",
                        userMessage.messageId
                    ))
                }
            }
        }
    }

    private fun sendMessageToAdmins(
        message: String,
        replyToMessageId: Int? = null,
        replyMarkup: ReplyKeyboardMarkup? = null
    ): SendMessage {
        return SendMessage().apply {
            setChatId(adminChannel)
            enableHtml(true)
            disableWebPagePreview()
            replyMarkup?.let { setReplyMarkup(it) }
            replyToMessageId?.let { setReplyToMessageId(it) }
            text = message
        }
    }

    private fun sendDeleteMessageToAdmins(messageId: Int): DeleteMessage {
        return DeleteMessage().apply {
            setChatId(adminChannel)
            setMessageId(messageId)
        }
    }

    private fun sendMessageToChat(message: String, chatId: Long): SendMessage {
        return SendMessage().apply {
            setChatId(chatId)
            enableHtml(true)
            disableWebPagePreview()
            text = message
        }
    }

    private fun forwardMessageToChat(fromChatId: Long, toChatId: Long, messageId: Int): ForwardMessage {
        return ForwardMessage().apply {
            setFromChatId(fromChatId)
            setChatId(toChatId)
            setMessageId(messageId)
        }
    }

    private fun isReplyToActiveCommand(commandName: String): Predicate<Update> {
        return Predicate<Update> { upd ->
            val form = db.getMap<String, CommandInAction>(COMMANDS_IN_ACTION)[commandName]
            return@Predicate form != null && upd.message.replyToMessage.messageId == form.messageId
        }
    }

    override fun checkGlobalFlags(update: Update): Boolean {
        return update.message.chatId == adminChannel || update.callbackQuery.message.chatId == adminChannel
    }

    override fun creatorId(): Long = owner

    override fun getBotUsername(): String = name

    companion object {
        private val PLATFORM = ChannelPlatform.TELEGRAM
        private const val COMMANDS_IN_ACTION = "commands_in_action"
        private const val SPLIT = "===============\n"
        private const val SPLIT_INPUT = ":="

        private fun writeForMessage(pair: Pair<String, Any?>): String {
            return StringBuilder()
                .append(PLATFORM.b1())
                .append(pair.first)
                .append(PLATFORM.b2())
                .append(SPLIT_INPUT)
                .append(PLATFORM.i1())
                .append(pair.second ?: "null")
                .append(PLATFORM.i2())
                .appendLine()
                .toString()
        }

        private fun writeForMessage(pairs: Map<String, Any?>): String {
            return pairs.entries.joinToString("\n") { writeForMessage(it.toPair()) }
        }

        private fun writeForMessage(manyPairs: List<Map<String, Any?>>): String {
            return manyPairs.joinToString(SPLIT) { writeForMessage(it) }
        }

        private fun adminBuilder(): AbilityBuilder {
            return builder()
                .privacy(Privacy.ADMIN)
                .locality(Locality.GROUP)
                .input(0)
        }

        private fun parseStringToMap(input: String): Map<String, String> {
            val pattern = Regex("^(\\S+)\\s*$SPLIT_INPUT\\s*([\\s\\S]*?)(?=\\n^\\S+\\s*$SPLIT_INPUT\\s*|\\Z)")
            val map = mutableMapOf<String, String>()

            input.lines().forEach { line ->
                val matchResult = pattern.find(line)
                if (matchResult != null) {
                    val (key, value) = matchResult.destructured
                    map[key] = value
                }
            }

            return map
        }
    }
}