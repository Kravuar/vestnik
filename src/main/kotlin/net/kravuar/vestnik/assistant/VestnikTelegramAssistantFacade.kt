package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.commons.Constants
import net.kravuar.vestnik.destination.ChannelPlatform
import net.kravuar.vestnik.destination.ChannelsFacade
import net.kravuar.vestnik.post.Post
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.AIArticleProcessingFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import org.telegram.telegrambots.bots.TelegramLongPollingBot
import org.telegram.telegrambots.meta.api.methods.ForwardMessage
import org.telegram.telegrambots.meta.api.methods.send.SendMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText
import org.telegram.telegrambots.meta.api.objects.Message
import org.telegram.telegrambots.meta.api.objects.Update
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup
import java.time.Duration
import java.util.Optional

internal class VestnikTelegramAssistantFacade(
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
) : TelegramLongPollingBot(token), AssistantFacade {

    private interface UpdateContext {
        val update: Update
        val user: Long
    }

    private data class CommandContext(
        override val update: Update,
        override val user: Long,
        val command: Command = Command.valueOf(update.message.text
            .split(" ")
            .first()
            .uppercase()
        ),
        val arguments: List<String> = update.message.text
            .split(" ")
            .drop(1)
            .also {
                require(it.size == command.requiredArgs.size) {
                    "Для команды ${command.commandValue} ожидаются аргументы: ${command.requiredArgs}"
                }
            }
    ): UpdateContext {
        enum class Command(
            val commandValue: String,
            val description: String,
            val requiredArgs: List<String>
        ) {
            SHOW_SOURCES("showSources", "Показать список источников", emptyList()),
            SHOW_SOURCE("showSource", "Показать источник", listOf("Имя источника")),
            ADD_SOURCE("addSource", "Добавить источник", emptyList()),
            DELETE_SOURCE("deleteSource", "Удалить источник по имени", listOf("Имя источника")),
            UPDATE_SOURCE("updateSource", "Обновить источник", emptyList()),
            SHOW_CHANNELS("showChannels", "Показать список каналов", emptyList()),
            ADD_CHANNEL("addChannel", "Добавить канал", emptyList()),
            DELETE_CHANNEL("deleteChannel", "Удалить канал по имени", listOf("Имя канала")),
            SHOW_CHAINS("showChains", "Показать цепочки обработки статей", emptyList()),
            SHOW_MODES("showModes", "Показать режимы обработки статей для источника", listOf("Имя источника")),
            SHOW_CHAIN("showChain", "Показать конкретную цепочку", listOf("Имя источника", "Имя режима")),
            ADD_CHAIN("addChain", "Добавить цепочку обработки статьи", emptyList()),
            DELETE_CHAIN("deleteChain", "Удалить цепочку обработки статьи", listOf("Имя источника", "Имя режима")),
            ADD_NODE("addNode", "Добавить узел после указанного узла", emptyList()),
            DELETE_NODE("deleteNode", "Удалить узел обработки статьи", listOf("ID узла")),
            UPDATE_NODE("updateNode", "Обновить узел обработки статьи", emptyList()),
            SHOW_POSTS("showPost", "Показать пост по статусу (статус, номер страницы)", emptyList()),
            SHOW_POST("showPost", "Показать пост по Id", listOf("ID поста")),
            SHOW_COMMANDS("showCommands", "Показать список команд", emptyList()),
        }

        fun getArg(index: Int): String {
            return if (arguments.size > index) {
                arguments[index]
            } else {
                throw IllegalArgumentException("Нет $index-го аргумента")
            }
        }
    }

    private data class FormReplyContext(
        override val update: Update,
        override val user: Long,
        val commandContext: CommandContext,
        val values: Map<String, String> = parseStringToMap(update.message.text)
    ): UpdateContext {
        companion object {
            private const val SPLIT = "===============\n"
            private const val SPLIT_INPUT = ":="

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

            fun writeForMessage(pair: Pair<String, Any?>): String {
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

            fun writeForMessage(pairs: Map<String, Any?>): String {
                return pairs.entries.joinToString("\n") { writeForMessage(it.toPair()) }
            }

            fun writeForMessage(manyPairs: List<Map<String, Any?>>): String {
                return manyPairs.joinToString(SPLIT) { writeForMessage(it) }
            }
        }
    }

    private data class PostActionContext(
        override val update: Update,
        override val user: Long,
        val post: Post
    ): UpdateContext

    private val commandsInAction = mutableMapOf<Int, CommandContext>()

    override fun onUpdateReceived(update: Update) {
        try {
            if (globalFilter(update)) {
                if (update.message.isCommand) {
                    onCommand(CommandContext(
                        update,
                        update.message.from.id
                    ))
                } else if (update.message.isReply) {
                    val commandInAction = commandsInAction[update.message.replyToMessage.messageId]
                    commandInAction?.run {
                        onFormReply(FormReplyContext(
                            update,
                            update.message.from.id,
                            this
                        ))
                    }
                } else if (update.callbackQuery != null) {
                    postsFacade.getPostOptional(update.callbackQuery.message.messageId).ifPresent {
                        onPostAction(
                            PostActionContext(
                                update,
                                update.callbackQuery.from.id,
                                it
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Ошибка при обработке обновления ${update.updateId}", e)
            try {
                sendMessageToAdmins(
                    message = "Ошибка при обработке обновления ${update.updateId}: ${e.message}",
                    replyToMessageId = update.message.messageId
                )
            } catch (e: Exception) {
                LOG.error("Ошибка при уведомлении об ошибке обработки обновления ${update.updateId}", e)
            }
        }
    }

    private fun globalFilter(update: Update): Boolean {
        // Only admins in admin chat
        return admins.intersect(
            setOf(update.message?.from?.id, update.callbackQuery?.from?.id)
        ).isNotEmpty() && update.message.chatId == adminChannel
    }

    private fun onCommand(commandContext: CommandContext) {
        when (commandContext.command) {
            CommandContext.Command.SHOW_SOURCES -> {
                handleCommand(
                    commandContext,
                    { sourcesFacade.getSources() },
                    { _, sources ->
                        val sourcesAsString = FormReplyContext.writeForMessage(sources.map {
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
            }
            CommandContext.Command.SHOW_SOURCE -> {
                handleCommand(
                    commandContext,
                    { ctx -> sourcesFacade.getSource(ctx.getArg(0)) },
                    { ctx, source ->
                        val sourceAsString = source.let {
                            FormReplyContext.writeForMessage(
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
                        """Источник ${ctx.getArg(0)}:
            
                        $sourceAsString
                        """
                    },
                    { ctx -> "Не удалось найти источник ${ctx.getArg(0)}" },
                )
            }
            CommandContext.Command.ADD_SOURCE -> {
                handleFormCommand(
                    commandContext,
                    """Введите следующие данные для добавления источника: 
                    
                    ${
                        FormReplyContext.writeForMessage(
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
                )
            }
            CommandContext.Command.DELETE_SOURCE -> {
                handleCommand(
                    commandContext,
                    { ctx -> sourcesFacade.deleteSource(ctx.getArg(0)) },
                    { _, source -> "Источник ${source.name} удален" },
                    { ctx -> "Не удалось удалить источник ${ctx.getArg(0)}" },
                )
            }
            CommandContext.Command.UPDATE_SOURCE -> {
                handleFormCommand(
                    commandContext,
                    """Введите необходимые данные для обновления источника: 
                    
                    ${
                        FormReplyContext.writeForMessage(
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
                )
            }
            CommandContext.Command.SHOW_CHANNELS -> {
                handleCommand(
                    commandContext,
                    { channelsFacade.getAllChannels() },
                    { _, channels ->
                        val channelsAsString = FormReplyContext.writeForMessage(channels.map {
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
            }
            CommandContext.Command.ADD_CHANNEL -> {
                handleFormCommand(
                    commandContext,
                    """Введите следующие данные для добавления канала: 
                    
                    ${
                        FormReplyContext.writeForMessage(
                            mapOf(
                                "id" to "ID канала",
                                "name" to "Имя канала",
                                "platform" to "Платформа (${ChannelPlatform.entries.joinToString("/") { it.name }})",
                                "sources" to "Источники (имена через запятую)",
                            )
                        )
                    }
                    """,
                )
            }
            CommandContext.Command.DELETE_CHANNEL -> {
                handleCommand(
                    commandContext,
                    { ctx -> channelsFacade.deleteChannel(ctx.getArg(0)) },
                    { _, source -> "Канал ${source.name} удален" },
                    { ctx -> "Не удалось удалить канал ${ctx.getArg(0)}" },
                )
            }
            CommandContext.Command.SHOW_CHAINS -> {
                handleCommand(
                    commandContext,
                    { aiArticleProcessingFacade.getSequences() },
                    { _, roots ->
                        val chainsAsString = FormReplyContext.writeForMessage(roots.map {
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
            }
            CommandContext.Command.SHOW_MODES -> {
                handleCommand(
                    commandContext,
                    { ctx -> aiArticleProcessingFacade.getModes(ctx.getArg(0)) },
                    { ctx, modes -> "Список режимов для источника ${ctx.getArg(0)}: ${modes.joinToString(", ", "[", "]")}" },
                    { ctx -> "Не удалось получить режимы для источника ${ctx.getArg(0)}" },
                )
            }
            CommandContext.Command.SHOW_CHAIN -> {
                handleCommand(
                    commandContext,
                    { ctx -> aiArticleProcessingFacade.getSequence(ctx.getArg(0), ctx.getArg(1)) },
                    { ctx, chain ->
                        val chainAsString = FormReplyContext.writeForMessage(chain.map {
                            mapOf(
                                "Id узла" to it.id,
                                "Модель" to it.model,
                                "Температура" to it.temperature,
                                "Промпт" to it.prompt,
                                "Размер промпта" to it.prompt.length,
                            )
                        })
                        """Цепочка для источника ${ctx.getArg(0)}, режима ${ctx.getArg(1)} (${chainAsString.length} узлов):
            
                        $chainAsString
                        """
                    },
                    { ctx -> "Не удалось получить цепочку для источника ${ctx.getArg(0)}, режима ${ctx.getArg(1)}" },
                )
            }
            CommandContext.Command.ADD_CHAIN -> {
                handleCommand(
                    commandContext,
                    { ctx -> aiArticleProcessingFacade.createSequence(ctx.getArg(0), ctx.getArg(1)) },
                    { ctx, chain ->
                        val chainAsString = FormReplyContext.writeForMessage(chain.map {
                            mapOf(
                                "Id узла" to it.id,
                                "Модель" to it.model,
                                "Температура" to it.temperature,
                                "Промпт" to it.prompt,
                                "Размер промпта" to it.prompt.length,
                            )
                        })
                        """Цепочка для источника ${ctx.getArg(0)}, режима ${ctx.getArg(1)} создана:
            
                        $chainAsString
                        """
                    },
                    { ctx -> "Не удалось создать цепочку для источника ${ctx.getArg(0)}, режима ${ctx.getArg(1)}" },
                )
            }
            CommandContext.Command.DELETE_CHAIN -> {
                handleCommand(
                    commandContext,
                    { ctx -> aiArticleProcessingFacade.deleteSequence(ctx.getArg(0), ctx.getArg(1)) },
                    { ctx, chain ->
                        val chainAsString = FormReplyContext.writeForMessage(chain.map {
                            mapOf(
                                "Id узла" to it.id,
                                "Модель" to it.model,
                                "Температура" to it.temperature,
                                "Промпт" to it.prompt,
                                "Размер промпта" to it.prompt.length,
                            )
                        })
                        """Цепочка для источника ${ctx.getArg(0)}, режима ${ctx.getArg(1)} удалена:
            
                        $chainAsString
                        """
                    },
                    { ctx -> "Не удалось удалить цепочку для источника ${ctx.getArg(0)}, режима ${ctx.getArg(1)}" },
                )
            }
            CommandContext.Command.ADD_NODE -> {
                handleFormCommand(
                    commandContext,
                    """Введите следующие данные для добавления узла: 
                    
                    ${
                        FormReplyContext.writeForMessage(
                            mapOf(
                                "prevNodeId" to "ID предыдущего узла (Обязательно)",
                                "model" to "Модель узла (По умолчанию: - ${Constants.DEFAULT_MODEL})",
                                "temperature" to "Температура узла (По умолчанию: - ${Constants.DEFAULT_TEMPERATURE})",
                                "prompt" to "Промпт узла (Обязательно)",
                            )
                        )
                    }
                    """,
                )
            }
            CommandContext.Command.DELETE_NODE -> {
                handleCommand(
                    commandContext,
                    { ctx -> aiArticleProcessingFacade.deleteNode(ctx.getArg(0).toLong()) },
                    { ctx, node ->
                        val nodeAsString = FormReplyContext.writeForMessage(mapOf(
                            "Id узла" to node.id,
                            "Модель" to node.model,
                            "Температура" to node.temperature,
                            "Промпт" to node.prompt,
                            "Размер промпта" to node.prompt.length,
                        ))
                        """Узел из цепочки для источника ${ctx.getArg(0)}, режима ${ctx.getArg(1)} удален:
            
                        $nodeAsString
                        """
                    },
                    { ctx -> "Не удалось удалить узел с id ${ctx.getArg(0)}" },
                )
            }
            CommandContext.Command.UPDATE_NODE -> {
                handleFormCommand(
                    commandContext,
                    """Введите следующие данные для обновления узла: 
                    
                    ${
                        FormReplyContext.writeForMessage(
                            mapOf(
                                "nodeId" to "ID узла (Обязательно)",
                                "model" to "Модель узла",
                                "temperature" to "Температура узла",
                                "prompt" to "Промпт узла"
                            )
                        )
                    }
                    """,
                )
            }
            CommandContext.Command.SHOW_POSTS -> {
                handleCommand(
                    commandContext,
                    { ctx -> articlesFacade.getArticles(Article.Status.valueOf(ctx.getArg(0).uppercase()), ctx.getArg(1).toInt()) },
                    { ctx, articles ->
                        val articlesAsString = FormReplyContext.writeForMessage(articles.second.map {
                            mapOf(
                                "Id статьи" to it.id,
                                "Источник" to it.source.name,
                                "Заголовок" to it.title,
                                "URL" to it.url,
                                "Создана" to it.createdAt.toString(),
                            )
                        })
                        """Страница ${ctx.getArg(1)}/${articles.first} статей в статусе ${ctx.getArg(0)}:
            
                        $articlesAsString
                        """
                    },
                    { ctx -> "Не удалось удалить узел с id ${ctx.getArg(0)}" },
                )
            }
            CommandContext.Command.SHOW_POST -> {
                handleCommand(
                    commandContext,
                    { ctx -> articlesFacade.getArticle(ctx.getArg(1).toLong()) },
                    { ctx, article ->
                        val articleAsString = FormReplyContext.writeForMessage(mapOf(
                            "Заголовок" to article.title,
                            "Источник" to article.source.name,
                            "URL" to article.url,
                            "Создана" to article.createdAt.toString(),
                            "Содержание" to article.content,
                            "Статус" to article.status,
                        )
                        )
                        """Статья c Id ${ctx.getArg(0)}:
            
                        $articleAsString
                        """
                    },
                    { ctx -> "Не удалось найти статью с id ${ctx.getArg(0)}" },
                )
            }
            CommandContext.Command.SHOW_COMMANDS -> {
                handleCommand(
                    commandContext,
                    {},
                    { _, _ ->
                        val commands = FormReplyContext.writeForMessage(
                            CommandContext.Command.entries.associate {
                                it.name to "${it.description}, аргументы: ${it.requiredArgs.joinToString(", ")}"
                            }
                        )
                        """Список команд:
                    
                        $commands
                        """
                    },
                    { "Не удалось получить список команд" }
                )
            }
        }
    }

    private fun onFormReply(formReplyContext: FormReplyContext) {
        when (formReplyContext.commandContext.command) {
            CommandContext.Command.ADD_SOURCE -> {
                handleFormCommandResponse(
                    formReplyContext,
                    { context -> sourcesFacade.addSource(SourcesFacade.SourceInput().apply {
                        context.values["name"]?.let {
                            name = Optional.of(it)
                        }
                        context.values["url"]?.let {
                            url = Optional.of(it)
                        }
                        context.values["schedule"]?.let {
                            scheduleDelay = Optional.of(Duration.ofMinutes(it.toLong()))
                        }
                        context.values["xpath"]?.let {
                            contentXPath = Optional.of(it)
                        }
                        context.values["channels"]?.let {
                            channels = Optional.of(it.split(",").map {
                                    name -> channelsFacade.getChannelByName(name.trim())
                            }.toMutableSet())
                        }
                        context.values["suspended"]?.toBoolean()?.let {
                            suspended = Optional.of(it)
                        }
                    })},
                    { _, source -> "Источник ${source.name} добавлен" },
                    { "Не удалось добавить источник" }
                )
            }
            CommandContext.Command.UPDATE_SOURCE -> {
                handleFormCommandResponse(
                    formReplyContext,
                    { context -> sourcesFacade.updateSource(
                        requireNotNull(context.values["currentName"]) { "Не указано имя обновляемого источника" },
                        SourcesFacade.SourceInput().apply {
                            context.values["newName"]?.let {
                                name = Optional.of(it)
                            }
                            context.values["url"]?.let {
                                url = Optional.of(it)
                            }
                            context.values["schedule"]?.let {
                                scheduleDelay = Optional.of(Duration.ofMinutes(it.toLong()))
                            }
                            context.values["xpath"]?.let {
                                contentXPath = Optional.of(it)
                            }
                            context.values["channels"]?.let {
                                channels = Optional.of(it.split(",").map {
                                        name -> channelsFacade.getChannelByName(name.trim())
                                }.toMutableSet())
                            }
                            context.values["suspended"]?.toBoolean()?.let {
                                suspended = Optional.of(it)
                            }
                        }
                    )},
                    { _, source -> "Источник ${source.name} обновлён" },
                    { "Не удалось обновить источник" }
                )
            }
            CommandContext.Command.ADD_CHANNEL -> {
                handleFormCommandResponse(
                    formReplyContext,
                    { context -> channelsFacade.addChannel(ChannelsFacade.ChannelInput().apply {
                        context.values["name"]?.let {
                            name = Optional.of(it)
                        }
                        context.values["id"]?.let {
                            id = Optional.of(it)
                        }
                        context.values["platform"]?.let {
                            platform = Optional.of(ChannelPlatform.valueOf(it.uppercase()))
                        }
                        context.values["sources"]?.let {
                            sources = Optional.of(it.split(",").map {
                                    name -> sourcesFacade.getSource(name.trim())
                            }.toMutableSet())
                        }
                    })},
                    { _, channel -> "Канал ${channel.name} добавлен" },
                    { "Не удалось добавить канал" }
                )
            }
            CommandContext.Command.ADD_NODE -> {
                handleFormCommandResponse(
                    formReplyContext,
                    { context -> aiArticleProcessingFacade.insertNode(
                        requireNotNull(context.values["prevNodeId"]) { "ID предыдущего узла обязателен" }.toLong(),
                        AIArticleProcessingFacade.AIArticleProcessingNodeInput().apply {
                            context.values["model"]?.let {
                                model = Optional.of(it)
                            }
                            context.values["temperature"]?.let {
                                temperature = Optional.of(it.toDouble())
                            }
                            context.values["prompt"]?.let {
                                prompt = Optional.of(it)
                            }
                        }
                    )},
                    { _, node -> "Узел ${node.id} добавлен в цепочку источника ${node.source.name}, режима ${node.mode}" },
                    { "Не удалось добавить узел" },
                )
            }
            CommandContext.Command.UPDATE_NODE -> {
                handleFormCommandResponse(
                    formReplyContext,
                    { context -> aiArticleProcessingFacade.updateNode(
                        requireNotNull(context.values["nodeId"]) { "ID узла обязателен" }.toLong(),
                        AIArticleProcessingFacade.AIArticleProcessingNodeInput().apply {
                            context.values["model"]?.let {
                                model = Optional.of(it)
                            }
                            context.values["temperature"]?.let {
                                temperature = Optional.of(it.toDouble())
                            }
                            context.values["prompt"]?.let {
                                prompt = Optional.of(it)
                            }
                        }
                    )},
                    { _, node -> "Узел ${node.id} обновлён в цепочке источника ${node.source.name}, режима ${node.mode}" },
                    { "Не удалось обновить узел" },
                )
            }
            else -> {
                LOG.warn("От команды ${formReplyContext.commandContext.command} не ожидался ввод данных")
            }
        }
    }

    private fun onPostAction(postActionContext: PostActionContext) {
        // TODO: this
    }

    private fun handleFormCommand(
        context: CommandContext,
        schemaMessage: String,
    ) {
        // Send form message
        val message = sendMessageToAdmins(
            message = schemaMessage,
            replyToMessageId = context.update.message.messageId,
        )

        // Set as in action
        commandsInAction[message.messageId] = context
    }

    private fun <T> handleFormCommandResponse(
        context: FormReplyContext,
        action: (FormReplyContext) -> T,
        successMessage: (FormReplyContext, T) -> String,
        errorMessage: (FormReplyContext) -> String
    ) {
        handleCommand(
            context.commandContext,
            { action(context) },
            { _, result -> successMessage(context, result) },
            { errorMessage(context) }
        )
    }

    private fun <T> handleCommand(
        context: CommandContext,
        action: (CommandContext) -> T,
        successMessage: (CommandContext, T) -> String,
        errorMessage: (CommandContext) -> String
    ) {
        try {
            action(context).also {
                try {
                    sendMessageToAdmins(
                        successMessage(context, it),
                        context.update.message.messageId
                    )
                } catch (e: Exception) {
                    LOG.warn("Не удалось оповестить об успешно выполненном действии ${context.command}", e)
                } finally {
                    commandsInAction.remove(context.update.message.messageId)
                }
            }
        } catch (actionException: Exception) {
            LOG.error("Ошибке команды ${context.command}, сообщение ${context.update.message.messageId}", actionException)
            try {
                sendMessageToAdmins(
                    "${errorMessage(context)}: ${actionException.message}",
                    context.update.message.messageId
                )
            } catch (exception: Exception) {
                LOG.error(
                    "Не удалось оповестить об ошибке команды ${context.command}, " +
                            "сообщение ${context.update.message.messageId}",
                    exception
                )
            }
        }
    }

    private fun sendMessageToAdmins(
        message: String,
        replyToMessageId: Int? = null,
        replyMarkup: ReplyKeyboardMarkup? = null
    ): Message {
        return sendApiMethod(SendMessage().apply {
            setChatId(adminChannel)
            enableHtml(true)
            disableWebPagePreview()
            replyMarkup?.let { setReplyMarkup(it) }
            replyToMessageId?.let { setReplyToMessageId(it) }
            text = message
        })
    }

    private fun sendDeleteMessageToAdmins(messageId: Int): Boolean {
        return sendApiMethod(DeleteMessage().apply {
            setChatId(adminChannel)
            setMessageId(messageId)
        })
    }

    private fun sendEditMessageToAdmins(messageId: Int, text: String) {
        sendApiMethod(EditMessageText().apply {
            setChatId(adminChannel)
            setMessageId(messageId)
            setText(text)
        })
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

    override fun getBotUsername(): String = name

    companion object {
        private val PLATFORM = ChannelPlatform.TELEGRAM
        private val LOG = LogManager.getLogger(VestnikTelegramAssistantFacade::class.java)
    }

    override fun notifyNewArticle(article: Article) {
        TODO("Not yet implemented")
    }
}