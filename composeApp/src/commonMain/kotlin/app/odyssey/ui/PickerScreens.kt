package app.odyssey.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.odyssey.AppModel
import app.odyssey.engine.CountryCatalog

/**
 * Picking a country.
 *
 * All 195 are listed, not just the ones the canon covers. Showing only covered
 * countries would let the app pretend the world is exactly as large as its own
 * dataset; showing the rest, greyed, makes the canon's scope visible and gives
 * the roadmap somewhere to land.
 */
@Composable
fun CountryPickerScreen(model: AppModel) {
    val covered = model.repo.countriesInCanon()
    val selected = model.snapshot.selection.country
    val all = CountryCatalog.sorted()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                "Country",
                "${covered.size} of ${CountryCatalog.total} covered by canon v${model.snapshot.result.canonVersion}.",
            )
        }

        items(all.filter { it.first in covered }) { (code, name) ->
            val places = model.snapshot.canon.active().count { it.country == code }
            PickerRow(
                title = name,
                trailing = "$places places",
                selected = code == selected,
                enabled = true,
                accent = Palette.Verified,
            ) { model.selectCountry(code) }
        }

        item { SectionTitle("Not in the canon yet", "${CountryCatalog.total - covered.size} countries") }

        items(all.filterNot { it.first in covered }) { (_, name) ->
            PickerRow(title = name, trailing = "", selected = false, enabled = false) { }
        }

        item {
            Text(
                "Countries outside the canon are in no denominator, so they cannot " +
                    "dilute a percentage. They arrive with future canon releases.",
                fontSize = 11.sp,
                color = Palette.Muted,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** Picking a state within the selected country. */
@Composable
fun RegionPickerScreen(model: AppModel) {
    val snap = model.snapshot
    val states = snap.canon.regionsIn(snap.selection.country)
    val selected = snap.selection.regionCode

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ScreenHeader(
                model.regionNoun().replaceFirstChar { it.uppercase() },
                "${states.size} of ${model.regionsInCountry()} ${model.regionNoun()} in " +
                    "${CountryCatalog.name(snap.selection.country)} are in the canon.",
            )
        }

        items(states) { code ->
            val (done, total) = snap.result.regionProgress(code)
            PickerRow(
                title = snap.canon.regionName(code),
                trailing = "$done/$total",
                selected = code == selected,
                enabled = true,
                accent = when {
                    total > 0 && done == total -> Palette.Verified
                    done > 0 -> Palette.Pending
                    else -> Palette.Muted
                },
            ) { model.selectRegionAndReturn(code) }
        }
    }
}

@Composable
private fun PickerRow(
    title: String,
    trailing: String,
    selected: Boolean,
    enabled: Boolean,
    accent: androidx.compose.ui.graphics.Color = Palette.Muted,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(if (selected) Palette.SurfaceHigh else Palette.Surface)
            .border(
                1.dp,
                if (selected) Palette.Verified else Palette.Line,
                RoundedCornerShape(11.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) Palette.Text else Palette.Muted,
            modifier = Modifier.weight(1f),
        )
        if (trailing.isNotEmpty()) {
            Text(trailing, fontSize = 12.sp, color = accent)
            Box(modifier = Modifier.width(8.dp))
        }
        if (selected) {
            Text("✓", fontSize = 14.sp, color = Palette.Verified)
        } else if (!enabled) {
            Text("—", fontSize = 12.sp, color = Palette.Muted)
        }
    }
}
