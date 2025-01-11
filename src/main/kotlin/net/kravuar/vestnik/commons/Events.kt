package net.kravuar.vestnik.commons

import org.springframework.context.ApplicationEvent
import org.springframework.core.ResolvableType
import org.springframework.core.ResolvableTypeProvider

enum class EntityState {
    CREATED,
    UPDATED,
    DELETED
}

class EntityEvent<T>(
    source: Any,
    val entity: T,
    val state: EntityState,
): ApplicationEvent(source), ResolvableTypeProvider {
    companion object {
        fun <T> created(source: Any, entity: T) = EntityEvent(source, entity, EntityState.CREATED)
        fun <T> updated(source: Any, entity: T) = EntityEvent(source, entity, EntityState.UPDATED)
        fun <T> deleted(source: Any, entity: T) = EntityEvent(source, entity, EntityState.DELETED)
    }

    override fun toString(): String {
        return "EntityEvent(source=$source, state=$state, entity=$entity)"
    }

    override fun getResolvableType(): ResolvableType {
        return ResolvableType.forClassWithGenerics(javaClass, ResolvableType.forInstance(entity))
    }
}