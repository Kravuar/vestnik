package net.kravuar.vestnik.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.destination.Channel
import java.time.OffsetDateTime

@Entity
class Post(
    @ManyToOne(optional = false)
    var article: Article,
    @ManyToOne(optional = false)
    var channel: Channel,
    @Column(nullable = false)
    var channelPostId: Long,
    @Column(nullable = false)
    var adminId: String,
    @Column(nullable = false)
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
)