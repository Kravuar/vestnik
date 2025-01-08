package net.kravuar.vestnik.post

import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.processor.ProcessedArticle
import java.util.Optional

interface PostsFacade {
    data class PostInput(
        val processedArticle: Optional<ProcessedArticle>,
        val channel: Optional<Channel>,
        val channelPostId: Optional<Long>,
        val isForwarded: Optional<Boolean>,
    )
    fun getPost(postId: Long): Post
    fun getPostsOfArticle(articleId: Long): List<Post>
    fun existsPostOfProcessedArticleAndChannel(processedArticleId: Long, channelId: Long): Boolean
    fun addPost(postInput: PostInput): Post
}