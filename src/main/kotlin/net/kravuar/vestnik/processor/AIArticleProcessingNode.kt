package net.kravuar.vestnik.processor

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import net.kravuar.vestnik.source.Source

@Entity
@Table(indexes = [
    Index(columnList = "source,mode", unique = true),
])
internal class AIArticleProcessingNode(
    @ManyToOne(optional = false)
    @Column(nullable = false, updatable = false)
    var source: Source,
    @Column(nullable = false)
    var mode: String,
    @Column(nullable = false)
    var model: String,
    @Column(nullable = false)
    var temperature: Double,
    @Column(nullable = false)
    var prompt: String,
    @OneToOne(fetch = FetchType.LAZY)
    var parent: AIArticleProcessingNode? = null,
    @OneToOne(mappedBy = "parent")
    var child: AIArticleProcessingNode? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
)