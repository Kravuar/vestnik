package net.kravuar.vestnik.post

import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
@Transactional
internal interface PostsRepository: JpaRepository<Post, Long> {
    fun findAllByProcessedArticleArticleId(articleId: Long): List<Post>
    fun existsPostByProcessedArticleIdAndChannelId(processedArticleId: Long, channelId: Long): Boolean
}