package net.kravuar.vestnik.assistant

import dev.inmo.micro_utils.common.alsoIfFalse
import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.bot.exceptions.MessageIsNotModifiedException
import dev.inmo.tgbotapi.extensions.api.bot.setMyCommands
import dev.inmo.tgbotapi.extensions.api.edit.reply_markup.editMessageReplyMarkup
import dev.inmo.tgbotapi.extensions.api.edit.text.editMessageText
import dev.inmo.tgbotapi.extensions.api.files.downloadFileStream
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.behaviour_builder.BehaviourContext
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviour
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitAnyContentMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitTextMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommand
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onCommandWithArgs
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.SimpleFilter
import dev.inmo.tgbotapi.extensions.utils.extensions.sameMessage
import dev.inmo.tgbotapi.extensions.utils.formatting.boldHTML
import dev.inmo.tgbotapi.extensions.utils.formatting.hashTagHTML
import dev.inmo.tgbotapi.extensions.utils.formatting.italicHTML
import dev.inmo.tgbotapi.extensions.utils.formatting.strikethroughHTML
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.updates.retrieving.longPolling
import dev.inmo.tgbotapi.extensions.utils.withUserOrThrow
import dev.inmo.tgbotapi.types.BotCommand
import dev.inmo.tgbotapi.types.ChatId
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
import dev.inmo.tgbotapi.utils.PreviewFeature
import dev.inmo.tgbotapi.utils.row
import io.ktor.utils.io.jvm.javaio.toInputStream
import korlibs.time.minutes
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.channels.ChannelPlatform
import net.kravuar.vestnik.channels.ChannelsFacade
import net.kravuar.vestnik.commons.Constants
import net.kravuar.vestnik.processor.ProcessedArticle
import net.kravuar.vestnik.processor.ProcessedArticlesFacade
import net.kravuar.vestnik.processor.nodes.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import java.io.InputStream
import java.util.Optional
import java.util.function.Predicate
import kotlin.time.Duration
import kotlin.time.toKotlinDuration

private class ReplyableException(
    val replyTo: AccessibleMessage,
    override val message: String,
    override val cause: Throwable? = null,
) : RuntimeException()

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
    private val adminChannel: ChatId = ChatId(RawChatId(adminChannelId))

    private val admins: Set<UserId> = adminsIds.map { UserId(RawChatId(it)) }.toSet()
    private val owner: UserId = UserId(RawChatId(ownerId))
    private val adminMessageFilter = SimpleFilter<CommonMessage<*>> {
        (it.chat.id == adminChannel && it.withUserOrThrow().user.id in admins).alsoIfFalse {
            LOG.info("Сообщение не прошло проверку безопасности: $it")
        }
    }
    private val adminCallbackFilter = SimpleFilter<MessageCallbackQuery> {
        (it.message.chat.id == adminChannel && it.withUserOrThrow().user.id in admins).alsoIfFalse {
            LOG.info("Callback не прошёл проверку безопасности: $it")
        }
    }

    private data class MessageWithReplyMarkup(
        val message: String,
        val replyMarkup: InlineKeyboardMarkup?
    )

    private enum class Command(
        val commandName: String,
        val description: String,
        val args: List<Arg>,
    ) {
        SHOW_SOURCES("show_sources", "Показать список источников", emptyList()),
        SHOW_SOURCE("show_source", "Показать источник", listOf()),
        ADD_SOURCE("add_source", "Добавить источник", emptyList()),
        DELETE_SOURCE("delete_source", "Удалить источник по имени", listOf(Arg("name", "Имя источника"))),
        UPDATE_SOURCE("update_source", "Обновить источник", emptyList()),
        SHOW_CHANNELS("show_channels", "Показать список каналов", emptyList()),
        ADD_CHANNEL("add_channel", "Добавить канал", emptyList()),
        DELETE_CHANNEL("delete_channel", "Удалить канал по имени", listOf(Arg("name", "Имя канала"))),
        SHOW_CHAINS("show_chains", "Показать цепочки обработки статей", emptyList()),
        SHOW_MODES(
            "show_modes",
            "Показать режимы обработки статей для источника",
            listOf(Arg("name", "Имя источника"))
        ),
        SHOW_CHAIN(
            "show_chain",
            "Показать конкретную цепочку",
            listOf(Arg("sourceName", "Имя источника"), Arg("mode", "Имя режима"))
        ),
        ADD_CHAIN(
            "add_chain",
            "Добавить цепочку обработки статьи",
            listOf(Arg("sourceName", "Имя источника"), Arg("mode", "Имя режима"))
        ),
        DELETE_CHAIN(
            "delete_chain",
            "Удалить цепочку обработки статьи",
            listOf(Arg("sourceName", "Имя источника"), Arg("mode", "Имя режима"))
        ),
        ADD_NODE("add_node", "Добавить узел после указанного узла", emptyList()),
        DELETE_NODE("delete_node", "Удалить узел обработки статьи", listOf(Arg("id", "ID узла"))),
        UPDATE_NODE("update_node", "Обновить узел обработки статьи", emptyList()),
        SHOW_COMMANDS("show_commands", "Показать список команд", emptyList());

        data class Arg(
            val name: String,
            val description: String
        )
    }

    internal suspend fun start(): Job {
        val behaviour = bot.buildBehaviour(defaultExceptionsHandler = {
            LOG.error("Ошибка во время работы ассистента", it)
            when (it) {
                is MessageIsNotModifiedException -> {
                    LOG.warn("Ошибка модификации сообщения", it)
                }

                is ReplyableException -> {
                    try {
                        bot.reply(
                            message = it.replyTo,
                            text = it.message
                        )
                    } catch (exception: Exception) {
                        LOG.error("Не удалось оповестить об ошибке $it")
                    }
                }

                else -> {
                    bot.send(
                        chatId = adminChannel,
                        text = "Произошла непредвиденная ошибка: ${it.message ?: it}"
                    )
                }
            }
        }) {

            // MAIN PROCESS ARTICLE HANDLER
            onMessageDataCallbackQuery(
                dataRegex = PROCESS_ARTICLE_REGEX,
                initialFilter = adminCallbackFilter,
                markerFactory = null,
            ) { processCallback ->
                val data = processCallback.data.substringAfter("_")
                val (mode, id) = getProcessArticleData(data)
                LOG.info("Запущена обработка статьи id=$id, mode=$mode, messageId=${processCallback.message.messageId}")

                // Process
                val article = articlesFacade.getArticle(id)
                val processedArticle = processedArticlesFacade.processArticle(article, mode)

                processedArticleHandling(processedArticle)
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
                            "Приостановлен" to it.suspended,
                            "Удалён" to it.deleted
                        )
                    })

                    MessageWithReplyMarkup(
                        "Список источников" + if (sources.content.isNotEmpty()) {
                            "\n" + sourcesAsString
                        } else {
                            " пуст"
                        },
                        paginationMarkup(page, sources.totalPages)
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
                                "Целевые Каналы" to source.channels.joinToString { channel -> channel.name },
                                "Приостановлен" to source.suspended,
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
                                    "currentName" to "Имя обновляемого источника (обязательно)",
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
                            requireNotNull(input["currentName"]) { "Не указано имя обновляемого источника" },
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

            onCommand(
                command = Command.SHOW_CHANNELS.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                val pageSupplier = { page: Int ->
                    val channels = channelsFacade.getChannels(page)
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
                        paginationMarkup(page, channels.totalPages)
                    )
                }
                handleCallbackCommand(
                    Command.SHOW_CHANNELS,
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
                    { input ->
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
                    { _, channel -> "Канал ${channel.name} добавлен" },
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

            onCommand(
                command = Command.SHOW_CHAINS.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage ->
                val pageSupplier = { page: Int ->
                    val chains = aiArticleProcessingNodesFacade.getChains(page)
                    val chainsAsString = writeForMessage(chains.content.map {
                        mapOf(
                            "Id Корня" to it.id,
                            "Источник" to it.source.name,
                            "Режим" to it.mode,
                        )
                    })

                    MessageWithReplyMarkup(
                        "Список цепочек обработки новости" + if (chains.content.isNotEmpty()) {
                            "\n" + chainsAsString
                        } else {
                            " пуст"
                        },
                        paginationMarkup(page, chains.totalPages)
                    )
                }
                handleCallbackCommand(
                    Command.SHOW_CHAINS,
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
                command = Command.SHOW_MODES.commandName,
                initialFilter = adminMessageFilter,
                markerFactory = null,
            ) { userMessage, args ->
                val pageSupplier = { sourceName: String, page: Int ->
                    val modes = aiArticleProcessingNodesFacade.getModes(
                        sourcesFacade.getSourceByName(sourceName),
                        page
                    )
                    MessageWithReplyMarkup(
                        "Список режимов для источника $sourceName" + if (modes.content.isNotEmpty()) {
                            ": " + modes.content.joinToString()
                        } else {
                            " пуст"
                        },
                        paginationMarkup(page, modes.totalPages)
                    )
                }
                handleCallbackCommand(
                    Command.SHOW_MODES,
                    args,
                    { PAGE_REGEX.matches(it) },
                    { argsByName ->
                        pageSupplier(
                            requireNotNull(argsByName["name"]) {
                                "Имя источника не может отсутствовать"
                            },
                            1
                        )
                    },
                    userMessage,
                    { argsByName, message, callback ->
                        val page = pageSupplier(argsByName["name"]!!, callback.toInt())
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
                        aiArticleProcessingNodesFacade.getChain(
                            sourcesFacade.getSourceByName(requireNotNull(argsByName["sourceName"]) {
                                "Имя источника является обязательным"
                            }),
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
                        "Цепочка для источника ${argsByName["sourceName"]!!}, режима ${argsByName["mode"]!!} (${chain.size} узлов):" +
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
                            sourcesFacade.getSourceByName(requireNotNull(argsByName["sourceName"]) {
                                "Имя источника является обязательным"
                            }),
                            requireNotNull(argsByName["mode"]) { "Режим обработки является обязательным" },
                        )
                    },
                    { argsByName, _ -> "Цепочка обработки источника ${argsByName["sourceName"]!!} с именем режима ${argsByName["mode"]!!} создана" },
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
                            sourcesFacade.getSourceByName(requireNotNull(argsByName["sourceName"]) {
                                "Имя источника является обязательным"
                            }),
                            requireNotNull(argsByName["mode"]) { "Режим обработки является обязательным" },
                        )
                    },
                    { argsByName, _ -> "Цепочка обработки источника ${argsByName["sourceName"]!!} с именем режима ${argsByName["mode"]!!} удалена" },
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
                    { _, node -> "Узел ${node.id} добавлен в цепочку источника ${node.source.name}, режима ${node.mode}" },
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
                    { _, node -> "Узел ${node.id} обновлён в цепочке источника ${node.source.name}, режима ${node.mode}" },
                    { "Не удалось обновить узел" },
                )
            }

            setMyCommands(
                Command.entries.map { command ->
                    BotCommand(
                        command.commandName,
                        command.description + if (command.args.isNotEmpty()) {
                            " аргументы: ${command.args.joinToString(", ") { it.description }}"
                        } else {
                            ""
                        }
                    )
                },
                scope = BotCommandScope.Chat(adminChannel)
            )

            allUpdatesFlow.subscribeSafelyWithoutExceptions(this) {
                LOG.debug(it)
            }
        }
        return bot.longPolling(behaviour).also {
            LOG.info("Работа ассистента запущена, конфигурация: $config")
            it.invokeOnCompletion {
                LOG.info("Работа ассистента закончена")
            }
        }
    }

    override fun notifyNewArticle(article: Article) {
        LOG.info("Оповещение о новой статье $article")
        runBlocking {
            val id = requireNotNull(article.id) { "ID обрабатываемой статьи не может отсутствовать." }
            bot.send(
                chatId = adminChannel,
                text = newArticleMessage(article),
                markup = articleReplyMarkup(id, processedArticlesFacade.getModes(article)),
            )
        }
    }

    private suspend fun <BS : BehaviourContext> BS.processedArticleHandling(processedArticle: ProcessedArticle) {
        // Retrieving channels
        val channels = processedArticle.article.source.channels.associateBy { it.id }
        val selected = mutableSetOf<Long>()

        // Creating processed article message
        val processedArticleMessage = send(
            chatId = adminChannel,
            text = processedArticleMessage(processedArticle),
            markup = postReplyMarkup(channels.values, selected)
        )

        // Handle interactions with that processed article
        val processedArticleHandlerMainJob = waitMessageDataCallbackQuery().filter {
            it.message.sameMessage(processedArticleMessage) && adminCallbackFilter.invoke(it)
        }.subscribeSafelyWithoutExceptions(this) { processedArticleCallback ->
            LOG.info(
                "Callback на обработанной статье id=${processedArticle.id}: " +
                        "messageId=${processedArticleCallback.message.messageId}, " +
                        "data=${processedArticleCallback.data}"
            )
            when {
                // Select channel
                SELECT_CHANNEL_REGEX.matches(processedArticleCallback.data) -> {
                    selected.add(getSelectChannelId(processedArticleCallback.data))
                    edit(
                        message = processedArticleMessage,
                        markup = postReplyMarkup(channels.values, selected)
                    )
                }
                // Deselect channel
                DESELECT_CHANNEL_REGEX.matches(processedArticleCallback.data) -> {
                    selected.remove(getDeselectChannelId(processedArticleCallback.data))
                    edit(
                        message = processedArticleMessage,
                        markup = postReplyMarkup(channels.values, selected)
                    )
                }
                // Post article
                processedArticleCallback.data == postCallbackData() -> {
                    val selectedChannels = selected.map {
                        requireNotNull(channels[it])
                    }

                    // Select primary channel message
                    val primaryChannelSelectionMessage = send(
                        chatId = adminChannel,
                        text = primaryChannelSelectionMessage(),
                        markup = primaryChannelSelectionReplyMarkup(selectedChannels)
                    )

                    coroutineScope {
                        // Primary channel selection handling
                        val primaryChannel = requireNotNull(
                            channels[
                                getSelectChannelId(
                                    waitMessageDataCallbackQuery()
                                        .filter {
                                            it.message.sameMessage(primaryChannelSelectionMessage) && adminCallbackFilter.invoke(
                                                it
                                            )
                                        }.first().data
                                )
                            ]
                        ) { "Выбранный первичный канал не может отсутствовать" }

                        // Select delay message
                        var postDelay = Duration.ZERO
                        val mediaList = mutableListOf<InputStream>()
                        val finalPostMessage = send(
                            chatId = adminChannel,
                            text = finalPostActionMessage(0),
                            markup = finalPostActionReplyMarkup(postDelay)
                        )

                        // Handle media attachment
                        waitAnyContentMessage().filter {
                            it.replyTo?.sameMessage(finalPostMessage) ?: false && adminMessageFilter.invoke(it)
                        }.subscribeSafelyWithoutExceptions(this) { messageContent ->
                            val contentToProcess: List<MediaContent> = with(messageContent.content) {
                                when (this) {
                                    is PhotoContent -> listOf(this)
                                    is VideoContent -> listOf(this)
                                    is MediaGroupContent<*> -> {
                                        this.group.map { part ->
                                            part.content
                                        }.also { content ->
                                            require(content.all { part ->
                                                part is PhotoContent || part is VideoContent
                                            }) { "Поддерживаются только фото/видео медиа файлы." }
                                        }
                                    }

                                    else -> {
                                        throw IllegalArgumentException("Поддерживаются только фото/видео медиа файлы.")
                                    }
                                }
                            }
                            // Update attached medias
                            mediaList.clear()
                            mediaList.addAll(contentToProcess.map { media ->
                                downloadFileStream(media).toInputStream()
                            })

                            // Update message
                            edit(
                                message = finalPostMessage,
                                text = finalPostActionMessage(mediaList.size)
                            )
                        }

                        // Handle delay adjustment/final post action
                        waitMessageDataCallbackQuery().filter {
                            it.message.sameMessage(finalPostMessage) && adminCallbackFilter.invoke(it)
                        }.onEach { delaySelectionCallback ->
                            if (DELAY_DELTA_REGEX.matches(delaySelectionCallback.data)) {
                                val deltaMinutes = delaySelectionCallback.data.toInt()
                                postDelay = postDelay.plus(deltaMinutes.minutes)
                                edit(
                                    message = finalPostMessage,
                                    text = finalPostActionMessage(mediaList.size),
                                    markup = finalPostActionReplyMarkup(postDelay)
                                )
                            }
                        }.filter { delaySelectionCallback ->
                            delaySelectionCallback.data == postCallbackData()
                        }.first()

                        // Delay itself
                        if (postDelay != Duration.ZERO) {
                            delay(postDelay)
                        }

                        // Publishing
                        val forwardChannels = selectedChannels
                            .filter { it != primaryChannel }
                        channelsFacade.postArticle(processedArticle, primaryChannel, forwardChannels)

                        reply(
                            message = primaryChannelSelectionMessage,
                            text = articlePostedMessage(processedArticle, primaryChannel, forwardChannels)
                        )
                    }
                }

                else -> {
                    throw IllegalArgumentException("Неизвестный callback при подготовке поста к публикации: ${processedArticleCallback.data}")
                }
            }
        }

        // Handle regenerate requests
        val processedArticleRegenerateHandlerJob = waitTextMessage().filter {
            it.replyTo?.sameMessage(processedArticleMessage) ?: false && it.content.text.isNotBlank() && adminMessageFilter.invoke(
                it
            )
        }.subscribeSafelyWithoutExceptions(this) { textMessage ->
            launch {
                // Launch parallel processing of regenerated article
                processedArticleMessage(
                    processedArticlesFacade.reprocessArticle(processedArticle.id!!, textMessage.content.text)
                )
            }
        }

        // Stop article processing after timeout
        delay(config.articleLifeTime.toKotlinDuration())
        processedArticleHandlerMainJob.cancel("Новость устарела")
        processedArticleRegenerateHandlerJob.cancel("Новость устарела")
        LOG.info(
            "Процесс для обработанной новости " +
                    "id=${processedArticle.id}, " +
                    "messageId=${processedArticleMessage.messageId} завершён"
        )
        edit(
            message = processedArticleMessage,
            text = "[УСТАРЕЛО]".boldHTML() +
                    "\n" +
                    processedArticleMessage(processedArticle).strikethroughHTML()
        )
    }

    //
    // COMMANDS
    //

    private suspend fun <BC : BehaviourContext> BC.handleCallbackCommand(
        command: Command,
        args: Array<String>?,
        callbackRegex: Predicate<String>,
        replyMessageInit: (Map<String, String>) -> MessageWithReplyMarkup,
        userMessage: TextMessage,
        callback: suspend (Map<String, String>, ContentMessage<TextContent>, String) -> Unit,
        unknownCallback: ((ContentMessage<TextContent>, String) -> Unit)? = null,
    ) {
        val argsAsMap = args?.let {
            if (command.args.size != it.size) {
                reply(
                    message = userMessage,
                    text = "Ожидались следующие аргументы: ${command.args.joinToString()}"
                )
            }
            it.withIndex().associateBy(
                { arg -> command.args[arg.index].name },
                { arg -> arg.value }
            )
        } ?: emptyMap()

        // Send initial message
        val messageWithReplyMarkup = replyMessageInit.invoke(argsAsMap)
        val initialMessage = reply(
            message = userMessage,
            text = messageWithReplyMarkup.message,
            markup = messageWithReplyMarkup.replyMarkup
        )

        // Wait for callbacks
        val handleInputJob = launch {
            waitMessageDataCallbackQuery().filter {
                it.message.sameMessage(initialMessage)
            }.subscribeSafelyWithoutExceptions(this) { messageCallback ->
                if (callbackRegex.test(messageCallback.data)) {
                    callback(argsAsMap, initialMessage, messageCallback.data)
                } else {
                    unknownCallback?.invoke(initialMessage, messageCallback.data)
                }
            }
        }

        // Stop command processing after timeout
        delay(config.commandLifeTime.toKotlinDuration())
        handleInputJob.cancel("Обработчик команды устарел")
        LOG.info(
            "Процесс для обработки поисковой команды " +
                    "command=${command.commandName}, " +
                    "messageId=${initialMessage.messageId} завершён"
        )
        edit(
            message = initialMessage,
            text = "[УСТАРЕЛО]".boldHTML() +
                    "\n" +
                    initialMessage.content.text.strikethroughHTML()
        )
    }

    private suspend fun <BC : BehaviourContext, T> BC.handleArgumentCommand(
        command: Command,
        userMessage: TextMessage,
        args: Array<String>,
        action: (Map<String, String>) -> T,
        successMessage: (Map<String, String>, T) -> String,
        errorMessage: (Map<String, String>) -> String
    ) {
        if (command.args.size != args.size) {
            reply(
                message = userMessage,
                text = "Ожидались следующие аргументы: ${command.args.joinToString()}"
            )
        }
        val argsAsMap = args.withIndex().associateBy(
            { command.args[it.index].name },
            { it.value }
        )

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

        val handleInputJob = launch {
            // Wait for reply with input
            val inputMessage = waitTextMessage().filter {
                it.replyTo?.sameMessage(formMessage) ?: false
            }.first()
            val input = parseStringToMap(inputMessage.content.text)

            handleCommand(
                command,
                inputMessage,
                { action(input) },
                { result -> successMessage(input, result) },
                { errorMessage(input) }
            )
        }

        // Stop command processing after timeout
        delay(config.commandLifeTime.toKotlinDuration())
        handleInputJob.cancel("Обработчик команды устарел")
        LOG.info(
            "Процесс для обработки команды " +
                    "command=${command.commandName}, " +
                    "messageId=${formMessage.messageId} завершён"
        )
        edit(
            message = formMessage,
            text = "[УСТАРЕЛО]".boldHTML() +
                    "\n" +
                    formMessage.content.text.strikethroughHTML()
        )
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
                } catch (e: Exception) {
                    LOG.error("Не удалось оповестить об успешно выполненном действии ${command.commandName}")
                    throw e
                }
            }
        } catch (actionException: Exception) {
            throw ReplyableException(
                replyTo = userMessage,
                message = "Ошибка команды ${command.commandName}. ${errorMessage()}: ${actionException.message}",
                cause = actionException
            )
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(TelegramAssistantFacade::class.java)

        private const val SPLIT_INPUT = "="
        private val SPLIT = "===============================================".strikethroughHTML() + "\n"
        private val DEFAULT_PARSE_MODE = HTML

        //
        // PARSING/FORMATTING
        //

        private val INPUT_REGEX =
            Regex("(^[a-zA-Z]+?)\\s*$SPLIT_INPUT\\s*([\\s\\S]*?)(?=\\n^\\S+\\s*$SPLIT_INPUT\\s*|\\Z)")

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
                .append(pair.second.toString().italicHTML())
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
                    )
        }

        private fun processedArticleMessage(processArticle: ProcessedArticle): String {
            return "Результат обработки режимом ${processArticle.mode.boldHTML()}:" +
                    "\n" +
                    writeForMessage(
                        mapOf(
                            "Id результата" to processArticle.id,
                            "Содержание" to processArticle.content,
                        )
                    )
        }

        private fun articlePostedMessage(
            processedArticle: ProcessedArticle,
            primaryChannel: Channel,
            forwardChannels: Collection<Channel>
        ): String {
            return "Новость ${processedArticle.id.toString().hashTagHTML()}" +
                    "\n" +
                    "опубликована в ${primaryChannel.name.boldHTML()}" +
                    "\n" +
                    "и переслана в ${forwardChannels.joinToString { it.name.boldHTML() }}"
        }

        private fun primaryChannelSelectionMessage(): String {
            return "Выберите первичный канал"
        }

        private fun finalPostActionMessage(mediasCount: Int): String {
            return "Выберите когда опубликовать новость, прикрепите необходимые медиа файлы (${mediasCount} файлов прикреплено)"
        }

        //
        // MARKUPS
        //

        private fun articleReplyMarkup(articleId: Long, modes: List<String>): InlineKeyboardMarkup {
            return inlineKeyboard {
                row {
                    modes.map { mode ->
                        dataButton(mode, processArticleCallbackData(ProcessArticleData(mode, articleId)))
                    }
                }
            }
        }

        private fun primaryChannelSelectionReplyMarkup(channels: Collection<Channel>): InlineKeyboardMarkup {
            return inlineKeyboard {
                channels.chunked(2).forEach { chunk ->
                    row {
                        chunk.forEach { channel ->
                            dataButton(
                                "${channel.name} | ${channel.platform.name}",
                                selectChannelCallbackData(channel.id)
                            )
                        }
                    }
                }
            }
        }

        private fun finalPostActionReplyMarkup(currentDelay: Duration): InlineKeyboardMarkup {
            return inlineKeyboard {
                row {
                    dataButton("-10м", delayDeltaCallbackData(-10))
                    dataButton("-1м", delayDeltaCallbackData(-1))
                    dataButton("+1м", delayDeltaCallbackData(1))
                    dataButton("+10м", delayDeltaCallbackData(10))
                }
                row {
                    val message = when (currentDelay) {
                        Duration.ZERO -> "Опубликовать сейчас"
                        else -> "Опубликовать через ${currentDelay.minutes}м"
                    }
                    dataButton(message, postCallbackData())
                }
            }
        }

        private fun postReplyMarkup(channels: Collection<Channel>, selected: Set<Long>): InlineKeyboardMarkup {
            return inlineKeyboard {
                channels.chunked(2).forEach { chunk ->
                    row {
                        chunk.forEach { channel ->
                            val isSelected: Boolean = selected.contains(channel.id)
                            val callbackData = when (isSelected) {
                                true -> deselectChannelCallbackData(channel.id)
                                false -> selectChannelCallbackData(channel.id)
                            }
                            dataButton(("[+] ".takeIf { isSelected } ?: "") +
                                    channel.name +
                                    " | " +
                                    channel.platform.name,
                                callbackData)
                        }
                    }
                }

                row {
                    dataButton("Опубликовать", postCallbackData())
                }
            }
        }

        private val PAGE_REGEX = Regex("^[1-9]\\d*\$")
        private fun paginationMarkup(currentPage: Int, totalPages: Int): InlineKeyboardMarkup? {
            if (currentPage < 1) {
                throw IllegalArgumentException("Невалидное значение страницы: $currentPage/$totalPages")
            }
            if (totalPages == 0) {
                return null
            }

            return inlineKeyboard {
                row {
                    if (currentPage > 2) {
                        dataButton("⏪ 1", "1")
                    }

                    // Previous page button (if applicable)
                    if (currentPage > 1) {
                        val prev = (currentPage - 1).toString()
                        dataButton(prev, prev)
                    }

                    // Current page (highlighted)
                    dataButton("\uD83D\uDD39 $currentPage \uD83D\uDD39", "current")

                    // Next page button (if applicable)
                    if (currentPage < totalPages) {
                        val next = (currentPage + 1).toString()
                        dataButton(next, next)
                    }

                    // Last page button (only if currentPage is far from the last page)
                    if (currentPage < totalPages - 1) {
                        dataButton("⏩ $totalPages", totalPages.toString())
                    }
                }
            }
        }

        //
        // CALLBACKS
        //

        data class ProcessArticleData(
            val mode: String,
            val articleId: Long
        )

        private val PROCESS_ARTICLE_REGEX = Regex("PA_.*?_.*")
        private fun processArticleCallbackData(articleData: ProcessArticleData): String {
            return "PA_${articleData.mode}_${articleData.articleId}"
        }

        private fun getProcessArticleData(data: String): ProcessArticleData {
            return data
                .substringAfter("PA_")
                .split("_").let {
                    ProcessArticleData(
                        it[0],
                        it[1].toLong()
                    )
                }
        }

        private val SELECT_CHANNEL_REGEX = Regex("SE_.*")
        private fun selectChannelCallbackData(channelId: Long): String {
            return "SE_${channelId}"
        }

        private fun getSelectChannelId(data: String): Long {
            return data.substringAfter("_").toLong()
        }

        private val DESELECT_CHANNEL_REGEX = Regex("DESE_.*")
        private fun deselectChannelCallbackData(channelId: Long): String {
            return "DESE_${channelId}"
        }

        private fun getDeselectChannelId(data: String): Long {
            return data.substringAfter("_").toLong()
        }

        private fun postCallbackData(): String {
            return "POST"
        }

        private val DELAY_DELTA_REGEX = Regex("^\\d+")
        private fun delayDeltaCallbackData(minutes: Int): String {
            return minutes.toString()
        }

        //
        // UTILS
        //

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
    }
}