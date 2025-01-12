package net.kravuar.vestnik.processor.nodes

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import net.kravuar.vestnik.source.Source

interface AIArticleProcessingNode {
    var model: String
    var temperature: Double
    var prompt: String
}

@Entity
class ChainedAIArticleProcessingNode(
    @ManyToOne(optional = true)
    var source: Source? = null,
    @Column(nullable = false)
    @NotBlank
    @Size(max = 16)
    var mode: String,
    @NotBlank
    @Column(nullable = false)
    override var model: String,
    @Column(nullable = false)
    @Size(min = 0, max = 1)
    override var temperature: Double,
    @NotBlank
    @Column(nullable = false)
    @Lob
    override var prompt: String,
    @OneToOne(fetch = FetchType.LAZY)
    var parent: ChainedAIArticleProcessingNode? = null,
    @OneToOne(mappedBy = "parent")
    var child: ChainedAIArticleProcessingNode? = null,
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,
) : AIArticleProcessingNode {
    override fun toString(): String {
        return "AIProcessingNode(id=$id, mode=$mode, ${source?.let { "source=${it.name}," } ?: ","} model='$model', temperature=$temperature, prompt='$prompt', parent=${parent?.id})"
    }
}