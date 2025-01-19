package net.kravuar.vestnik.post

import jakarta.transaction.Transactional
import net.kravuar.vestnik.channels.Channel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
@Transactional
internal interface PostsRepository: JpaRepository<Post, Long> {
    fun findByChannelAndChannelPostId(channel: Channel, messageId: Long): Optional<Post>
    fun findAllByProcessedArticleArticleId(articleId: Long): List<Post>
    fun existsPostByProcessedArticleIdAndChannelId(processedArticleId: Long, channelId: Long): Boolean
}