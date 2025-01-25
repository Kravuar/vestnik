package net.kravuar.vestnik.channels

import com.google.common.util.concurrent.Striped
import jakarta.transaction.Transactional
import net.kravuar.vestnik.commons.Page
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.ProcessedArticle
import net.kravuar.vestnik.source.Source
import org.apache.logging.log4j.LogManager
import org.springframework.data.domain.PageRequest
import kotlin.concurrent.withLock

internal open class SimpleChannelsFacade(
    private val channelRepository: ChannelRepository,
    private val postsFacade: PostsFacade,
    private val publishers: Map<ChannelPlatform, PostPublisher>
) : ChannelsFacade {
    private val locks = Striped.lazyWeakLock(7)

    override fun getChannels(source: Source?, page: Int): Page<Channel> {
        return channelRepository.findChannels(
            source,
            PageRequest.of(
                page - 1,
                Page.DEFAULT_PAGE_SIZE
            )
        ).let {
            Page(
                it.totalPages,
                it.content
            )
        }
    }

    override fun getAllChannels(source: Source?, page: Int): Page<Channel> {
        return channelRepository.findAllChannels(
            source,
            PageRequest.of(
                page - 1,
                Page.DEFAULT_PAGE_SIZE
            )
        ).let {
            Page(
                it.totalPages,
                it.content
            )
        }
    }

    override fun getChannelByName(name: String): Channel {
        return channelRepository
            .findByName(name)
            .orElseThrow { IllegalArgumentException("Канал с именем $name не найден") }
    }

    @Transactional
    override fun addChannel(input: ChannelsFacade.ChannelInput): Channel {
        LOG.info("Добавление канала: $input")
        return channelRepository.save(
            Channel(
                input.id.orElseThrow { IllegalArgumentException("При создании канала id обязательно") },
                input.name.orElseThrow { IllegalArgumentException("При создании канала имя обязательно") },
                input.platform.orElse(ChannelPlatform.TG),
            ).apply {
                input.sources.ifPresent { sources = it }
            }).also {
            LOG.info("Добавлен канал: $it")
        }
    }

    @Transactional
    override fun deleteChannel(name: String): Boolean {
        LOG.info("Удаление канала: $name")
        return channelRepository.deleteByName(name).also {
            LOG.info("Удалён канал: $it")
        } > 0
    }

    override fun postArticle(
        processedArticle: ProcessedArticle,
        primaryChannel: Channel,
        forwardChannels: Collection<Channel>,
        media: List<ChannelsFacade.Media>
    ): ChannelsFacade.PublishingResult {
        LOG.info(
            "Публикация статьи: $processedArticle, в канал ${primaryChannel.name}" + if (forwardChannels.isNotEmpty()) {
                " с forward в: ${forwardChannels.joinToString { it.name }}"
            } else {
                ""
            }
        )
        require(forwardChannels.none { it.id == primaryChannel.id }) {
            "В каналах для Forwarding'а указан первичный канал"
        }

        val articleId = requireNotNull(processedArticle.article.id) {
            "Для публикуемой статьи ID статьи источника не может отсутствовать"
        }
        val lock = locks.get(articleId)

        // Do not allow many publications of same article at a time
        lock.withLock {
            // Check that it is not already posted in specified channels
            val currentChannelsByIds = listOf(
                primaryChannel,
                *forwardChannels.toTypedArray()
            ).associateBy { it.id }
            val postedChannelsByIds = postsFacade
                .getPostsOfArticle(articleId)
                .map { it.channel }
                .associateBy { it.id }

            val intersectionChannels = currentChannelsByIds.keys.intersect(postedChannelsByIds.keys)
                .map { currentChannelsByIds[it]!! }
            require(intersectionChannels.isEmpty()) {
                "Новость уже опубликована в следующих каналах: ${intersectionChannels.joinToString { it.name }}"
            }

            // Post article to primary, then forward to specified channels. Each action in separate transaction

            val primaryPublisher = publishers[primaryChannel.platform]
                ?: throw IllegalStateException("Публикатор в ${primaryChannel.platform} не найден")
            val primaryPost = primaryPublisher.publish(processedArticle, primaryChannel, media = media)
            LOG.info("Публикация статьи: $processedArticle в основной канал ${primaryChannel.name} выполнена")

            val forwardedPosts = forwardChannels.map {
                val forwardPublisher = publishers[it.platform]
                    ?: throw IllegalStateException("Публикатор в ${primaryChannel.platform} не найден")
                forwardPublisher.forward(primaryChannel, primaryPost.channelPostId, it).also { post ->
                    LOG.info("Forward статьи: $processedArticle в канал ${post.channel.name} выполнена")
                }
            }

            return ChannelsFacade.PublishingResult(
                primaryPost,
                forwardedPosts,
            )
        }
    }

    companion object {
        private val LOG = LogManager.getLogger(SimpleChannelsFacade::class.java)
    }
}