package net.kravuar.vestnik.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import net.kravuar.vestnik.channels.Channel
import net.kravuar.vestnik.processor.ProcessedArticle
import java.time.OffsetDateTime

@Entity
@Table(indexes = [
    Index(columnList = "processedArticleId,channelId", unique = true),
])
class Post(
    @ManyToOne(optional = false)
    var processedArticle: ProcessedArticle,
    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    var channel: Channel,
    @Column(nullable = false)
    var channelPostId: Long,
    @Column(nullable = false)
    var isForwarded: Boolean = false,
    @Column(nullable = false)
    val creationTimestamp: OffsetDateTime = OffsetDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)