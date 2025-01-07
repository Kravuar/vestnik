package net.kravuar.vestnik.processor

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.kravuar.vestnik.articles.Article
import java.time.OffsetDateTime

@Entity
class ProcessedArticle(
    @ManyToOne(optional = false)
    var article: Article,
    @Column(nullable = false)
    @Lob
    var content: String,
    @Column(nullable = false)
    @NotBlank
    @Size(max = 16)
    var mode: String,
    @Column(nullable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,
)