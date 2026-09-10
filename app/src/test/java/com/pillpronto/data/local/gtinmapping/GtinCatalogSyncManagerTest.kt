package com.pillpronto.data.local.gtinmapping

import com.pillpronto.data.remote.dto.GtinMappingDto
import com.pillpronto.util.FakeGtinCatalogRemoteDataSource
import com.pillpronto.util.FakeGtinMappingDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GtinCatalogSyncManagerTest {

    private val remoteDataSource = FakeGtinCatalogRemoteDataSource()
    private val dao = FakeGtinMappingDao()
    private val syncManager = GtinCatalogSyncManager(remoteDataSource, dao)

    @Test
    fun `pull scrie mapari noi in dao`() = runTest {
        remoteDataSource.pullResult = listOf(
            GtinMappingDto(
                gtin = "05901234123457", codCim = "W43451001",
                confirmedAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z"
            )
        )

        syncManager.pull()

        assertEquals("W43451001", dao.rows["05901234123457"]?.codCim)
    }

    @Test
    fun `pull suprascrie o ghicire locala existenta`() = runTest {
        dao.rows["05901234123457"] = com.pillpronto.data.local.entity.GtinMappingEntity(
            gtin = "05901234123457", codCim = "W_GRESIT", confirmedAt = 0L
        )
        remoteDataSource.pullResult = listOf(
            GtinMappingDto(
                gtin = "05901234123457", codCim = "W_CORECT",
                confirmedAt = "2026-01-01T00:00:00Z", updatedAt = "2026-01-01T00:00:00Z"
            )
        )

        syncManager.pull()

        assertEquals("W_CORECT", dao.rows["05901234123457"]?.codCim)
    }

    @Test
    fun `pull gol nu apeleaza dao`() = runTest {
        remoteDataSource.pullResult = emptyList()

        syncManager.pull()

        assertTrue(dao.rows.isEmpty())
    }
}
