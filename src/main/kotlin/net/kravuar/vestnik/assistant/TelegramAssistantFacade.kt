package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.commons.Constants
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
    ADD_SOURCE,
    UPDATE_SOURCE,
    ADD_CHANNEL,
    ADD_NODE,
    UPDATE_NODE
}

private data class CommandInAction(
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
                        "Приостановлен" to it.suspended,
                        "Удалён" to it.deleted
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
            val sourceAsString = source.let {
                writeForMessage(
                    mapOf(
                        "Id" to it.id,
                        "Name" to it.name,
                        "URL" to it.url,
                        "Периодичность" to it.scheduleDelay,
                        "XPATH к контенту" to it.contentXPath,
                        "Целевые Каналы" to it.channels.joinToString { channel -> channel.name },
                        "Приостановлен" to it.suspended,
                        "Удалён" to it.deleted
                    )
                )
            }
            """Источник ${ctx.firstArg()}:

            $sourceAsString
            """
        },
        { ctx -> "Не удалось найти источник ${ctx.firstArg()}" },
        input = 1
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
        { _, values -> sourcesFacade.addSource(SourcesFacade.SourceInput().apply {
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
        input = 1
    )

    fun updateSource(): Ability = commandWithInputAbility(
        "updateSource",
        "Обновить источник",
        """Введите необходимые данные для обновления источника: 
                    
        ${
            writeForMessage(
                mapOf(
                    "currentName" to "Имя обновляемого источника (обязательно)",
                    "newName" to "Имя",
                    "url" to "URL",
                    "schedule" to "Периодичность (в минутах)",
                    "xpath" to "XPATH к контенту",
                    "channels" to "Целевые каналы (имена через запятую)",
                    "suspended" to "Приостановлен",
                )
            )
        }
        """,
        CommandWithInput.UPDATE_SOURCE,
        { _, values -> sourcesFacade.updateSource(
            requireNotNull(values["currentName"]) { "Не указано имя обновляемого источника" },
            SourcesFacade.SourceInput().apply {
                values["newName"]?.let {
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
            }
        )},
        { _, source -> "Источник ${source.name} обновлён" },
        { "Не удалось обновить источник" }
    )

    fun showChannels(): Ability = nonInputCommandAbility(
        "showChannels",
        "Показать список каналов",
        { channelsFacade.getAllChannels() },
        { _, channels ->
            val channelsAsString = writeForMessage(channels.map {
                mapOf(
                    "Id" to it.id,
                    "Имя" to it.name,
                    "Платформа" to it.platform,
                    "Источники" to it.sources.joinToString(", ") { source -> source.name },
                    "Удалён" to it.deleted,
                )
            })
            """Список каналов:

            $channelsAsString
            """
        },
        { "Не удалось получить каналы" }
    )

    fun addChannel(): Ability = commandWithInputAbility(
        "addChanel",
        "Добавить канал",
        """Введите следующие данные для добавления канала: 
                    
        ${
            writeForMessage(
                mapOf(
                    "id" to "ID канала",
                    "name" to "Имя канала",
                    "platform" to "Платформа (${ChannelPlatform.entries.joinToString("/") { it.name }})",
                    "sources" to "Источники (имена через запятую)",
                )
            )
        }
        """,
        CommandWithInput.ADD_CHANNEL,
        { _, values -> channelsFacade.addChannel(ChannelsFacade.ChannelInput().apply {
            values["name"]?.let {
                name = Optional.of(it)
            }
            values["id"]?.let {
                id = Optional.of(it)
            }
            values["platform"]?.let {
                platform = Optional.of(ChannelPlatform.valueOf(it.uppercase()))
            }
            values["sources"]?.let {
                sources = Optional.of(it.split(",").map {
                        name -> sourcesFacade.getSource(name.trim())
                }.toMutableSet())
            }
        })},
        { _, channel -> "Канал ${channel.name} добавлен" },
        { "Не удалось добавить канал" }
    )

    fun deleteChannel(): Ability = nonInputCommandAbility(
        "deleteChannel",
        "Удалить канал по имени",
        { ctx -> channelsFacade.deleteChannel(ctx.firstArg()) },
        { _, source -> "Канал ${source.name} удален" },
        { ctx -> "Не удалось удалить канал ${ctx.firstArg()}" },
        input = 1
    )

    fun showChains(): Ability = nonInputCommandAbility(
        "showChains",
        "Показать цепочки обработки статей",
        { aiArticleProcessingFacade.getSequences() },
        { _, roots ->
            val chainsAsString = writeForMessage(roots.map {
                mapOf(
                    "Id Корня" to it.id,
                    "Источник" to it.source,
                    "Режим" to it.mode,
                )
            })
            """Список цепочек:

            $chainsAsString
            """
        },
        { "Не удалось получить цепочки" }
    )

    fun showModes(): Ability = nonInputCommandAbility(
        "showModes",
        "Показать режимы обработки статей для источника",
        { ctx -> aiArticleProcessingFacade.getModes(ctx.firstArg()) },
        { ctx, modes -> "Список режимов для источника ${ctx.firstArg()}: ${modes.joinToString(", ", "[", "]")}" },
        { ctx -> "Не удалось получить режимы для источника ${ctx.firstArg()}" },
        input = 1
    )

    fun showChain(): Ability = nonInputCommandAbility(
        "showChain",
        "Показать конкретную цепочку",
        { ctx -> aiArticleProcessingFacade.getSequence(ctx.firstArg(), ctx.secondArg()) },
        { ctx, chain ->
            val chainAsString = writeForMessage(chain.map {
                mapOf(
                    "Id узла" to it.id,
                    "Модель" to it.model,
                    "Температура" to it.temperature,
                    "Промпт" to it.prompt,
                    "Размер промпта" to it.prompt.length,
                )
            })
            """Цепочка для источника ${ctx.firstArg()}, режима ${ctx.secondArg()} (${chainAsString.length} узлов):

            $chainAsString
            """
        },
        { ctx -> "Не удалось получить цепочку для источника ${ctx.firstArg()}, режима ${ctx.secondArg()}" },
        input = 2
    )

    fun addChain(): Ability = nonInputCommandAbility(
        "addChain",
        "Добавить цепочку обработки статьи",
        { ctx -> aiArticleProcessingFacade.createSequence(ctx.firstArg(), ctx.secondArg()) },
        { ctx, chain ->
            val chainAsString = writeForMessage(chain.map {
                mapOf(
                    "Id узла" to it.id,
                    "Модель" to it.model,
                    "Температура" to it.temperature,
                    "Промпт" to it.prompt,
                    "Размер промпта" to it.prompt.length,
                )
            })
            """Цепочка для источника ${ctx.firstArg()}, режима ${ctx.secondArg()} создана:

            $chainAsString
            """
        },
        { ctx -> "Не удалось создать цепочку для источника ${ctx.firstArg()}, режима ${ctx.secondArg()}" },
        input = 2
    )

    fun deleteChain(): Ability = nonInputCommandAbility(
        "deleteChain",
        "Удалить цепочку обработки статьи",
        { ctx -> aiArticleProcessingFacade.deleteSequence(ctx.firstArg(), ctx.secondArg()) },
        { ctx, chain ->
            val chainAsString = writeForMessage(chain.map {
                mapOf(
                    "Id узла" to it.id,
                    "Модель" to it.model,
                    "Температура" to it.temperature,
                    "Промпт" to it.prompt,
                    "Размер промпта" to it.prompt.length,
                )
            })
            """Цепочка для источника ${ctx.firstArg()}, режима ${ctx.secondArg()} удалена:

            $chainAsString
            """
        },
        { ctx -> "Не удалось удалить цепочку для источника ${ctx.firstArg()}, режима ${ctx.secondArg()}" },
        input = 2
    )

    fun addNode(): Ability = commandWithInputAbility(
        "addNode",
        "Добавить узел после указанного узла",
        """Введите следующие данные для добавления узла: 
                    
        ${
            writeForMessage(
                mapOf(
                    "prevNodeId" to "ID предыдущего узла (Обязательно)",
                    "model" to "Модель узла (По умолчанию: - ${Constants.DEFAULT_MODEL})",
                    "temperature" to "Температура узла (По умолчанию: - ${Constants.DEFAULT_TEMPERATURE})",
                    "prompt" to "Промпт узла (Обязательно)",
                )
            )
        }
        """,
        CommandWithInput.ADD_NODE,
        { _, values -> aiArticleProcessingFacade.insertNode(
            requireNotNull(values["prevNodeId"]) { "ID предыдущего узла обязателен" }.toLong(),
            AIArticleProcessingFacade.AIArticleProcessingNodeInput().apply {
                values["model"]?.let {
                    model = Optional.of(it)
                }
                values["temperature"]?.let {
                    temperature = Optional.of(it.toDouble())
                }
                values["prompt"]?.let {
                    prompt = Optional.of(it)
                }
            }
        )},
        { _, node -> "Узел ${node.id} добавлен в цепочку источника ${node.source.name}, режима ${node.mode}" },
        { "Не удалось добавить узел" },
    )

    fun deleteNode(): Ability = nonInputCommandAbility(
        "deleteNode",
        "Удалить узел обработки статьи",
        { ctx -> aiArticleProcessingFacade.deleteNode(ctx.firstArg().toLong()) },
        { ctx, node ->
            val nodeAsString = writeForMessage(mapOf(
                    "Id узла" to node.id,
                    "Модель" to node.model,
                    "Температура" to node.temperature,
                    "Промпт" to node.prompt,
                    "Размер промпта" to node.prompt.length,
                ))
            """Узел из цепочки для источника ${ctx.firstArg()}, режима ${ctx.secondArg()} удален:

            $nodeAsString
            """
        },
        { ctx -> "Не удалось удалить узел с id ${ctx.firstArg()}" },
        input = 1
    )

    fun updateNode(): Ability = commandWithInputAbility(
        "updateNode",
        "Обновить узел обработки статьи",
        """Введите следующие данные для обновления узла: 
                    
        ${
            writeForMessage(
                mapOf(
                    "nodeId" to "ID узла (Обязательно)",
                    "model" to "Модель узла",
                    "temperature" to "Температура узла",
                    "prompt" to "Промпт узла"
                )
            )
        }
        """,
        CommandWithInput.UPDATE_SOURCE,
        { _, values -> aiArticleProcessingFacade.updateNode(
            requireNotNull(values["nodeId"]) { "ID узла обязателен" }.toLong(),
            AIArticleProcessingFacade.AIArticleProcessingNodeInput().apply {
                values["model"]?.let {
                    model = Optional.of(it)
                }
                values["temperature"]?.let {
                    temperature = Optional.of(it.toDouble())
                }
                values["prompt"]?.let {
                    prompt = Optional.of(it)
                }
            }
        )},
        { _, node -> "Узел ${node.id} обновлён в цепочке источника ${node.source.name}, режима ${node.mode}" },
        { "Не удалось обновить узел" },
    )

    fun showArticles(): Ability = nonInputCommandAbility(
        "showArticles",
        "Показать статьи по статусу (статус, номер страницы)",
        { ctx -> articlesFacade.getArticles(Article.Status.valueOf(ctx.firstArg().uppercase()), ctx.secondArg().toInt()) },
        { ctx, articles ->
            val articlesAsString = writeForMessage(articles.second.map {
                mapOf(
                    "Id статьи" to it.id,
                    "Источник" to it.source.name,
                    "Заголовок" to it.title,
                    "URL" to it.url,
                    "Создана" to it.createdAt.toString(),
                )
            })
            """Страница ${ctx.secondArg()}/${articles.first} статей в статусе ${ctx.firstArg()}:

            $articlesAsString
            """
        },
        { ctx -> "Не удалось удалить узел с id ${ctx.firstArg()}" },
        input = 2
    )

    fun showArticle(): Ability = nonInputCommandAbility(
        "showArticle",
        "Показать статью по Id",
        { ctx -> articlesFacade.getArticle(ctx.secondArg().toLong()) },
        { ctx, article ->
            val articleAsString = writeForMessage(mapOf(
                    "Заголовок" to article.title,
                    "Источник" to article.source.name,
                    "URL" to article.url,
                    "Создана" to article.createdAt.toString(),
                    "Содержание" to article.content,
                    "Статус" to article.status,
                )
            )
            """Статья c Id ${ctx.firstArg()}:

            $articleAsString
            """
        },
        { ctx -> "Не удалось найти статью с id ${ctx.firstArg()}" },
        input = 1
    )

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
            .input(input)
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
        action: (Update, Map<String, String>) -> T,
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
                    put(command.name, CommandInAction(message.messageId))
                }
            }.reply(
                { _, upd ->
                    handleCommandWithInputResponse(
                        command.name,
                        upd.message,
                        { values -> action(upd, values) },
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
        action: (Map<String, String>) -> T,
        successMessage: (T) -> String,
        errorMessage: (Map<String, String>) -> String
    ) {
        val values = parseStringToMap(userMessage.text)
        with(db.getMap<String, CommandInAction>(COMMANDS_IN_ACTION)) {
            requireNotNull(get(commandName)) {
                "Непредвиденная ошибка, input не найден"
            }.run {
                try {
                    action(values).also {
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