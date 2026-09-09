package com.pillpronto.ui.access

import com.pillpronto.domain.model.CaregiverSummary
import com.pillpronto.domain.model.LinkRole
import com.pillpronto.domain.model.LinkStatus
import com.pillpronto.domain.usecase.CreateInviteUseCase
import com.pillpronto.domain.usecase.GetLocalPatientProfileIdUseCase
import com.pillpronto.domain.usecase.GetMyCaregiversUseCase
import com.pillpronto.domain.usecase.GetMyOutgoingLinksUseCase
import com.pillpronto.domain.usecase.RevokeLinkUseCase
import com.pillpronto.util.FakeLinkRepository
import com.pillpronto.util.FakePatientProfileIdProvider
import com.pillpronto.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ManageProfessionalAccessViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val linkRepository = FakeLinkRepository()
    private val patientProfileIdProvider = FakePatientProfileIdProvider("patient-1")

    private fun createViewModel() = ManageProfessionalAccessViewModel(
        GetLocalPatientProfileIdUseCase(patientProfileIdProvider),
        CreateInviteUseCase(linkRepository),
        GetMyOutgoingLinksUseCase(linkRepository),
        GetMyCaregiversUseCase(linkRepository),
        RevokeLinkUseCase(linkRepository)
    )

    @Test
    fun `rolul implicit la pornire e DOCTOR`() = runTest {
        val vm = createViewModel()

        assertEquals(LinkRole.DOCTOR, vm.state.value.selectedRole)
    }

    @Test
    fun `doar legaturile DOCTOR sau PHARMACIST apar in lista`() = runTest {
        linkRepository.createInvite("patient-1", LinkRole.CAREGIVER_VIEWER) // nu trebuie sa apara
        linkRepository.createInvite("patient-1", LinkRole.DOCTOR)
        linkRepository.createInvite("patient-1", LinkRole.PHARMACIST)

        val vm = createViewModel()

        assertEquals(2, vm.state.value.links.size)
        assertEquals(setOf(LinkRole.DOCTOR, LinkRole.PHARMACIST), vm.state.value.links.map { it.role }.toSet())
    }

    @Test
    fun `generarea unei invitatii foloseste rolul selectat`() = runTest {
        val vm = createViewModel()
        vm.onRoleSelected(LinkRole.PHARMACIST)

        vm.onGenerateInvite()

        assertEquals(LinkRole.PHARMACIST, vm.state.value.links.first().role)
    }

    @Test
    fun `esecul la generare seteaza GENERATE_FAILED`() = runTest {
        linkRepository.createInviteError = RuntimeException("boom")
        val vm = createViewModel()

        vm.onGenerateInvite()

        assertEquals(ManageAccessError.GENERATE_FAILED, vm.state.value.error)
    }

    @Test
    fun `legaturile ACCEPTED sunt potrivite cu numele profesionistului`() = runTest {
        linkRepository.createInvite("patient-1", LinkRole.DOCTOR)
        linkRepository.links[0] = linkRepository.links.first()
            .copy(granteeUserId = "doctor-1", status = LinkStatus.ACCEPTED)
        linkRepository.caregivers["patient-1"] = listOf(CaregiverSummary("doctor-1", "Dr. Popescu"))

        val vm = createViewModel()

        assertEquals("Dr. Popescu", vm.state.value.grantedNames["doctor-1"])
    }

    @Test
    fun `revocarea marcheaza legatura ca REVOKED`() = runTest {
        val vm = createViewModel()
        vm.onGenerateInvite()
        val linkId = vm.state.value.links.first().id

        vm.onRevoke(linkId)

        assertEquals(LinkStatus.REVOKED, vm.state.value.links.first().status)
    }

    @Test
    fun `esecul la revocare seteaza REVOKE_FAILED`() = runTest {
        val vm = createViewModel()
        vm.onGenerateInvite()
        val linkId = vm.state.value.links.first().id
        linkRepository.revokeLinkError = RuntimeException("boom")

        vm.onRevoke(linkId)

        assertEquals(ManageAccessError.REVOKE_FAILED, vm.state.value.error)
    }
}
