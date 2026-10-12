package com.pillpronto.util

import com.pillpronto.domain.repository.GuestProfileIdProvider

class FakeGuestProfileIdProvider(override val guestProfileId: String = "guest-id") : GuestProfileIdProvider
