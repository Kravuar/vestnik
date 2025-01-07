package net.kravuar.vestnik.assistant

import dev.inmo.micro_utils.coroutines.subscribeSafelyWithoutExceptions
import dev.inmo.tgbotapi.bot.TelegramBot
import dev.inmo.tgbotapi.extensions.api.edit.reply_markup.editMessageReplyMarkup
import dev.inmo.tgbotapi.extensions.api.send.reply
import dev.inmo.tgbotapi.extensions.api.send.send
import dev.inmo.tgbotapi.extensions.api.send.sendMessage
import dev.inmo.tgbotapi.extensions.behaviour_builder.buildBehaviour
import dev.inmo.tgbotapi.extensions.behaviour_builder.expectations.waitMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.triggers_handling.onMessageDataCallbackQuery
import dev.inmo.tgbotapi.extensions.behaviour_builder.utils.marker_factories.ByIdCallbackQueryMarkerFactory
import dev.inmo.tgbotapi.extensions.utils.extensions.sameMessage
import dev.inmo.tgbotapi.extensions.utils.formatting.boldHTML
import dev.inmo.tgbotapi.extensions.utils.formatting.hashTagHTML
import dev.inmo.tgbotapi.extensions.utils.types.buttons.dataButton
import dev.inmo.tgbotapi.extensions.utils.types.buttons.inlineKeyboard
import dev.inmo.tgbotapi.extensions.utils.updates.retrieving.longPolling
import dev.inmo.tgbotapi.types.ChatId
import dev.inmo.tgbotapi.types.RawChatId
import dev.inmo.tgbotapi.types.UserId
import dev.inmo.tgbotapi.types.buttons.InlineKeyboardMarkup
import dev.inmo.tgbotapi.types.message.HTMLParseMode
import dev.inmo.tgbotapi.utils.regular
import dev.inmo.tgbotapi.utils.row
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.channels.ChannelsFacade
import net.kravuar.vestnik.post.Post
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.ProcessedArticle
import net.kravuar.vestnik.processor.ProcessedArticlesFacade
import net.kravuar.vestnik.processor.ai.AIArticleProcessingNodesFacade
import net.kravuar.vestnik.source.SourcesFacade
import org.apache.logging.log4j.LogManager
import java.io.InputStream
import kotlin.time.Duration.Companion.seconds

internal class TelegramAssistantFacade(
    adminChannelId: Long,
    adminsIds: Set<Long>,
    ownerId: Long,
    private val bot: TelegramBot,
    private val sourcesFacade: SourcesFacade,
    private val channelsFacade: ChannelsFacade,
    private val articlesFacade: ArticlesFacade,
    private val processedArticlesFacade: ProcessedArticlesFacade,
    private val postsFacade: PostsFacade,
    private val aiArticleProcessingNodesFacade: AIArticleProcessingNodesFacade
): AssistantFacade {
    private val adminChannel: ChatId = ChatId(RawChatId(adminChannelId))
    private val admins: Set<UserId> = adminsIds.map { UserId(RawChatId(it)) }.toSet()
    private val owner: UserId = UserId(RawChatId(ownerId))

    internal suspend fun start() {
        val behaviour = bot.buildBehaviour {
            onMessageDataCallbackQuery(
                dataRegex = PROCESS_ARTICLE_REGEX,
                markerFactory = ByIdCallbackQueryMarkerFactory
            ) { processCallback ->
                val data = processCallback.data.substringAfter("_")
                val (mode, id) = getProcessArticleData(data)
                LOG.info("Processing article $id, mode $mode")

                // Process
                val article = articlesFacade.getArticle(id)
                val processedArticle = processedArticlesFacade.processArticle(article, mode)

                // Retrieving channels
                val channels = article.source.channels.associateBy { it.id }
                val selected = mutableSetOf<String>()

                // Creating processed article message
                val processedArticleMessage = send(
                    chatId = processCallback.message.chat.id,
                    replyMarkup = postReplyMarkup(channels.values, selected),
                ) {
                    processedArticleMessage(processedArticle)
                }

                // Handle callback queries on that processed article message
                val processedArticleHandlerJob = waitMessageDataCallbackQuery().filter {
                    it.message.sameMessage(processedArticleMessage)
                }.subscribeSafelyWithoutExceptions(this) { processedArticleCallback ->
                    val selectedChanged = when {
                        // Select channel
                        SELECT_CHANNEL_REGEX.matches(processedArticleCallback.data) -> {
                            selected.add(getSelectChannelId(processedArticleCallback.data))
                            true
                        }
                        // Deselect channel
                        DESELECT_CHANNEL_REGEX.matches(processedArticleCallback.data) -> {
                            selected.remove(getDeselectChannelId(processedArticleCallback.data))
                            true
                        }
                        // Post article
                        postCallbackData() == processedArticleCallback.data -> {
                            val selectedChannels = selected.map {
                                requireNotNull(channels[it])
                            }

                            val primaryChannelSelectionMessage = send(
                                chatId = adminChannel,
                                replyMarkup = primaryChannelsSelectionReplyMarkup(selectedChannels)
                            ) {
                                regular("Выберите первичный канал")
                            }

                            val primaryChannel = requireNotNull(channels[
                                getSelectChannelId(waitMessageDataCallbackQuery()
                                    .filter {
                                        it.message.sameMessage(primaryChannelSelectionMessage)
                                    }.first().data
                                )
                            ]) { "Выбранный первичный канал не может отсутствовать" }

                            val forwardChannels = selectedChannels
                                .filter { it != primaryChannel }
                            channelsFacade.postArticle(processedArticle, primaryChannel, forwardChannels)

                            reply(to = primaryChannelSelectionMessage) {
                                articlePostedMessage(processedArticle, primaryChannel, forwardChannels)
                            }
                            false
                        }
                        // Post article with media
                        postWithMediaCallbackData() == processedArticleCallback.data -> {
                            // TODO: this
                            false
                        }

                        else -> {
                            throw IllegalArgumentException("Неизвестный callback: ${processedArticleCallback.data}")
                        }
                    }
                    if (selectedChanged) {
                        editMessageReplyMarkup(
                            message = processedArticleMessage,
                            replyMarkup = postReplyMarkup(channels.values, selected)
                        )
                    }
                }

                delay(180.seconds)
                processedArticleHandlerJob.cancel("Too old")
                reply(
                    to = processedArticleMessage
                ) {
                    regular("Outdated")
                }
            }

            allUpdatesFlow.subscribeSafelyWithoutExceptions(this) {
                LOG.debug(it)
            }
        }
        bot.longPolling(behaviour)
    }

    override fun notifyNewArticle(article: Article) {
        runBlocking {
            val id = requireNotNull(article.id) { "ID обрабатываемой статьи не может отсутствовать."}
            bot.sendMessage(
                chatId = adminChannel,
                replyMarkup = articleReplyMarkup(id, processedArticlesFacade.getModes(article)),
                text = newArticleMessage(article),
                parseMode = HTMLParseMode
            )
        }
    }

    override fun makePost(post: Post) {
        TODO("Not yet implemented")
    }

    override fun makePostWithMedia(post: Post, media: List<InputStream>) {
        TODO("Not yet implemented")
    }

    override fun makePostAsReply(post: Post, channels: List<Channel>) {
        TODO("Not yet implemented")
    }

    companion object {
        private val LOG = LogManager.getLogger(TelegramAssistantFacade::class.java)

        private const val SPLIT = "===============\n"
        private const val SPLIT_INPUT = ":="

        //
        // PARSING/FORMATTING
        //

        private val INPUT_REGEX = Regex("^(\\S+)\\s*$SPLIT_INPUT\\s*([\\s\\S]*?)(?=\\n^\\S+\\s*$SPLIT_INPUT\\s*|\\Z)")
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
                .append(SPLIT_INPUT)
                .append(pair.second.toString().boldHTML())
                .appendLine()
                .toString()
        }

        private fun writeForMessage(pairs: Map<String, Any?>): String {
            return pairs.entries.joinToString("\n") { writeForMessage(it.toPair()) }
        }

        private fun writeForMessage(manyPairs: List<Map<String, Any?>>): String {
            return manyPairs.joinToString(SPLIT) { writeForMessage(it) }
        }

        //
        // MESSAGES
        //

        private fun newArticleMessage(article: Article): String {
            return """Получена новая статья:

            ${
                writeForMessage(mapOf(
                    "Id статьи" to article.id,
                    "Источник" to article.source.name,
                    "Заголовок" to article.title,
                    "Описание" to article.description,
                    "URL" to article.url
                ))
            }
            """
        }

        private fun processedArticleMessage(processArticle: ProcessedArticle): String {
            return """Результат обработки режимом ${processArticle.mode.boldHTML()}:

            ${
                writeForMessage(mapOf(
                    "Id результата" to processArticle.id,
                    "Содержание" to processArticle.content,
                ))
            }
            """
        }

        private fun articlePostedMessage(processedArticle: ProcessedArticle, primaryChannel: Channel, forwardChannels: Collection<Channel>): String {
            return """Новость ${processedArticle.id.toString().hashTagHTML()} опубликована в ${primaryChannel.name.boldHTML()}
            и переслана в ${forwardChannels.joinToString { it.name.boldHTML() }}
            """.trimIndent()
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
                row {
                    dataButton("Отклонить", declineArticleCallbackData(articleId))
                }
            }
        }

        private fun primaryChannelsSelectionReplyMarkup(channels: Collection<Channel>): InlineKeyboardMarkup {
            return inlineKeyboard {
                channels.chunked(2).forEach { chunk ->
                    row {
                        chunk.forEach { channel ->
                            dataButton("${channel.name} | ${channel.platform.name}", selectChannelCallbackData(channel.id))
                        }
                    }
                }
            }
        }

        private fun postReplyMarkup(channels: Collection<Channel>, selected: Set<String>): InlineKeyboardMarkup {
            return inlineKeyboard {
                channels.chunked(2).forEach { chunk ->
                    row {
                        chunk.forEach { channel ->
                            val isSelected: Boolean = selected.contains(channel.id)
                            val callbackData = when(isSelected) {
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
                    dataButton("Опубликовать с Медиа", postWithMediaCallbackData())
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
                .split("_").let { ProcessArticleData(
                    it[0],
                    it[1].toLong()
                ) }
        }

        private val DECLINE_ARTICLE_REGEX = Regex("DA_.*")
        private fun declineArticleCallbackData(articleId: Long): String {
            return "DA_${articleId}"
        }
        private fun getDeclineArticleId(data: String): Long {
            return data.substringAfter("_").toLong()
        }

        private val SELECT_CHANNEL_REGEX = Regex("SE_.*")
        private fun selectChannelCallbackData(channelId: String): String {
            return "SE_${channelId}"
        }
        private fun getSelectChannelId(data: String): String {
            return data.substringAfter("_")
        }

        private val DESELECT_CHANNEL_REGEX = Regex("DESE_.*")
        private fun deselectChannelCallbackData(channelId: String): String {
            return "DESE_${channelId}"
        }
        private fun getDeselectChannelId(data: String): String {
            return data.substringAfter("_")
        }

        private fun postCallbackData(): String {
            return "POST"
        }

        private fun postWithMediaCallbackData(): String {
            return "POST_WM"
        }
    }
}