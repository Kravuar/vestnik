package net.kravuar.vestnik.channels

import com.google.common.util.concurrent.Striped
import jakarta.transaction.Transactional
import net.kravuar.vestnik.post.Post
import net.kravuar.vestnik.post.PostsFacade
import net.kravuar.vestnik.processor.ProcessedArticle
import java.util.Optional
import kotlin.concurrent.withLock

internal abstract class AbstractPostPublisher(
    private val postsFacade: PostsFacade
) : PostPublisher {
    private val locks = Striped.lazyWeakLock(5)

    protected abstract fun sendPost(
        processedArticle: ProcessedArticle,
        channel: Channel,
        mediaList: List<ChannelsFacade.Media>
    ): MessageId

    protected abstract fun sendForward(
        originalChannel: Channel,
        messageId: MessageId,
        targetChannel: Channel
    ): MessageId

    protected data class PublisherResult(
        val messageId: MessageId,
        val isForwarded: Boolean
    )

    protected open fun createPost(
        processedArticle: ProcessedArticle,
        targetChannel: Channel,
        action: () -> PublisherResult,
    ): Post {
        val processedArticleId = requireNotNull(processedArticle.id) {
            "ID выкладываемой новости не может отсутствовать"
        }
        val lock = locks.get(processedArticleId)

        lock.withLock {
            require(
                !postsFacade.existsPostOfProcessedArticleAndChannel(
                    processedArticleId,
                    targetChannel.id
                )
            ) {
                "Пост для статьи с id=$processedArticleId уже существует в канале ${targetChannel.name}"
            }

            val publisherResult = action.invoke()
            return postsFacade.addPost(
                PostsFacade.PostInput(
                    Optional.of(processedArticle),
                    Optional.of(targetChannel),
                    Optional.of(publisherResult.messageId),
                    Optional.of(publisherResult.isForwarded)
                )
            )
        }
    }

    @Transactional
    override fun publish(processedArticle: ProcessedArticle, channel: Channel, media: List<ChannelsFacade.Media>): Post {
        return createPost(
            processedArticle,
            channel,
        ) {
            val messageId = sendPost(processedArticle, channel, media)
            PublisherResult(messageId, false)
        }
    }

    @Transactional
    override fun forward(originalChannel: Channel, messageId: MessageId, targetChannel: Channel): Post {
        val originalPost = postsFacade.getPost(messageId)
        return createPost(
            originalPost.processedArticle,
            targetChannel,
        ) {
            val forwardedMessageId = sendForward(originalChannel, messageId, targetChannel)
            PublisherResult(forwardedMessageId, true)
        }
    }
}