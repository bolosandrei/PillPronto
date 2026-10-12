package com.pillpronto.data.local.dao

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pillpronto.data.local.PillProntoDatabase
import com.pillpronto.data.local.entity.LocalAccountProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Teste Room pe baza de date in-memory (fix izolare date locale) — vezi CLAUDE.md pt. context. */
@RunWith(AndroidJUnit4::class)
class LocalAccountProfileDaoTest {

    private lateinit var db: PillProntoDatabase
    private lateinit var dao: LocalAccountProfileDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, PillProntoDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.localAccountProfileDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun getByUserId_ftaraRandInsertat_intoarceNull() = runTest {
        assertNull(dao.getByUserId("user-1"))
    }

    @Test
    fun insertAndGetByUserId_roundTrip() = runTest {
        dao.insert(LocalAccountProfileEntity("user-1", "profile-1", 1000L))

        val loaded = dao.getByUserId("user-1")

        assertEquals("profile-1", loaded?.patientProfileId)
    }

    @Test
    fun insert_cuAcelasiPatientProfileId_eRespinsaDeIndexUnic() = runTest {
        dao.insert(LocalAccountProfileEntity("user-1", "profile-1", 1000L))

        var threw = false
        try {
            dao.insert(LocalAccountProfileEntity("user-2", "profile-1", 2000L))
        } catch (e: Exception) {
            threw = true
        }

        assertEquals(true, threw)
    }
}
