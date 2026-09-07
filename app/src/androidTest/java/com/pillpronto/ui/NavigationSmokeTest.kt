package com.pillpronto.ui

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import com.pillpronto.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Test de fum: navigarea intre cele 3 tab-uri din bottom bar functioneaza si schimba ecranul. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class NavigationSmokeTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    // POST_NOTIFICATIONS trebuie acordata dinainte de pornirea Activity-ii (API 33+),
    // altfel dialogul de sistem blocheaza arborele Compose in timpul testului.
    @get:Rule
    val ruleChain: RuleChain = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuleChain.outerRule(hiltRule)
            .around(GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS))
            .around(composeRule)
    } else {
        RuleChain.outerRule(hiltRule).around(composeRule)
    }

    @Test
    fun bottomBar_switchesBetweenTabs() {
        // Porneste pe "Azi".
        composeRule.onNodeWithText("Dozele de azi").assertExists()

        composeRule.onNodeWithText("Tratamente").performClick()
        composeRule.onNodeWithText("Tratamentele mele").assertExists()

        composeRule.onNodeWithText("Aderență").performClick()
        composeRule.onNodeWithText("Aderența (ultimele 30 zile)").assertExists()

        composeRule.onNodeWithText("Azi").performClick()
        composeRule.onNodeWithText("Dozele de azi").assertExists()
    }
}
