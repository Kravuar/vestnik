package net.kravuar.vestnik.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import net.kravuar.vestnik.articles.Article
import net.kravuar.vestnik.destination.Destination
import java.time.OffsetDateTime

@Entity
class Post(
    @ManyToOne(optional = false)
    var article: Article,
    @ManyToOne(optional = false)
    var destination: Destination,
    @Column(nullable = false)
    var adminId: String,
    @Column(nullable = false)
    var adminName: String,
    @Column(nullable = false)
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
    @Column(nullable = false)
    var targetPostId: Int,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int? = null,
)