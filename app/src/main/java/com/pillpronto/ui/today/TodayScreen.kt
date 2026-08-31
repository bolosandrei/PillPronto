package com.pillpronto.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val RO_LOCALE = Locale("ro")
private val FULL_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RO_LOCALE)

// Fereastra glisanta de date de pe ecranul "Azi": cate zile in trecut/viitor sunt selectabile.
private const val DATE_WINDOW_PAST_DAYS = 60L
private const val DATE_WINDOW_FUTURE_DAYS = 60L

@Composable
fun TodayScreen(padding: PaddingValues, vm: TodayViewModel = hiltViewModel()) {
    val doses by vm.doses.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val asNeededTreatments by vm.asNeededTreatments.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val context = LocalContext.current

    // Reevalueaza statusul permisiunii de alarme exacte cand revenim din setari.
    var canExact by remember { mutableStateOf(Permissions.canScheduleExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) canExact = Permissions.canScheduleExactAlarms(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                titleFor(selectedDate, today),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (selectedDate != today) {
                TextButton(onClick = { vm.onDateSelected(today) }) { Text("Azi") }
            }
        }

        DateStrip(
            today = today,
            selectedDate = selectedDate,
            onDateSelected = vm::onDateSelected,
            modifier = Modifier.padding(top = 12.dp)
        )

        if (!canExact) {
            ExactAlarmBanner(onOpenSettings = { Permissions.openExactAlarmSettings(context) })
        }

        // Actiune rapida pentru tratamentele "la nevoie" (PRN) — doar pe ziua curenta, nu are sens
        // sa "loghezi acum" pentru o zi din trecut/viitor selectata din fereastra glisanta.
        if (selectedDate == today && asNeededTreatments.isNotEmpty()) {
            AsNeededSection(
                treatments = asNeededTreatments,
                onLog = { vm.onLogAsNeeded(it) },
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (doses.isEmpty()) {
            Text(
                emptyMessageFor(selectedDate, today),
                Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                items(doses, key = { it.dose.id }) { item ->
                    DoseRow(item, onTake = { vm.onTake(item.dose.id) }, onSkip = { vm.onSkip(item.dose.id) })
                }
            }
        }
    }
}

private fun titleFor(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Dozele de azi"
    today.minusDays(1) -> "Dozele de ieri"
    today.plusDays(1) -> "Dozele de mâine"
    else -> "Dozele din ${date.format(FULL_DATE)}"
}

private fun emptyMessageFor(date: LocalDate, today: LocalDate): String =
    if (date == today)
        "Nicio doză programată azi. Adaugă un tratament din tab-ul Tratamente."
    else
        "Nicio doză programată în această zi."

/** Fereastra glisanta de date: selectie orizontala intre trecut si viitor, implicit ziua curenta. */
@Composable
private fun DateStrip(
    today: LocalDate,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val dates = remember(today) {
        (-DATE_WINDOW_PAST_DAYS..DATE_WINDOW_FUTURE_DAYS).map { today.plusDays(it) }
    }
    val todayIndex = DATE_WINDOW_PAST_DAYS.toInt()
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (todayIndex - 3).coerceAtLeast(0))

    // Cand selectia se schimba (tap pe chip sau butonul "Azi"), readuce ziua selectata in vizor.
    LaunchedEffect(selectedDate) {
        val index = dates.indexOf(selectedDate)
        if (index >= 0) {
            listState.animateScrollToItem((index - 3).coerceAtLeast(0))
        }
    }

    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(dates, key = { it.toEpochDay() }) { date ->
            DateChip(
                date = date,
                isSelected = date == selectedDate,
                isToday = date == today,
                onClick = { onDateSelected(date) }
            )
        }
    }
}

@Composable
private fun DateChip(date: LocalDate, isSelected: Boolean, isToday: Boolean, onClick: () -> Unit) {
    val dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, RO_LOCALE).replaceFirstChar { it.uppercase() }
    val background = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .clip(shape)
            .background(background)
            .let { if (isToday && !isSelected) it.border(1.dp, MaterialTheme.colorScheme.primary, shape) else it }
            .clickable(onClick = onClick)
            .width(48.dp)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(dayLabel, style = MaterialTheme.typography.labelSmall, color = contentColor)
        Text(
            date.dayOfMonth.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor
        )
    }
}

/** Card cu actiune rapida pentru tratamentele "la nevoie" (PRN) — fara orar, logate ad-hoc. */
@Composable
private fun AsNeededSection(treatments: List<Treatment>, onLog: (Long) -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("La nevoie", style = MaterialTheme.typography.titleSmall)
            treatments.forEach { t ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(t.medicationName, style = MaterialTheme.typography.bodyLarge)
                        Text(t.dosage, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedButton(onClick = { onLog(t.id) }) { Text("Am luat o doză") }
                }
            }
        }
    }
}

@Composable
private fun ExactAlarmBanner(onOpenSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Alarmele exacte sunt dezactivate", style = MaterialTheme.typography.titleSmall)
            Text(
                "Fără această permisiune, reminderele pot întârzia. Activeaz-o pentru notificări la timp.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
                Text("Deschide setările")
            }
        }
    }
}

@Composable
private fun DoseRow(item: DoseItem, onTake: () -> Unit, onSkip: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.medicationName, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (item.dose.isAsNeeded) "la nevoie" else item.dose.scheduledAt.toLocalTime().format(HM)
                )
            }
            Text(item.dosage, style = MaterialTheme.typography.bodySmall)
            when (item.dose.status) {
                DoseStatus.TAKEN -> Text("Luat ✓", color = DoseTaken)
                DoseStatus.MISSED -> Text("Ratat", color = DoseMissed)
                DoseStatus.SKIPPED -> Text("Omis", textDecoration = TextDecoration.LineThrough)
                DoseStatus.PENDING -> Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onTake) { Text("Confirmă") }
                    OutlinedButton(onClick = onSkip) { Text("Omite") }
                }
            }
        }
    }
}
