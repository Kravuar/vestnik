package net.kravuar.vestnik.post

interface PostsFacade {
    fun getPosts(articleId: Long): List<Post>
    fun addPost(articleId: Long, channelId: String, channelPostId: Long, adminId: String): Post
}