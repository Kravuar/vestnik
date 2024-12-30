package net.kravuar.vestnik.articles

import net.kravuar.vestnik.source.Source
import org.springframework.scheduling.TaskScheduler
import java.util.concurrent.ScheduledFuture

internal class ArticleScheduler(
    private val scheduler: TaskScheduler,
    private val articleFacade: ArticleFacade,
) {
    private val tasksBySource = mutableMapOf<String, ScheduledFuture<*>>()

    fun stopPollingAll() {
        tasksBySource.values.forEach { it.cancel(true) }
    }

    @Synchronized
    fun startPolling(source: Source) {
        source.run {
            tasksBySource[name]?.cancel(true)
            tasksBySource[name] = scheduler.scheduleWithFixedDelay({
                articleFacade.fetchAndStoreLatestNews(name, scheduleDelay)
            }, scheduleDelay)
        }
    }

    fun stopPolling(source: String) {
        tasksBySource[source]?.cancel(true)
    }
}