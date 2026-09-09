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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.DisposableEffect
import com.pillpronto.R
import com.pillpronto.core.permissions.Permissions
import com.pillpronto.core.ui.components.DatePickerDialogBox
import com.pillpronto.core.ui.theme.DoseMissed
import com.pillpronto.core.ui.theme.DoseTaken
import com.pillpronto.domain.model.DoseItem
import com.pillpronto.domain.model.DoseStatus
import com.pillpronto.domain.model.Treatment
import com.pillpronto.domain.usecase.isDoseActionable
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HM = DateTimeFormatter.ofPattern("HH:mm")
private val RO_LOCALE = Locale("ro")
private val FULL_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RO_LOCALE)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("LLLL yyyy", RO_LOCALE)

@Composable
fun TodayScreen(padding: PaddingValues, vm: TodayViewModel = hiltViewModel()) {
    val doses by vm.doses.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val asNeededTreatments by vm.asNeededTreatments.collectAsStateWithLifecycle()
    val today = remember { LocalDate.now() }
    val context = LocalContext.current
    var showMonthPicker by remember { mutableStateOf(false) }

    // Reevalueaza statusul permisiunii de alarme exacte cand revenim din setari.
    var canExact by remember { mutableStateOf(Permissions.canScheduleExactAlarms(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canExact = Permissions.canScheduleExactAlarms(context)
                // O doza depasita de fereastra de actiune (isDoseActionable) trece in MISSED
                // prompt la revenirea din fundal, nu doar la cele 6h ale workerului periodic.
                vm.refreshOverdue()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // Prima intrare pe ecran (compozitie noua, ex. dupa navigare intre tab-uri) — DisposableEffect
    // de mai sus nu se declanseaza la asta, doar la un ON_RESUME real ulterior.
    LaunchedEffect(Unit) { vm.refreshOverdue() }

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
                TextButton(onClick = { vm.onDateSelected(today) }) { Text(stringResource(R.string.nav_today)) }
            }
        }

        MonthYearHeader(
            selectedDate = selectedDate,
            onPrevious = vm::onPreviousMonth,
            onNext = vm::onNextMonth,
            onOpenPicker = { showMonthPicker = true },
            modifier = Modifier.padding(top = 8.dp)
        )

        DateStrip(
            today = today,
            selectedDate = selectedDate,
            onDateSelected = vm::onDateSelected,
            modifier = Modifier.padding(top = 4.dp)
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

    if (showMonthPicker) {
        DatePickerDialogBox(
            initial = selectedDate,
            onConfirm = { vm.onDateSelected(it); showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }
}

@Composable
private fun titleFor(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.today_title_today)
    today.minusDays(1) -> stringResource(R.string.today_title_yesterday)
    today.plusDays(1) -> stringResource(R.string.today_title_tomorrow)
    else -> stringResource(R.string.today_title_other_day, date.format(FULL_DATE))
}

@Composable
private fun emptyMessageFor(date: LocalDate, today: LocalDate): String =
    if (date == today) stringResource(R.string.today_empty_today)
    else stringResource(R.string.today_empty_other_day)

/** Randul cu luna/anul afisate — sageti pentru ajustare fina, eticheta deschide picker-ul
 * nativ (an + navigare pe luni) pentru salt direct la o data indepartata. */
@Composable
private fun MonthYearHeader(
    selectedDate: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = selectedDate.format(MONTH_YEAR).replaceFirstChar { it.uppercase() }
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = stringResource(R.string.today_previous_month))
        }
        Row(
            Modifier.clickable(onClick = onOpenPicker),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = stringResource(R.string.today_open_month_picker),
                modifier = Modifier.size(20.dp)
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = stringResource(R.string.today_next_month))
        }
    }
}

/** Stripul de zile al lunii afisate (derivata din selectedDate) — selectie orizontala. */
@Composable
private fun DateStrip(
    today: LocalDate,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val yearMonth = YearMonth.from(selectedDate)
    val dates = remember(yearMonth) {
        (1..yearMonth.lengthOfMonth()).map { yearMonth.atDay(it) }
    }
    val listState = rememberLazyListState()

    // Cand selectia se schimba (tap pe chip, sageata de luna, picker sau butonul "Azi"),
    // readuce ziua selectata in vizor — inclusiv dupa ce stripul s-a regenerat pentru alta luna.
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
            Text(stringResource(R.string.today_as_needed_section_title), style = MaterialTheme.typography.titleSmall)
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
                    OutlinedButton(onClick = { onLog(t.id) }) { Text(stringResource(R.string.common_log_dose)) }
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
            Text(stringResource(R.string.today_exact_alarm_banner_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.today_exact_alarm_banner_text),
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = onOpenSettings, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.today_open_settings))
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
                    if (item.dose.isAsNeeded) stringResource(R.string.common_as_needed)
                    else item.dose.scheduledAt.toLocalTime().format(HM)
                )
            }
            Text(item.dosage, style = MaterialTheme.typography.bodySmall)
            // Cantitatea specifica a acestei doze (Faza 2a) — poate diferi de la o ora la alta
            // pentru acelasi tratament (ex. "Nolpaza dimineata 1 compr., seara 2 compr.").
            if (item.dose.cantitate.isNotBlank()) {
                Text(item.dose.cantitate, style = MaterialTheme.typography.bodySmall)
            }
            when (item.dose.status) {
                DoseStatus.TAKEN -> Text(stringResource(R.string.dose_status_taken), color = DoseTaken)
                DoseStatus.MISSED -> Text(stringResource(R.string.dose_status_missed), color = DoseMissed)
                DoseStatus.SKIPPED -> Text(stringResource(R.string.dose_status_skipped), textDecoration = TextDecoration.LineThrough)
                // Butoanele apar doar in fereastra de +-60 min din jurul orei programate — o doza
                // programata peste cateva ore (normal, inca nu e cazul ei) sau depasita (va trece
                // in MISSED la refreshOverdue()) nu mai arata butoane. Doze "la nevoie" (isAsNeeded)
                // nu ajung niciodata aici cu status PENDING (logate direct din AsNeededSection).
                DoseStatus.PENDING -> if (isDoseActionable(item.dose.scheduledAt)) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onTake) { Text(stringResource(R.string.common_confirm_take)) }
                        OutlinedButton(onClick = onSkip) { Text(stringResource(R.string.common_skip)) }
                    }
                }
            }
        }
    }
}
