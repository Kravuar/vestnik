package net.kravuar.vestnik.post

import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.processor.ProcessedArticle
import java.util.Optional

interface PostsFacade {
    data class PostInput(
        val processedArticle: Optional<ProcessedArticle>,
        val channel: Optional<Channel>,
        val channelPostId: Optional<Long>,
        val adminId: Optional<String>,
        val isForwarded: Optional<Boolean>,
    )
    fun getPosts(articleId: Long): List<Post>
    fun addPost(postInput: PostInput): Post
}