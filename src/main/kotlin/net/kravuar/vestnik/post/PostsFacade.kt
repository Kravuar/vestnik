package net.kravuar.vestnik.post

import java.util.Optional

interface PostsFacade {
    fun getPost(postId: Int): Post
    fun getPostOptional(postId: Int): Optional<Post>
    fun getPosts(articleId: Long): List<Post>
    fun addPost(articleId: Long, channelId: String, channelPostId: Long, adminId: String): Post
}