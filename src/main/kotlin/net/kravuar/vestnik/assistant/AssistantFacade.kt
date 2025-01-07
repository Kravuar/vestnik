package net.kravuar.vestnik.assistant

import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.post.Post
import java.io.InputStream

interface AssistantFacade {
    fun notifyNewArticle(article: Article)
    fun makePost(post: Post)
    fun makePostWithMedia(post: Post, media: List<InputStream>)
    fun makePostAsReply(post: Post, channels: List<Channel>)
}