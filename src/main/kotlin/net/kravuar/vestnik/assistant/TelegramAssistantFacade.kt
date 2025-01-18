package net.kravuar.vestnik.assistant

import dev.inmo.micro_utils.common.alsoIfFalse
import dev.inmo.micro_utils.coroutines.defaultSafelyWithoutExceptionHandlerWithNull
import dev.inmo.micro_utils.coroutines.launchSafelyWithoutExceptions
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.bot.exceptions.MessageIsNotModifiedException
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.delete
import dev.inmo.tgbotapi.extensions.api.deleteMessage
import dev.inmo.tgbotapi.extensions.api.edit.reply_markup.editMessageReplyMarkup
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviour
import dev.inmo.tgbotapi.extensions.behaviour_builder.createSubContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMediaContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.SimpleFilter
import dev.inmo.tgbotapi.extensions.utils.extensions.sameMessage
import dev.inmo.tgbotapi.extensions.utils.flatMap
import dev.inmo.tgbotapi.extensions.utils.formatting.boldHTML
import dev.inmo.tgbotapi.extensions.utils.formatting.linkHTML
import dev.inmo.tgbotapi.extensions.utils.formatting.makeChatLink
import dev.inmo.tgbotapi.extensions.utils.formatting.makeLinkToMessage
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.updates.retrieving.longPolling
import dev.inmo.tgbotapi.extensions.utils.withContent
import dev.inmo.tgbotapi.extensions.utils.withUserOrThrow
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.MessageId
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.commands.BotCommandScope
import dev.inmo.tgbotapi.types.message.HTML
import dev.inmo.tgbotapi.types.message.abstracts.AccessibleMessage
import dev.inmo.tgbotapi.types.message.abstracts.CommonMessage
import dev.inmo.tgbotapi.types.message.abstracts.ContentMessage
import dev.inmo.tgbotapi.types.message.content.MediaContent
import dev.inmo.tgbotapi.types.message.content.MediaGroupContent
import dev.inmo.tgbotapi.types.message.content.MessageContent
import dev.inmo.tgbotapi.types.message.content.PhotoContent
import dev.inmo.tgbotapi.types.message.content.TextContent
import dev.inmo.tgbotapi.types.message.content.TextMessage
import dev.inmo.tgbotapi.types.message.content.VideoContent
import dev.inmo.tgbotapi.types.queries.callback.MessageCallbackQuery
import dev.inmo.tgbotapi.types.queries.callback.MessageDataCallbackQuery
import dev.inmo.tgbotapi.utils.PreviewFeature
import dev.inmo.tgbotapi.utils.extensions.toHtml
import dev.inmo.tgbotapi.utils.internal.htmlSpoilerClosingControl
import dev.inmo.tgbotapi.utils.internal.htmlSpoilerControl
import dev.inmo.tgbotapi.utils.row
import jakarta.transaction.Transactional
import korlibs.time.max
import korlibs.time.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.channels.ChannelPlatform
import net.kravuar.vestnik.channels.ChannelsFacade
import net.kravuar.vestnik.commons.Constants
import net.kravuar.vestnik.post.Post
import net.kravuar.vestnik.processor.ProcessedArticle
import net.kravuar.vestnik.processor.ProcessedArticlesFacade
import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import java.util.Optional
import java.util.concurrent.CancellationException
import java.util.function.Predicate
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

private data class MessageWithReplyMarkup(
    val message: String,
    val replyMarkup: InlineKeyboardMarkup?
)

private class AssistantActionException(
    val replyTo: AccessibleMessage? = null,
    val assistantMessage: ContentMessage<MessageContent>? = null,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException()

private class AssistantServiceException(
    val assistantMessage: ContentMessage<MessageContent>,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException()

private enum class Command(
    val commandName: String,
    val description: String,
    val args: List<Arg>,
) {
    SHOW_SOURCES("show_sources", "Показать список источников", emptyList()),
    SHOW_SOURCE("show_source", "Показать источник", listOf(Arg("name", "Имя источника"))),
    ADD_SOURCE("add_source", "Добавить источник", emptyList()),
    DELETE_SOURCE("delete_source", "Удалить источник по имени", listOf(Arg("name", "Имя источника"))),
    UPDATE_SOURCE("update_source", "Обновить источник", emptyList()),
    SHOW_CHANNELS("show_channels", "Показать список каналов", listOf(Arg("source", "Имя источника", true))),
    ADD_CHANNEL("add_channel", "Добавить канал", emptyList()),
    DELETE_CHANNEL("delete_channel", "Удалить канал по имени", listOf(Arg("name", "Имя канала"))),
    SHOW_CHAINS("show_chains", "Показать цепочки обработки статей", listOf(Arg("source", "Имя источника", true))),
    SHOW_MODES(
        "show_modes",
        "Показать режимы обработки статей для источника",
        listOf(Arg("source", "Имя источника", true))
    ),
    SHOW_CHAIN(
        "show_chain",
        "Показать конкретную цепочку",
        listOf(Arg("source", "Имя источника", true), Arg("mode", "Имя режима"))
    ),
    ADD_CHAIN(
        "add_chain",
        "Добавить цепочку обработки статьи для конкретного источника",
        listOf(Arg("source", "Имя источника", true), Arg("mode", "Имя режима"))
    ),
    DELETE_CHAIN(
        "delete_chain",
        "Удалить цепочку обработки статьи",
        listOf(Arg("source", "Имя источника", true), Arg("mode", "Имя режима"))
    ),
    ADD_NODE("add_node", "Добавить узел после указанного узла", emptyList()),
    DELETE_NODE("delete_node", "Удалить узел обработки статьи", listOf(Arg("id", "ID узла"))),
    UPDATE_NODE("update_node", "Обновить узел обработки статьи", emptyList()),
    SHOW_COMMANDS("show_commands", "Показать список команд", emptyList());

    data class Arg(
        val name: String,
        val description: String,
        val optional: Boolean = false
    ) {
        override fun toString(): String {
            return description + if (optional) {
                " (опционален)"
            } else {
                ""
            }
        }
    }
}

@OptIn(PreviewFeature::class)
internal class TelegramAssistantFacade(
    adminChannelId: Long,
    adminsIds: Set<Long>,
    ownerId: Long,
    private val bot: TelegramBot,
    private val config: AssistantProperties,
    private val sourcesFacade: SourcesFacade,
    private val channelsFacade: ChannelsFacade,
    private val articlesFacade: ArticlesFacade,
    private val processedArticlesFacade: ProcessedArticlesFacade,
    private val aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade
) : AssistantFacade {
    private val owner: UserId = UserId(RawChatId(ownerId))
    private val admins: Set<UserId> = adminsIds.map { UserId(RawChatId(it)) }.toSet()
    private val adminChannel: ChatId = ChatId(RawChatId(adminChannelId))
    private val adminMessageFilter = SimpleFilter<CommonMessage<*>> {
        (it.chat.id == adminChannel && it.withUserOrThrow().user.id in admins).alsoIfFalse {
            LOG.warn("Сообщение не прошло проверку безопасности: $it")
        }
    }
    private val adminCallbackFilter = SimpleFilter<MessageCallbackQuery> {
        (it.message.chat.id == adminChannel && it.withUserOrThrow().user.id in admins).alsoIfFalse {
            LOG.warn("Callback не прошёл проверку безопасности: $it")
        }
    }

    override fun notifyNewArticle(article: Article) {
        LOG.info("Оповещение о новой статье $article")

        val modes = processedArticlesFacade.getModes(article, 1)
        if (modes.totalPages == 0) {
            throw IllegalStateException("Отсутствуют режимы для обработки новости")
        }
        runBlocking {
            bot.send(
                chatId = adminChannel,
                text = newArticleMessage(article),
                markup = selectArticleModeMarkup(
                    requireNotNull(article.id) {
                        "ID обрабатываемой статьи не может отсутствовать."
                    },
                    modes.content,
                    1,
                    modes.totalPages
                ),
            )
        }
    }

    internal suspend fun start(): Job {
        val behaviour = bot.buildBehaviour(defaultExceptionsHandler = {
            LOG.error("Ошибка во время работы ассистента", it)
            when (it) {
                is MessageIsNotModifiedException -> {
                    LOG.warn("Ошибка модификации сообщения, изменённое совпадает с текущем", it)
                }

                is AssistantActionException -> {
                    try {
                        if (it.replyTo != null) {
                            bot.reply(
                                message = it.replyTo,
                                text = it.message
                            )
                        } else {
                            requireNotNull(it.assistantMessage) {
                                "Не задано ни сообщение для reply, ни сообщение ассистента для редактирования"
                            }.run {
                                with(
                                    this.withContent<TextContent>()
                                        ?: throw IllegalStateException("Ошибка с сообщением для редактирования не содержит текстового контента")
                                ) {
                                    bot.edit(
                                        message = this,
                                        text = it.message +
                                                "\n" +
                                                this.content.text.boldHTML()
                                    )
                                }
                            }
                        }

                    } catch (exception: Throwable) {
                        LOG.error("Не удалось оповестить об ошибке $it, по причине: $exception")
                    }
                }

                is AssistantServiceException -> {
                    try {
                        bot.send(
                            chatId = adminChannel,
                            text = "Ошибка: ${it.message}"
                        )
                    } catch (exception: Throwable) {
                        LOG.error("Не удалось оповестить о сервисной ошибке: $it")
                    }
                    try {
                        bot.deleteMessage(
                            it.assistantMessage
                        )
                    } catch (exception: Throwable) {
                        LOG.error("Не удалось удалить сообщение ${it.assistantMessage.messageId} ассистента с ошибкой: $it")
                    }
                }

                is CancellationException -> {
                    LOG.fatal("Внутренняя ошибка coroutine и процессов обработки")
                }

                else -> {
                    LOG.error("Произошла непредвиденная ошибка. ${it.message ?: it}")
                    try {
                        bot.send(
                            chatId = adminChannel,
                            text = "Произошла непредвиденная ошибка: ${it.message ?: it}"
                        )
                    } catch (exception: Throwable) {
                        LOG.error("Не удалось оповестить о непредвиденной ошибке: ${it.message ?: it}")
                    }
                }
            }
        }) {
            // ARTICLE MODES PAGINATION
            onMessageDataCallbackQuery(
                dataRegex = ARTICLE_MODES_PAGE_REGEX,
                initialFilter = adminCallbackFilter,
                markerFactory = null
            ) { paginationCallback ->
                val (articleId, page) = getArticleModesData(paginationCallback.data)
                val article = articlesFacade.getArticle(articleId)
                val modes = processedArticlesFacade.getModes(article, page)

                edit(
                    message = paginationCallback.message.withContent()
                        ?: throw IllegalStateException("Некорректный callback для выбора страницы режимов обработки статьи"),
                    text = newArticleMessage(article),
                    markup = selectArticleModeMarkup(
                        requireNotNull(article.id) {
                            "ID обрабатываемой статьи не может отсутствовать."
                        },
                        modes.content,
                        page,
                        modes.totalPages
                    ),
                )
            }

            // PROCESS ARTICLE HANDLER
            onMessageDataCallbackQuery(
                dataRegex = PROCESS_ARTICLE_REGEX,
                initialFilter = adminCallbackFilter,
                markerFactory = null,
            ) { processCallback ->
                val (articleId, mode) = getProcessArticleData(processCallback.data)
                LOG.info("Получен запрос на обработку статьи $articleId, режим $mode, сообщение ${processCallback.message.messageId}")

                val article = articlesFacade.getArticle(articleId)

                val articleMessage = reply(
                    message = processCallback.message,
                    text = "Обрабатываю статью в режиме $mode...",
                )

                with(createSubContext()) {
                    launchSafelyWithoutExceptions {
                        try {
                            val processedArticle = processedArticlesFacade.processArticle(article, mode)
                            this@with.handleProcessedArticleInteraction(articleMessage, processedArticle)
                        } catch (throwable: Throwable) {
                            throw AssistantServiceException(
                                assistantMessage = articleMessage,
                                message = "Ошибка обработки статьи: ${throwable.message}",
                                cause = throwable
                            )
                        }
                    }.also {
                        LOG.debug("Запущен процесс обработки статьи $articleId, сообщение ${articleMessage.messageId}")
                    }.invokeOnCompletion {
                        LOG.debug("Завершён процесс обработки статьи $articleId, сообщение ${articleMessage.messageId}")
                    }
                }
            }

            //
            // ADMINISTRATION COMMANDS HANDLERS
            //

            onCommand(
                command = Command.SHOW_SOURCES.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                val pageSupplier = { page: Int ->
                    val sources = sourcesFacade.getSources(page)
                    val sourcesAsString = writeForMessage(sources.content.map {
                        mapOf(
                            "Id" to it.id,
                            "Name" to it.name,
                            "URL" to it.url,
                            "Периодичность" to it.scheduleDelay.toKotlinDuration().toString(),
                            "Приостановлен" to if (it.suspended == true) {
                                "да"
                            } else {
                                "нет"
                            },
                            "Удалён" to it.deleted
                        )
                    })

                    MessageWithReplyMarkup(
                        "Список источников" + if (sources.content.isNotEmpty()) {
                            "\n" + sourcesAsString
                        } else {
                            " пуст"
                        },
                        simplePaginationMarkup(page, sources.totalPages)
                    )
                }
                handleCallbackCommand(
                    Command.SHOW_SOURCES,
                    null,
                    { PAGE_REGEX.matches(it) },
                    { _ -> pageSupplier(1) },
                    userMessage,
                    { _, message, callback ->
                        val page = pageSupplier(callback.toInt())
                        edit(
                            message = message,
                            text = page.message,
                            markup = page.replyMarkup
                        )
                    }
                )
            }

            onCommandWithArgs(
                command = Command.SHOW_SOURCE.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                handleArgumentCommand(
                    Command.SHOW_SOURCE,
                    userMessage,
                    args,
                    { argsByName ->
                        sourcesFacade.getSourceByName(
                            requireNotNull(argsByName["name"]) { "Имя источника является обязательным" }
                        )
                    },
                    { argsByName, source ->
                        val sourceAsString = writeForMessage(
                            mapOf(
                                "Id" to source.id,
                                "URL" to source.url,
                                "Периодичность" to source.scheduleDelay.toKotlinDuration().toString(),
                                "XPATH к контенту" to source.contentXPath,
                                "Целевые Каналы" to source.channels.joinToString { channel -> channel.chatLink() },
                                "Приостановлен" to if (source.suspended == true) {
                                    "да"
                                } else {
                                    "нет"
                                },
                            )
                        )
                        "Источник ${argsByName["name"]!!}:" +
                                "\n" +
                                sourceAsString
                    },
                    { _ -> "Не удалось найти источник" }
                )
            }

            onCommand(
                command = Command.ADD_SOURCE.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                handleFormCommand(
                    Command.ADD_SOURCE,
                    userMessage,
                    "Введите следующие данные для добавления источника:" +
                            "\n" +
                            writeForMessage(
                                mapOf(
                                    "name" to "Имя",
                                    "url" to "URL",
                                    "schedule" to "Периодичность (в минутах)",
                                    "xpath" to "XPATH к контенту",
                                    "channels" to "Целевые каналы (имена через запятую)",
                                    "suspended" to "Приостановлен (опционально)",
                                )
                            ),
                    { input ->
                        sourcesFacade.addSource(SourcesFacade.SourceInput().apply {
                            input["name"]?.let {
                                name = Optional.of(it)
                            }
                            input["url"]?.let {
                                url = Optional.of(it)
                            }
                            input["schedule"]?.let {
                                scheduleDelay = Optional.of(java.time.Duration.ofMinutes(it.toLong()))
                            }
                            input["xpath"]?.let {
                                contentXPath = Optional.of(it)
                            }
                            input["channels"]?.let {
                                channels = Optional.of(it.split(",").map { name ->
                                    channelsFacade.getChannelByName(name.trim())
                                }.toMutableSet())
                            }
                            input["suspended"]?.toBoolean()?.let {
                                suspended = Optional.of(it)
                            }
                        })
                    },
                    { _, source -> "Источник ${source.name} добавлен" },
                    { _ -> "Не удалось добавить источник" }
                )
            }

            onCommand(
                command = Command.UPDATE_SOURCE.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                handleFormCommand(
                    Command.UPDATE_SOURCE,
                    userMessage,
                    "Введите необходимые данные для обновления источника:" +
                            "\n" +
                            writeForMessage(
                                mapOf(
                                    "name" to "Имя обновляемого источника (обязательно)",
                                    "newName" to "Имя",
                                    "url" to "URL",
                                    "schedule" to "Периодичность (в минутах)",
                                    "xpath" to "XPATH к контенту",
                                    "channels" to "Целевые каналы (имена через запятую)",
                                    "suspended" to "Приостановлен",
                                )
                            ),
                    { input ->
                        sourcesFacade.updateSource(
                            requireNotNull(input["name"]) { "Не указано имя обновляемого источника" },
                            SourcesFacade.SourceInput().apply {
                                input["newName"]?.let {
                                    name = Optional.of(it)
                                }
                                input["url"]?.let {
                                    url = Optional.of(it)
                                }
                                input["schedule"]?.let {
                                    scheduleDelay = Optional.of(java.time.Duration.ofMinutes(it.toLong()))
                                }
                                input["xpath"]?.let {
                                    contentXPath = Optional.of(it)
                                }
                                input["channels"]?.let {
                                    channels = Optional.of(it.split(",").map { name ->
                                        channelsFacade.getChannelByName(name.trim())
                                    }.toMutableSet())
                                }
                                input["suspended"]?.toBoolean()?.let {
                                    suspended = Optional.of(it)
                                }
                            })
                    },
                    { _, source -> "Источник ${source.name} обновлён" },
                    { _ -> "Не удалось обновить источник" }
                )
            }

            onCommandWithArgs(
                command = Command.DELETE_SOURCE.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                handleArgumentCommand(
                    Command.DELETE_SOURCE,
                    userMessage,
                    args,
                    { argsByName ->
                        sourcesFacade.deleteSource(
                            requireNotNull(argsByName["name"]) { "Имя источника является обязательным" }
                        )
                    },
                    { argsByName, deleted ->
                        if (deleted) {
                            "Источник ${argsByName["name"]!!} удален"
                        } else {
                            "Не найдено источника с именем ${argsByName["name"]!!} для удаления"
                        }
                    },
                    { _ -> "Не удалось удалить источник" }
                )
            }

            onCommandWithArgs(
                command = Command.SHOW_CHANNELS.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                val pageSupplier = { source: String?, page: Int ->
                    val channels = channelsFacade.getChannels(
                        source?.let { sourcesFacade.getSourceByName(it) },
                        page
                    )
                    val channelsAsString = writeForMessage(channels.content.map {
                        mapOf(
                            "Id" to it.id,
                            "Имя" to it.name,
                            "Платформа" to it.platform,
                            "Источники" to it.sources.joinToString(", ") { source -> source.name },
                            "Удалён" to it.deleted,
                        )
                    })

                    MessageWithReplyMarkup(
                        "Список каналов" + if (channels.content.isNotEmpty()) {
                            "\n" + channelsAsString
                        } else {
                            " пуст"
                        },
                        simplePaginationMarkup(page, channels.totalPages)
                    )
                }
                handleCallbackCommand(
                    Command.SHOW_CHANNELS,
                    args,
                    { PAGE_REGEX.matches(it) },
                    { argsByName -> pageSupplier(argsByName["source"], 1) },
                    userMessage,
                    { argsByName, message, callback ->
                        val page = pageSupplier(argsByName["source"], callback.toInt())
                        edit(
                            message = message,
                            text = page.message,
                            markup = page.replyMarkup
                        )
                    }
                )
            }

            onCommand(
                command = Command.ADD_CHANNEL.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                handleFormCommand(
                    Command.ADD_CHANNEL,
                    userMessage,
                    "Введите следующие данные для добавления канала:" +
                            "\n" +
                            writeForMessage(
                                mapOf(
                                    "id" to "ID канала",
                                    "name" to "Имя канала",
                                    "platform" to "Платформа (${ChannelPlatform.entries.joinToString("/") { it.name }})",
                                    "sources" to "Источники (имена через запятую)",
                                )
                            ),
                    @Transactional() { input ->
                        channelsFacade.addChannel(ChannelsFacade.ChannelInput().apply {
                            input["name"]?.let {
                                name = Optional.of(it)
                            }
                            input["id"]?.let {
                                id = Optional.of(it.toLong())
                            }
                            input["platform"]?.let {
                                platform = Optional.of(ChannelPlatform.valueOf(it.uppercase()))
                            }
                            input["sources"]?.let {
                                sources = Optional.of(it.split(",").map { name ->
                                    sourcesFacade.getSourceByName(name.trim())
                                }.toMutableSet())
                            }
                        })
                    },
                    { _, channel -> "Канал ${channel.chatLink()} добавлен" },
                    { _ -> "Не удалось добавить канал" }
                )
            }

            onCommandWithArgs(
                command = Command.DELETE_CHANNEL.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                handleArgumentCommand(
                    Command.DELETE_CHANNEL,
                    userMessage,
                    args,
                    { argsByName ->
                        channelsFacade.deleteChannel(
                            requireNotNull(argsByName["name"]) { "Имя канала является обязательным" }
                        )
                    },
                    { argsByName, deleted ->
                        if (deleted) {
                            "Канал ${argsByName["name"]!!} удален"
                        } else {
                            "Не найдено канала с именем ${argsByName["name"]!!} для удаления"
                        }
                    },
                    { _ -> "Не удалось удалить канал" }
                )
            }

            onCommandWithArgs(
                command = Command.SHOW_CHAINS.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                val pageSupplier = { source: String?, page: Int ->
                    val chains = aiArticleProcessingNodesFacade.getChains(
                        source?.let { sourcesFacade.getSourceByName(source) },
                        page
                    )
                    val chainsAsString = writeForMessage(chains.content.map {
                        mutableMapOf(
                            "Id Корня" to it.id,
                            "Режим" to it.mode,
                        ).also { map ->
                            it.source?.run { map["Источник"] = name }
                        }
                    })

                    MessageWithReplyMarkup(
                        "Список цепочек обработки новости" + if (chains.content.isNotEmpty()) {
                            "\n" + chainsAsString
                        } else {
                            " пуст"
                        },
                        simplePaginationMarkup(page, chains.totalPages)
                    )
                }
                handleCallbackCommand(
                    Command.SHOW_CHAINS,
                    args,
                    { PAGE_REGEX.matches(it) },
                    { argsByName -> pageSupplier(argsByName["source"], 1) },
                    userMessage,
                    { argsByName, message, callback ->
                        val page = pageSupplier(argsByName["source"], callback.toInt())
                        edit(
                            message = message,
                            text = page.message,
                            markup = page.replyMarkup
                        )
                    }
                )
            }

            onCommandWithArgs(
                command = Command.SHOW_MODES.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                val pageSupplier = { source: String?, page: Int ->
                    val modes = aiArticleProcessingNodesFacade.getModes(
                        source?.let { sourcesFacade.getSourceByName(source) },
                        page
                    )
                    MessageWithReplyMarkup(
                        "Список режимов для источника $source" + if (modes.content.isNotEmpty()) {
                            ": " + modes.content.joinToString()
                        } else {
                            " пуст"
                        },
                        simplePaginationMarkup(page, modes.totalPages)
                    )
                }
                handleCallbackCommand(
                    Command.SHOW_MODES,
                    args,
                    { PAGE_REGEX.matches(it) },
                    { argsByName ->
                        pageSupplier(
                            argsByName["source"],
                            1
                        )
                    },
                    userMessage,
                    { argsByName, message, callback ->
                        val page = pageSupplier(argsByName["source"], callback.toInt())
                        edit(
                            message = message,
                            text = page.message,
                            markup = page.replyMarkup
                        )
                    }
                )
            }

            onCommandWithArgs(
                command = Command.SHOW_CHAIN.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                handleArgumentCommand(
                    Command.SHOW_CHAIN,
                    userMessage,
                    args,
                    { argsByName ->
                        aiArticleProcessingNodesFacade.getChainMode(
                            argsByName["source"]?.let { sourcesFacade.getSourceByName(it) },
                            requireNotNull(argsByName["mode"]) { "Режим обработки является обязательным" },
                        )
                    },
                    { argsByName, chain ->
                        val chainAsString = writeForMessage(chain.map {
                            mapOf(
                                "Id узла" to it.id,
                                "Модель" to it.model,
                                "Температура" to it.temperature,
                                "Размер промпта" to it.prompt.length,
                                "Промпт" to it.prompt,
                            )
                        })
                        "Цепочка (${chain.size} узлов) для режима ${argsByName["mode"]!!}" + (argsByName["source"]?.let {
                            ", источника $it:"
                        } ?: ":") +
                                "\n" +
                                chainAsString
                    },
                    { _ -> "Не удалось найти цепочку" }
                )
            }

            onCommandWithArgs(
                command = Command.ADD_CHAIN.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                handleArgumentCommand(
                    Command.ADD_CHAIN,
                    userMessage,
                    args,
                    { argsByName ->
                        aiArticleProcessingNodesFacade.createChain(
                            argsByName["source"]?.let { sourcesFacade.getSourceByName(it) },
                            requireNotNull(argsByName["mode"]) { "Режим обработки является обязательным" },
                        )
                    },
                    { argsByName, _ ->
                        "Создана цепочка обработки источника с именем режима ${argsByName["mode"]!!}" + (argsByName["source"]?.let {
                            ", источника $it"
                        } ?: "")
                    },
                    { _ -> "Не удалось добавить цепочку обработки новостей" }
                )
            }

            onCommandWithArgs(
                command = Command.DELETE_CHAIN.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                handleArgumentCommand(
                    Command.DELETE_CHAIN,
                    userMessage,
                    args,
                    { argsByName ->
                        aiArticleProcessingNodesFacade.deleteChain(
                            argsByName["source"]?.let { sourcesFacade.getSourceByName(it) },
                            requireNotNull(argsByName["mode"]) { "Режим обработки является обязательным" },
                        )
                    },
                    { argsByName, _ ->
                        "Удалена цепочка обработки с именем режима ${argsByName["mode"]!!}" + (argsByName["source"]?.let {
                            ", источника $it"
                        } ?: "")
                    },
                    { _ -> "Не удалось удалить цепочку обработки новостей" }
                )
            }

            onCommand(
                command = Command.ADD_NODE.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                handleFormCommand(
                    Command.ADD_NODE,
                    userMessage,
                    "Введите следующие данные для добавления узла:" +
                            "\n" +
                            writeForMessage(
                                mapOf(
                                    "prevNodeId" to "ID предыдущего узла (Обязательно)",
                                    "model" to "Модель узла (По умолчанию: - ${Constants.DEFAULT_MODEL})",
                                    "temperature" to "Температура узла (По умолчанию: - ${Constants.DEFAULT_TEMPERATURE})",
                                    "prompt" to "Промпт узла (Обязательно)",
                                )
                            ),
                    { input ->
                        aiArticleProcessingNodesFacade.insertNode(
                            requireNotNull(input["prevNodeId"]) { "ID предыдущего узла обязателен" }.toLong(),
                            AIArticleProcessingNodesFacade.AIArticleProcessingNodeInput().apply {
                                input["model"]?.let {
                                    model = Optional.of(it)
                                }
                                input["temperature"]?.let {
                                    temperature = Optional.of(it.toDouble())
                                }
                                input["prompt"]?.let {
                                    prompt = Optional.of(it)
                                }
                            })
                    },
                    { _, node -> "Узел ${node.id} добавлен в цепочку для режима ${node.mode}" + node.source?.let { ", источника ${it.name}" } },
                    { "Не удалось добавить узел" },
                )
            }

            onCommandWithArgs(
                command = Command.DELETE_NODE.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                handleArgumentCommand(
                    Command.DELETE_NODE,
                    userMessage,
                    args,
                    { argsByName ->
                        aiArticleProcessingNodesFacade.deleteNode(requireNotNull(argsByName["id"]) {
                            "ID удаляемого узла является обязательным"
                        }.toLong())
                    },
                    { argsByName, deleted ->
                        if (deleted) {
                            "Узел ${argsByName["id"]!!} удален"
                        } else {
                            "Не найдено узла с id ${argsByName["id"]!!} для удаления"
                        }
                    },
                    { _ -> "Не удалось удалить узел обработки" }
                )
            }

            onCommand(
                command = Command.UPDATE_NODE.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                handleFormCommand(
                    Command.UPDATE_NODE,
                    userMessage,
                    "Введите следующие данные для обновления узла:" +
                            "\n" +
                            writeForMessage(
                                mapOf(
                                    "nodeId" to "ID узла (Обязательно)",
                                    "model" to "Модель узла",
                                    "temperature" to "Температура узла",
                                    "prompt" to "Промпт узла"
                                )
                            ),
                    { input ->
                        aiArticleProcessingNodesFacade.updateNode(
                            requireNotNull(input["nodeId"]) { "ID узла обязателен" }.toLong(),
                            AIArticleProcessingNodesFacade.AIArticleProcessingNodeInput().apply {
                                input["model"]?.let {
                                    model = Optional.of(it)
                                }
                                input["temperature"]?.let {
                                    temperature = Optional.of(it.toDouble())
                                }
                                input["prompt"]?.let {
                                    prompt = Optional.of(it)
                                }
                            })
                    },
                    { _, node -> "Узел ${node.id} обновлён в цепочке режима ${node.mode}" + node.source?.let { ", источника ${it.name}" } },
                    { "Не удалось обновить узел" },
                )
            }

            setMyCommands(
                Command.entries.map { command ->
                    BotCommand(
                        command.commandName,
                        command.description + if (command.args.isNotEmpty()) {
                            " аргументы: ${command.args.joinToString(", ") { it.toString() }}"
                        } else {
                            ""
                        }
                    )
                },
                scope = BotCommandScope.Chat(adminChannel)
            )

            allUpdatesFlow.subscribeSafelyWithoutExceptions(this) {
                LOG.trace(it)
            }
        }
        return bot.longPolling(behaviour).also {
            LOG.info("Работа ассистента запущена, конфигурация: $config")
            it.invokeOnCompletion {
                LOG.info("Работа ассистента закончена")
            }
        }
    }

    private data class PublicationInfo(
        var primaryChannel: Channel,
        var forwardChannels: List<Channel>,
        var delay: Duration = Duration.ZERO,
        var mediaList: MutableList<ChannelsFacade.Media> = mutableListOf()
    )

    private suspend fun <BS : BehaviourContext> BS.handleProcessedArticlePublishing(
        processedArticleMessage: ContentMessage<TextContent>,
        selectedChannels: List<Channel>,
        processedArticle: ProcessedArticle,
    ) {
        // Multistep final form
        val finalFormMessage: AccessibleMessage

        // Select primary channel
        finalFormMessage = reply(
            message = processedArticleMessage,
            text = primaryChannelSelectionMessage(),
            markup = primaryChannelSelectionMarkup(selectedChannels)
        )

        withNotifyingTimeout(config.articleLifeTime.toKotlinDuration(), finalFormMessage) {
            // Primary channel selection handling
            val publicationInfo = with(
                waitMessageDataCallbackQuery()
                    .first { callback ->
                        callback.message.sameMessage(finalFormMessage) && adminCallbackFilter.invoke(callback)
                    }.data
            ) {
                selectedChannels.first {
                    it.id == getSelectData(this)
                }
            }.let { primaryChannel ->
                PublicationInfo(
                    primaryChannel,
                    selectedChannels
                        .filter { channel -> channel.name != primaryChannel.name }
                )
            }

            // Select delay
            edit(
                message = finalFormMessage,
                text = finalPostFormMessage(publicationInfo),
                markup = finalPostFormMarkup(publicationInfo)
            )

            merge(
                waitMessageDataCallbackQuery().filter {
                    it.message.sameMessage(finalFormMessage) && adminCallbackFilter.invoke(it)
                }.onEach {
                    LOG.debug("Получено изменение задержки публикации для обработанной статьи ${processedArticle.id}, сообщение ${finalFormMessage.messageId}")
                },
                waitMediaContentMessage().filter {
                    it.replyTo?.sameMessage(finalFormMessage) ?: false && adminMessageFilter.invoke(it)
                }.onEach {
                    LOG.debug("Получены медиа файлы ${it.content.javaClass}, сообщение ${it.messageId}, обработанная статья ${processedArticle.id}")
                }.flatMap { message ->
                    message.content.let { content ->
                        when (content) {
                            is MediaGroupContent<*> -> {
                                content.group.map { part ->
                                    part.content
                                }
                            }

                            else -> {
                                listOf(content)
                            }
                        }
                    }
                }
            ).onEach { update ->
                when (update) {
                    is MessageDataCallbackQuery -> {
                        when {
                            // Handle delay adjustment
                            DELAY_DELTA_REGEX.matches(update.data) -> {
                                val deltaMinutes = update.data.toInt()
                                val newDelay = max(Duration.ZERO, publicationInfo.delay.plus(deltaMinutes.minutes))
                                if (publicationInfo.delay != newDelay) {
                                    publicationInfo.delay = newDelay
                                    edit(
                                        message = finalFormMessage,
                                        text = finalPostFormMessage(publicationInfo),
                                        markup = finalPostFormMarkup(publicationInfo)
                                    )
                                }
                            }

                            // Attached medias clear
                            update.data == clearMediaCallbackData() -> {
                                publicationInfo.mediaList.clear()

                                // Update message
                                edit(
                                    message = finalFormMessage,
                                    text = finalPostFormMessage(publicationInfo),
                                    markup = finalPostFormMarkup(publicationInfo)
                                )
                            }
                        }
                    }

                    is MediaContent -> {
                        // Update attached medias
                        publicationInfo.mediaList.add(
                            ChannelsFacade.Media(
                                update.media.fileId.fileId,
                                when (update) {
                                    is PhotoContent -> ChannelsFacade.Media.Type.PHOTO
                                    is VideoContent -> ChannelsFacade.Media.Type.VIDEO
                                    else -> throw IllegalArgumentException("Поддерживаются только фото/видео типы медиа файлов")
                                }
                            )
                        )

                        // Update message
                        edit(
                            message = finalFormMessage,
                            text = finalPostFormMessage(publicationInfo),
                            markup = finalPostFormMarkup(publicationInfo)
                        )
                    }
                }
            }.catch {
                defaultSafelyWithoutExceptionHandlerWithNull.invoke(it)
            }.first { update ->
                // When finally publishing
                update is MessageDataCallbackQuery && update.data == postCallbackData()
            }.run {
                // Delay itself
                if (publicationInfo.delay != Duration.ZERO) {
                    LOG.debug("Для обработанной статьи ${processedArticle.id}, сообщение ${processedArticleMessage.messageId} запущена задержка: ${publicationInfo.delay}")
                    delay(publicationInfo.delay)
                }

                // Publishing

                LOG.debug("Публикуем обработанную статью ${processedArticle.id}, сообщение ${processedArticleMessage.messageId}, задержка ${publicationInfo.delay}, медиа: ${publicationInfo.mediaList.size}, в канал ${publicationInfo.primaryChannel.name}, затем пересылаем в ${publicationInfo.forwardChannels.map { it.name }}")
                val publishingResult = channelsFacade.postArticle(
                    processedArticle,
                    publicationInfo.primaryChannel,
                    publicationInfo.forwardChannels,
                    publicationInfo.mediaList
                )

                edit(
                    message = finalFormMessage,
                    text = articlePostedMessage(
                        processedArticle,
                        publishingResult
                    ),
                    markup = inlineKeyboard { row {} } // TODO: clear keyboard
                )
            }
        }
    }

    private suspend fun <BS : BehaviourContext> BS.handleProcessedArticleInteraction(
        processedArticleMessage: ContentMessage<TextContent>,
        processedArticle: ProcessedArticle
    ) {
        // Retrieving channels
        var page = 1
        var currentChannelsPage = channelsFacade.getChannels(
            processedArticle.article.source,
            page
        )
        val selected = mutableSetOf<Long>()

        // Edit message to contain processed article
        edit(
            message = processedArticleMessage,
            text = processedArticleMessage(processedArticle),
            markup = processedArticleMarkup(
                currentChannelsPage.content,
                selected,
                page,
                currentChannelsPage.totalPages
            )
        )

        suspend fun editChannelsMarkup() {
            edit(
                message = processedArticleMessage,
                markup = processedArticleMarkup(
                    currentChannelsPage.content,
                    selected,
                    page,
                    currentChannelsPage.totalPages
                )
            )
        }

        // Handle interactions with that processed article
        withNotifyingTimeout(config.articleLifeTime.toKotlinDuration(), processedArticleMessage) {
            val processedArticleHandlerMainJob = waitMessageDataCallbackQuery().filter {
                it.message.sameMessage(processedArticleMessage) && adminCallbackFilter.invoke(it)
            }.subscribeSafelyWithoutExceptions(this) { processedArticleCallback ->
                LOG.debug(
                    "Callback на обработанной статье id=${processedArticle.id}: " +
                            "messageId=${processedArticleCallback.message.messageId}, " +
                            "data=${processedArticleCallback.data}"
                )
                when {
                    // Change page of channels
                    PAGE_REGEX.matches(processedArticleCallback.data) -> {
                        page = processedArticleCallback.data.toInt()
                        currentChannelsPage = channelsFacade.getChannels(processedArticle.article.source, page)
                        editChannelsMarkup()
                    }
                    // Select channel
                    SELECT_REGEX.matches(processedArticleCallback.data) -> {
                        selected.add(getSelectData(processedArticleCallback.data))
                        editChannelsMarkup()
                    }
                    // Deselect channel
                    DESELECT_REGEX.matches(processedArticleCallback.data) -> {
                        selected.remove(getDeselectData(processedArticleCallback.data))
                        editChannelsMarkup()
                    }
                    // Post article
                    processedArticleCallback.data == postCallbackData() -> {
                        with(createSubContext()) {
                            launchSafelyWithoutExceptions {
                                val selectedChannels = processedArticle.article.source.channels.filter {
                                    it.id in selected
                                }
                                this@with.handleProcessedArticlePublishing(
                                    processedArticleMessage,
                                    selectedChannels,
                                    processedArticle
                                )
                            }.also {
                                LOG.debug("Запущен процесс публикации, сообщение ${processedArticleMessage.messageId}, обработанная статья $processedArticle")
                            }.invokeOnCompletion {
                                LOG.debug("Завершён процесс публикации, сообщение ${processedArticleMessage.messageId}, обработанная статья $processedArticle")
                            }
                        }
                    }

                    else -> {
                        throw IllegalArgumentException("Неизвестный callback при подготовке поста к публикации: ${processedArticleCallback.data}")
                    }
                }
            }.also {
                LOG.debug("Запущен процесс обработки callback'ов на обработанной статье $processedArticle, сообщение ${processedArticleMessage.messageId}")
                it.invokeOnCompletion {
                    LOG.debug("Завершён процесс обработки callback'ов на обработанной статье $processedArticle, сообщение ${processedArticleMessage.messageId}")
                }
            }

            // Handle regenerate requests
            val processedArticleRegenerateHandlerJob = waitTextMessage().filter {
                it.replyTo?.sameMessage(processedArticleMessage) ?: false && it.content.text.isNotBlank() && adminMessageFilter.invoke(
                    it
                )
            }.subscribeSafelyWithoutExceptions(this) { textMessage ->
                // Launch parallel processing of regenerated article
                with(createSubContext()) {
                    launchSafelyWithoutExceptions {
                        val reprocessMessage = reply(
                            message = textMessage,
                            text = "Исправляю..."
                        )
                        this@with.handleProcessedArticleInteraction(
                            reprocessMessage,
                            processedArticlesFacade.reprocessArticle(processedArticle.id!!, textMessage.content.text)
                        )
                    }.also {
                        LOG.info("Запущена переобработка обработанной статьи $processedArticle, сообщение ${processedArticleMessage.messageId}")
                    }
                }
            }.also {
                LOG.debug("Запущен процесс ожидания запроса на переобработку для обработанной статьи $processedArticle, сообщение ${processedArticleMessage.messageId}")
                it.invokeOnCompletion {
                    LOG.debug("Завершён процесс ожидания запроса на переобработку для обработанной статьи $processedArticle, сообщение ${processedArticleMessage.messageId}")
                }
            }

            listOf(
                processedArticleHandlerMainJob,
                processedArticleRegenerateHandlerJob
            ).joinAll()
        }
    }

    //
    // COMMANDS
    //

    private suspend fun <BC : BehaviourContext> BC.handleCallbackCommand(
        command: Command,
        args: Array<String>?,
        callbackRegex: Predicate<String>,
        replyMessageInit: (Map<String, String?>) -> MessageWithReplyMarkup,
        userMessage: TextMessage,
        callback: suspend (Map<String, String?>, ContentMessage<TextContent>, String) -> Unit,
        unknownCallback: ((ContentMessage<TextContent>, String) -> Unit)? = null,
    ) {
        val argsAsMap = args?.let {
            try {
                parseArgs(command.args, it)
            } catch (exception: Throwable) {
                throw AssistantActionException(
                    replyTo = userMessage,
                    message = exception.message ?: "Не удалось прочитать аргументы команды",
                    cause = exception
                )
            }
        } ?: emptyMap()

        // Send initial message
        val messageWithReplyMarkup = replyMessageInit.invoke(argsAsMap)
        val initialMessage = reply(
            message = userMessage,
            text = messageWithReplyMarkup.message,
            markup = messageWithReplyMarkup.replyMarkup
        )

        // Wait for callbacks
        withNotifyingTimeout(config.commandLifeTime.toKotlinDuration(), initialMessage) {
            waitMessageDataCallbackQuery().filter {
                it.message.sameMessage(initialMessage)
            }.subscribeSafelyWithoutExceptions(this) { messageCallback ->
                try {
                    if (callbackRegex.test(messageCallback.data)) {
                        callback(argsAsMap, initialMessage, messageCallback.data)
                    } else {
                        unknownCallback?.invoke(initialMessage, messageCallback.data)
                    }
                } catch (throwable: Throwable) {
                    throw AssistantServiceException(
                        assistantMessage = initialMessage,
                        message = "Ошибка обработки callback для команды ${command.name}: ${throwable.message}",
                        cause = throwable
                    )
                }
            }.join()
        }
    }

    private suspend fun <BC : BehaviourContext, T> BC.handleArgumentCommand(
        command: Command,
        userMessage: TextMessage,
        args: Array<String>,
        action: (Map<String, String?>) -> T,
        successMessage: (Map<String, String?>, T) -> String,
        errorMessage: (Map<String, String?>) -> String
    ) {
        val argsAsMap = try {
            parseArgs(command.args, args)
        } catch (exception: Throwable) {
            throw AssistantActionException(
                replyTo = userMessage,
                message = exception.message ?: "Не удалось прочитать аргументы команды",
                cause = exception
            )
        }

        handleCommand(
            command,
            userMessage,
            { action(argsAsMap) },
            { result -> successMessage(argsAsMap, result) },
            { errorMessage(argsAsMap) }
        )
    }

    private suspend fun <BC : BehaviourContext, T> BC.handleFormCommand(
        command: Command,
        userMessage: TextMessage,
        schemaMessage: String,
        action: (Map<String, String>) -> T,
        successMessage: (Map<String, String>, T) -> String,
        errorMessage: (Map<String, String>) -> String
    ) {
        // Send form message
        val formMessage = reply(
            message = userMessage,
            text = schemaMessage
        )

        withNotifyingTimeout(config.commandLifeTime.toKotlinDuration(), formMessage) {
            // Wait for reply with input
            val inputMessage = waitTextMessage().first {
                it.replyTo?.sameMessage(formMessage) ?: false
            }
            try {
                val input = parseStringToMap(inputMessage.content.text)

                handleCommand(
                    command,
                    inputMessage,
                    { action(input) },
                    { result -> successMessage(input, result) },
                    { errorMessage(input) }
                )
            } catch (throwable: Throwable) {
                throw AssistantServiceException(
                    assistantMessage = formMessage,
                    message = "Ошибка обработки ввода для команды ${command.name}: ${throwable.message}",
                    cause = throwable
                )
            }
        }
    }

    private suspend fun <BC : BehaviourContext, T> BC.handleCommand(
        command: Command,
        userMessage: TextMessage,
        action: () -> T,
        successMessage: (T) -> String,
        errorMessage: () -> String
    ) {
        try {
            action().also {
                try {
                    reply(
                        message = userMessage,
                        text = successMessage(it)
                    )
                } catch (e: Throwable) {
                    LOG.error("Не удалось оповестить об успешно выполненном действии ${command.commandName}")
                    throw e
                }
            }
        } catch (actionException: Throwable) {
            throw AssistantActionException(
                replyTo = userMessage,
                message = "Ошибка команды ${command.commandName}. ${errorMessage()}: ${actionException.message}",
                cause = actionException
            )
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(TelegramAssistantFacade::class.java)

        private const val SPLIT_INPUT = "="
        private val SPLIT = "===============================================".spoilerHTML() + "\n"
        private val DEFAULT_PARSE_MODE = HTML

        //
        // PARSING/FORMATTING
        //

        private fun String.spoilerHTML(): String {
            return "<$htmlSpoilerControl>${toHtml()}</$htmlSpoilerClosingControl>"
        }

        private val INPUT_REGEX =
            Regex("(^[a-zA-Z]+?)\\s*$SPLIT_INPUT\\s*([\\s\\S]*?)(?=\\n^\\S+\\s*$SPLIT_INPUT\\s*|\\Z)")

        private fun parseArgs(commandArgs: List<Command.Arg>, args: Array<String>): Map<String, String?> {
            if (commandArgs.size != args.size) {
                throw IllegalArgumentException("Ожидались следующие аргументы: ${commandArgs.joinToString()}")
            }
            return args.withIndex().associateBy(
                { arg -> commandArgs[arg.index].name },
                { arg ->
                    if (commandArgs[arg.index].optional) {
                        arg.value.let { value ->
                            if (value == "null") {
                                null
                            } else {
                                value
                            }
                        }
                    } else {
                        arg.value
                    }
                }
            )
        }

        private fun parseStringToMap(input: String): Map<String, String> {

            val map = mutableMapOf<String, String>()

            input.lines().forEach { line ->
                val matchResult = INPUT_REGEX.find(line)
                if (matchResult != null) {
                    val (key, value) = matchResult.destructured
                    map[key] = value
                }
            }

            return map
        }

        private fun writeForMessage(pair: Pair<String, Any?>): String {
            return StringBuilder()
                .append(pair.first.boldHTML())
                .append(" ")
                .append(SPLIT_INPUT)
                .append(" ")
                .append(pair.second.toString())
                .toString()
        }

        private fun writeForMessage(pairs: Map<String, Any?>): String {
            return pairs.entries.joinToString("\n") { writeForMessage(it.toPair()) }
        }

        private fun writeForMessage(manyPairs: List<Map<String, Any?>>): String {
            return manyPairs.joinToString("\n" + SPLIT) { writeForMessage(it) }
        }

        //
        // MESSAGES
        //

        private fun newArticleMessage(article: Article): String {
            return "Получена новая статья:" +
                    "\n" +
                    writeForMessage(
                        mapOf(
                            "Id статьи" to article.id,
                            "Источник" to article.source.name,
                            "Заголовок" to article.title,
                            "Описание" to article.description,
                            "URL" to article.url
                        )
                    ) +
                    "\n" +
                    "Для обработки выберите режим:"
        }

        private fun selectArticleModeMessage(article: Article): String {
            return "Выберите режим обработки статьи статьи id=${article.id}" +
                    "\n" +
                    article.title.boldHTML()
        }

        private fun processedArticleMessage(processArticle: ProcessedArticle): String {
            return "Результат обработки ${processArticle.id}, режим ${processArticle.mode.boldHTML()} (символов: ${processArticle.content.length})." +
                    "\n" +
                    "\n" +
                    processArticle.content +
                    "\n" +
                    "\n" +
                    "Для корректировки введите замечания." +
                    "\n" +
                    "Для публикации выберите каналы:"
        }

        private fun articlePostedMessage(
            processedArticle: ProcessedArticle,
            publishingResult: ChannelsFacade.PublishingResult
        ): String {
            return "Статья ${processedArticle.id} опубликована:" +
                    "\n" +
                    writeForMessage(
                        mutableMapOf(
                            "Первичный канал" to publishingResult.primaryPost.messageLink(),
                        ).apply {
                            if (publishingResult.forwardedPosts.isNotEmpty()) {
                                put("Forward каналы", publishingResult.forwardedPosts.joinToString {
                                    it.messageLink()
                                })
                            }
                        }
                    )

        }

        private fun primaryChannelSelectionMessage(): String {
            return "Выберите первичный канал"
        }

        private fun finalPostFormMessage(
            publicationInfo: PublicationInfo
        ): String {
            return "Подготовка к посту:" +
                    "\n" +
                    writeForMessage(
                        mutableMapOf(
                            "Первичный канал" to publicationInfo.primaryChannel.chatLink(),
                            "Медиа файлы" to publicationInfo.mediaList.size
                        ).apply {
                            if (publicationInfo.forwardChannels.isNotEmpty()) {
                                put("Forward каналы", publicationInfo.forwardChannels.joinToString { it.chatLink() })
                            }
                        }
                    ) +
                    "\n" +
                    "Выберите когда опубликовать новость, при необходимости прикрепите медиа файлы"
        }

        //
        // MARKUPS
        //

        private fun selectArticleModeMarkup(
            articleId: Long,
            modes: Collection<String>,
            page: Int,
            totalPages: Int
        ): InlineKeyboardMarkup {
            return inlineKeyboard {
                modes.forEach { mode ->
                    row {
                        dataButton("Режим $mode", processArticleCallbackData(ProcessArticleData(articleId, mode)))
                    }
                }
            }.let { mainMarkup ->
                paginationMarkup(
                    page,
                    totalPages,
                    "AM_${articleId}_"
                )?.let {
                    mainMarkup + it
                } ?: mainMarkup
            }
        }

        private fun primaryChannelSelectionMarkup(channels: Collection<Channel>): InlineKeyboardMarkup {
            return inlineKeyboard {
                channels.chunked(4).forEach { chunk ->
                    row {
                        chunk.forEach { channel ->
                            dataButton(
                                "${channel.name} | ${channel.platform.name}",
                                selectCallbackData(channel.id)
                            )
                        }
                    }
                }
            }
        }

        private fun finalPostFormMarkup(publicationInfo: PublicationInfo): InlineKeyboardMarkup {
            return inlineKeyboard {
                row {
                    dataButton("-10м", delayDeltaCallbackData(-10))
                    dataButton("-1м", delayDeltaCallbackData(-1))
                    dataButton("+1м", delayDeltaCallbackData(1))
                    dataButton("+10м", delayDeltaCallbackData(10))
                }
                row {
                    val message = when (publicationInfo.delay) {
                        Duration.ZERO -> "Опубликовать сейчас"
                        else -> "Опубликовать через ${publicationInfo.delay.minutes}м"
                    }
                    dataButton(message, postCallbackData())
                    if (publicationInfo.mediaList.isNotEmpty()) {
                        dataButton("Убрать медиа", clearMediaCallbackData())
                    }
                }
            }
        }

        private fun processedArticleMarkup(
            channels: Collection<Channel>,
            selected: Set<Long>,
            page: Int,
            totalPages: Int
        ): InlineKeyboardMarkup {
            return inlineKeyboard {
                row {
                    dataButton("Опубликовать", postCallbackData())
                }

                channels.forEach { channel ->
                    row {
                        val isSelected: Boolean = selected.contains(channel.id)
                        val callbackData = when (isSelected) {
                            true -> deselectCallbackData(channel.id)
                            false -> selectCallbackData(channel.id)
                        }
                        dataButton(("[+] ".takeIf { isSelected } ?: "") +
                                channel.name +
                                " | " +
                                channel.platform.name,
                            callbackData)
                    }
                }
            }.let { mainMarkup ->
                simplePaginationMarkup(page, totalPages)?.let {
                    mainMarkup + it
                } ?: mainMarkup
            }
        }

        private val PAGE_REGEX = Regex("^[1-9]\\d*\$")
        private fun simplePaginationMarkup(
            currentPage: Int,
            totalPages: Int,
        ): InlineKeyboardMarkup? {
            return paginationMarkup(currentPage, totalPages)
        }

        //
        // CALLBACKS
        //

        private data class ArticleModesData(
            val articleId: Long,
            val page: Int
        )
        private val ARTICLE_MODES_PAGE_REGEX = Regex("AM_\\d*_\\d*")
        private fun getArticleModesData(data: String): ArticleModesData {
            require(data.startsWith("AM_")) { "В запросе на выбор страницы режимов статьи встречен некорректный callback" }
            return data.substringAfter("AM_").split("_").let {
                require(it.size == 2) { "В запросе на выбор страницы режимов статьи встречен некорректный callback" }
                ArticleModesData(
                    it[0].toLong(),
                    it[1].toInt()
                )
            }
        }

        private data class ProcessArticleData(
            val articleId: Long,
            val mode: String
        )
        private val PROCESS_ARTICLE_REGEX = Regex("PA_\\d*_.*")
        private fun processArticleCallbackData(processArticleData: ProcessArticleData): String {
            return "PA_${processArticleData.articleId}_${processArticleData.mode}"
        }
        private fun getProcessArticleData(data: String): ProcessArticleData {
            require(data.startsWith("PA_")) { "В запросе на обработку статьи встречен некорректный callback" }
            return data.substringAfter("PA_").split("_").let {
                require(it.size == 2) { "В запросе на обработку статьи встречен некорректный callback" }
                ProcessArticleData(
                    it[0].toLong(),
                    it[1]
                )
            }
        }

        private val SELECT_REGEX = Regex("SE_.*")
        private fun selectCallbackData(id: Long): String {
            return "SE_${id}"
        }
        private fun getSelectData(data: String): Long {
            return data.substringAfter("_").toLong()
        }

        private val DESELECT_REGEX = Regex("DESE_.*")
        private fun deselectCallbackData(id: Long): String {
            return "DESE_${id}"
        }
        private fun getDeselectData(data: String): Long {
            return data.substringAfter("_").toLong()
        }

        private fun postCallbackData(): String {
            return "POST"
        }

        private fun clearMediaCallbackData(): String {
            return "CLEAR"
        }

        private val DELAY_DELTA_REGEX = Regex("^-?\\d+")
        private fun delayDeltaCallbackData(minutes: Int): String {
            return minutes.toString()
        }

        //
        // UTILS
        //

        private fun paginationMarkup(
            currentPage: Int,
            totalPages: Int,
            prefix: String = "",
        ): InlineKeyboardMarkup? {
            if (totalPages == 0) {
                return null
            }

            if (currentPage < 1) {
                throw IllegalArgumentException("Невалидное значение страницы: $currentPage/$totalPages")
            }

            return inlineKeyboard {
                row {
                    if (currentPage > 2) {
                        dataButton("⏪ 1", "$prefix${1}")
                    }

                    // Previous page button (if applicable)
                    if (currentPage > 1) {
                        val prev = currentPage - 1
                        dataButton(prev.toString(), "${prefix}$prev")
                    }

                    // Current page (highlighted)
                    dataButton("\uD83D\uDD39 $currentPage \uD83D\uDD39", "${prefix}$currentPage")

                    // Next page button (if applicable)
                    if (currentPage < totalPages) {
                        val next = currentPage + 1
                        dataButton(next.toString(), "${prefix}$next")
                    }

                    // Last page button (only if currentPage is far from the last page)
                    if (currentPage < totalPages - 1) {
                        dataButton("⏩ $totalPages", "${prefix}$totalPages")
                    }
                }
            }
        }

        private fun Channel.chatLink(): String {
            return name.linkHTML(
                makeChatLink(
                    RawChatId(id),
                )
            )
        }

        private fun Post.messageLink(): String {
            return channel.name.linkHTML(
                makeLinkToMessage(
                    RawChatId(channel.id),
                    MessageId(channelPostId)
                )
            )
        }

        private suspend fun TelegramBot.send(
            chatId: ChatId,
            text: String,
            markup: InlineKeyboardMarkup? = null
        ): ContentMessage<TextContent> {
            return send(
                chatId = chatId,
                text = text,
                replyMarkup = markup,
                parseMode = DEFAULT_PARSE_MODE
            )
        }

        private suspend fun TelegramBot.reply(
            message: AccessibleMessage,
            text: String,
            markup: InlineKeyboardMarkup? = null
        ): ContentMessage<TextContent> {
            return reply(
                to = message,
                text = text,
                replyMarkup = markup,
                parseMode = DEFAULT_PARSE_MODE
            )
        }

        private suspend fun TelegramBot.edit(
            message: ContentMessage<TextContent>,
            text: String? = null,
            markup: InlineKeyboardMarkup? = null
        ): ContentMessage<MessageContent> {
            return if (text != null) {
                editMessageText(
                    message = message,
                    text = text,
                    replyMarkup = markup,
                    parseMode = DEFAULT_PARSE_MODE
                )
            } else {
                editMessageReplyMarkup(
                    message = message,
                    replyMarkup = markup
                )
            }
        }

        private suspend fun <BC : BehaviourContext, T> BC.withNotifyingTimeout(
            timeout: Duration,
            message: ContentMessage<TextContent>,
            block: suspend CoroutineScope.() -> T
        ): T? {
            try {
                return withTimeout(timeout) {
                    LOG.debug("Запуск процесса для сообщения ${message.messageId}")
                    block.invoke(this)
                }
            } catch (exception: Throwable) {
                if (exception is TimeoutCancellationException || exception is CancellationException) {
                    LOG.debug(
                        "Процесс для сообщения ${message.messageId} завершён" + if (exception is TimeoutCancellationException) {
                            " по timeout'у"
                        } else {
                            ""
                        }
                    )
                    delete(message)
                } else {
                    throw exception
                }
            }

            return null
        }
    }
}