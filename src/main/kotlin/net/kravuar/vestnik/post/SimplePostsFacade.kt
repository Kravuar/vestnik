package net.kravuar.vestnik.post

import jakarta.transaction.Transactional
import net.kravuar.vestnik.articles.ArticlesFacade
import net.kravuar.vestnik.destination.ChannelsFacade

internal class SimplePostsFacade(
    private val postsRepository: PostsRepository,
    private val articlesFacade: ArticlesFacade,
    private val channelsFacade: ChannelsFacade,
) : PostsFacade {
    override fun getPosts(articleId: Long): List<Post> {
        return postsRepository.findAllByArticleId(articleId)
    }

    @Transactional
    override fun addPost(articleId: Long, channelId: String, channelPostId: Long, adminId: String): Post {
        val article = articlesFacade.getArticle(articleId)
        val channel = channelsFacade.getChannel(channelId)
        return postsRepository.save(Post(
            article,
            channel,
            channelPostId,
            adminId
        ))
    }
}