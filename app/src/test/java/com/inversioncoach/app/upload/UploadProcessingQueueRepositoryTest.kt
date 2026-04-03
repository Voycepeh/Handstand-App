package com.inversioncoach.app.upload

import com.inversioncoach.app.model.UploadJobStatus
import com.inversioncoach.app.model.UploadProcessingJob
import com.inversioncoach.app.storage.db.UploadProcessingJobDao
import com.inversioncoach.app.storage.repository.UploadProcessingQueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UploadProcessingQueueRepositoryTest {
    @Test
    fun enqueueHonorsCapacity() = runTest {
        val dao = FakeDao()
        val repo = UploadProcessingQueueRepository(dao, maxPendingJobs = 2)

        val first = repo.enqueue("uri://1", "HOLD_BASED", null, null, false, false, null)
        val second = repo.enqueue("uri://2", "HOLD_BASED", null, null, false, false, null)
        val third = repo.enqueue("uri://3", "HOLD_BASED", null, null, false, false, null)

        assertNotNull(first)
        assertNotNull(second)
        assertNull(third)
        assertEquals(2, dao.observeAll().first().size)
    }


    @Test
    fun runningJobDoesNotConsumePendingCapacity() = runTest {
        val dao = FakeDao()
        val repo = UploadProcessingQueueRepository(dao, maxPendingJobs = 1)
        val running = repo.enqueue("uri://running", "HOLD_BASED", null, null, false, false, null)!!
        repo.save(running.copy(status = UploadJobStatus.RUNNING))

        val queued = repo.enqueue("uri://queued", "HOLD_BASED", null, null, false, false, null)

        assertNotNull(queued)
    }

    @Test
    fun retryingJobConsumesPendingCapacity() = runTest {
        val dao = FakeDao()
        val repo = UploadProcessingQueueRepository(dao, maxPendingJobs = 1)
        val retrying = repo.enqueue("uri://retry", "HOLD_BASED", null, null, false, false, null)!!
        repo.save(retrying.copy(status = UploadJobStatus.RETRYING))

        val queued = repo.enqueue("uri://queued", "HOLD_BASED", null, null, false, false, null)

        assertNull(queued)
    }

    @Test
    fun getNextQueuedSkipsCompleted() = runTest {
        val dao = FakeDao()
        val repo = UploadProcessingQueueRepository(dao, maxPendingJobs = 3)
        val first = repo.enqueue("uri://1", "HOLD_BASED", null, null, false, false, null)!!
        val second = repo.enqueue("uri://2", "HOLD_BASED", null, null, false, false, null)!!
        repo.save(first.copy(status = UploadJobStatus.COMPLETED))

        assertEquals(second.jobId, repo.getNextQueuedJob()?.jobId)
    }
}

private class FakeDao : UploadProcessingJobDao {
    private val items = linkedMapOf<String, UploadProcessingJob>()
    private val flow = MutableStateFlow<List<UploadProcessingJob>>(emptyList())

    override suspend fun upsert(job: UploadProcessingJob) {
        items[job.jobId] = job
        flow.value = items.values.sortedBy { it.enqueueOrder }
    }

    override fun observeAll(): Flow<List<UploadProcessingJob>> = flow

    override suspend fun getById(jobId: String): UploadProcessingJob? = items[jobId]

    override suspend fun getActiveJob(): UploadProcessingJob? =
        items.values.filter { it.status == UploadJobStatus.RUNNING || it.status == UploadJobStatus.RETRYING }
            .minByOrNull { it.enqueueOrder }

    override suspend fun getNextQueuedJob(): UploadProcessingJob? =
        items.values.filter { it.status == UploadJobStatus.QUEUED }.minByOrNull { it.enqueueOrder }

    override suspend fun getPendingJobs(): List<UploadProcessingJob> =
        items.values.filter { it.status in setOf(UploadJobStatus.QUEUED, UploadJobStatus.RETRYING) }

    override suspend fun getActiveQueueCount(): Int = getPendingJobs().size

    override suspend fun updateStatus(jobId: String, status: UploadJobStatus, updatedAt: Long) {
        val current = items[jobId] ?: return
        upsert(current.copy(status = status, updatedAt = updatedAt))
    }
}
