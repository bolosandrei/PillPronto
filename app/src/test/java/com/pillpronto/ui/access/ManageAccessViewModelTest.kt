package com.pillpronto.ui.access

import com.pillpronto.domain.model.CaregiverSummary
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ManageAccessViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val linkRepository = FakeLinkRepository()
    private val patientProfileIdProvider = FakePatientProfileIdProvider("patient-1")

    private fun createViewModel() = ManageAccessViewModel(
        GetLocalPatientProfileIdUseCase(patientProfileIdProvider),
        CreateInviteUseCase(linkRepository),
        GetMyOutgoingLinksUseCase(linkRepository),
        GetMyCaregiversUseCase(linkRepository),
        RevokeLinkUseCase(linkRepository)
    )

    @Test
    fun `la init incarca legaturile existente ale profilului local`() = runTest {
        linkRepository.createInvite("patient-1")
        linkRepository.createInvite("alt-pacient") // nu trebuie sa apara

        val vm = createViewModel()

        assertEquals(1, vm.state.value.links.size)
        assertEquals(false, vm.state.value.isLoading)
    }

    @Test
    fun `generarea unei invitatii adauga un rand pending si reface lista`() = runTest {
        val vm = createViewModel()

        vm.onGenerateInvite()

        assertEquals(1, vm.state.value.links.size)
        assertEquals(LinkStatus.PENDING, vm.state.value.links.first().status)
        assertTrue(vm.state.value.links.first().inviteCode!!.isNotBlank())
    }

    @Test
    fun `esecul la generare seteaza GENERATE_FAILED`() = runTest {
        linkRepository.createInviteError = RuntimeException("boom")
        val vm = createViewModel()

        vm.onGenerateInvite()

        assertEquals(ManageAccessError.GENERATE_FAILED, vm.state.value.error)
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
    fun `legaturile ACCEPTED sunt potrivite cu numele Apartinatorului`() = runTest {
        linkRepository.createInvite("patient-1")
        val linkId = linkRepository.links.first().id
        // Simuleaza revendicarea codului de catre un Apartinator (claim_link seteaza grantee+accepted).
        linkRepository.links[0] = linkRepository.links.first()
            .copy(granteeUserId = "caregiver-1", status = LinkStatus.ACCEPTED)
        linkRepository.caregivers["patient-1"] = listOf(CaregiverSummary("caregiver-1", "Maria"))

        val vm = createViewModel()

        assertEquals("Maria", vm.state.value.caregiverNames["caregiver-1"])
        assertEquals(LinkStatus.ACCEPTED, vm.state.value.links.first { it.id == linkId }.status)
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
