package com.example.v0idsound

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: IndexerSettings,
    onSetMinimumDuration: (Int) -> Unit,
    onSetOnlyMusic: (Boolean) -> Unit,
    onSetHideRecordings: (Boolean) -> Unit,
    onSetSortByName: (Boolean) -> Unit,
    onSetRescanOnStartup: (Boolean) -> Unit,
    onRescan: () -> Unit,
    onSetBroadScan: (Boolean) -> Unit,
    onAddScanFolder: (Uri) -> Unit,
    onRemoveScanFolder: (String) -> Unit,
    onSetMaxFolderDepth: (Int) -> Unit,
    onSetUseMediaStore: (Boolean) -> Unit,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAccentColor: (AccentColor) -> Unit,
    onSetAccentArgb: (Int) -> Unit,
    onSetMarqueeLongTitles: (Boolean) -> Unit
) {
    var selectedCategory by remember {
        mutableStateOf(SettingsCategory.INDEXING)
    }

    var durationInput by remember {
        mutableStateOf(settings.minimumDurationSeconds.toString())
    }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let(onAddScanFolder)
    }

    LaunchedEffect(settings.minimumDurationSeconds) {
        durationInput = settings.minimumDurationSeconds.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == SettingsCategory.INDEXING,
                onClick = {
                    selectedCategory = SettingsCategory.INDEXING
                },
                label = {
                    Text("Indexing")
                }
            )

            FilterChip(
                selected = selectedCategory == SettingsCategory.CUSTOMIZATION,
                onClick = {
                    selectedCategory = SettingsCategory.CUSTOMIZATION
                },
                label = {
                    Text("Customization")
                }
            )
        }

        when (selectedCategory) {
            SettingsCategory.INDEXING -> {
                IndexingSettings(
                    settings = settings,
                    durationInput = durationInput,
                    onDurationInputChange = { newValue ->
                        val digitsOnly = newValue.filter { it.isDigit() }

                        durationInput = digitsOnly

                        digitsOnly.toIntOrNull()?.let { seconds ->
                            if (seconds in 0..3600) {
                                onSetMinimumDuration(seconds)
                            }
                        }
                    },
                    pickFolder = {
                        pickFolder.launch(null)
                    },
                    onSetOnlyMusic = onSetOnlyMusic,
                    onSetHideRecordings = onSetHideRecordings,
                    onSetBroadScan = onSetBroadScan,
                    onRemoveScanFolder = onRemoveScanFolder,
                    onSetMaxFolderDepth = onSetMaxFolderDepth,
                    onSetUseMediaStore = onSetUseMediaStore,
                    onSetSortByName = onSetSortByName,
                    onSetRescanOnStartup = onSetRescanOnStartup,
                    onRescan = onRescan
                )
            }

            SettingsCategory.CUSTOMIZATION -> {
                CustomizationSettings(
                    settings = settings,
                    onSetDarkTheme = onSetDarkTheme,
                    onSetAccentColor = onSetAccentColor,
                    onSetAccentArgb = onSetAccentArgb,
                    onSetMarqueeLongTitles = onSetMarqueeLongTitles
                )
            }
        }
    }
}

@Composable
private fun IndexingSettings(
    settings: IndexerSettings,
    durationInput: String,
    onDurationInputChange: (String) -> Unit,
    pickFolder: () -> Unit,
    onSetOnlyMusic: (Boolean) -> Unit,
    onSetHideRecordings: (Boolean) -> Unit,
    onSetBroadScan: (Boolean) -> Unit,
    onRemoveScanFolder: (String) -> Unit,
    onSetMaxFolderDepth: (Int) -> Unit,
    onSetUseMediaStore: (Boolean) -> Unit,
    onSetSortByName: (Boolean) -> Unit,
    onSetRescanOnStartup: (Boolean) -> Unit,
    onRescan: () -> Unit
) {
    Text(
        text = "Indexing",
        style = MaterialTheme.typography.titleLarge
    )

    Text("Minimum track duration")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = durationInput,
            onValueChange = onDurationInputChange,
            label = {
                Text("Seconds")
            },
            modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number
            )
        )

        Text("seconds")
    }

    HorizontalDivider()

    SettingToggle(
        label = "Only show music",
        description = "Uses Android’s music flag instead of showing every audio file.",
        checked = settings.onlyMusic,
        onChange = onSetOnlyMusic
    )

    SettingToggle(
        label = "Hide recordings",
        description = "Hides voice recordings and similar audio files.",
        checked = settings.hideRecordings,
        onChange = onSetHideRecordings
    )

    SettingToggle(
        label = "Broad scan",
        description = "Include unusual audio formats and files with missing MIME metadata.",
        checked = settings.broadScan,
        onChange = onSetBroadScan
    )

    HorizontalDivider()

    Text(
        text = "Custom folders",
        style = MaterialTheme.typography.titleLarge
    )

    Button(
        onClick = pickFolder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Add folder to scan")
    }

    if (settings.scanFolders.isEmpty()) {
        Text("No custom folders selected.")
    } else {
        settings.scanFolders.forEach { folderUri ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = folderUri.substringAfterLast("%3A"),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = {
                        onRemoveScanFolder(folderUri)
                    }
                ) {
                    Text("Remove")
                }
            }
        }
    }

    Text("Maximum folder depth: ${settings.maxFolderDepth}")

    Slider(
        value = settings.maxFolderDepth.toFloat(),
        onValueChange = { value ->
            onSetMaxFolderDepth(value.toInt())
        },
        valueRange = 1f..15f,
        steps = 13
    )

    SettingToggle(
        label = "Use Android music library",
        description = "Also include audio already indexed by MediaStore.",
        checked = settings.useMediaStore,
        onChange = onSetUseMediaStore
    )

    HorizontalDivider()

    SettingToggle(
        label = "Sort by title",
        description = "Off sorts tracks by the order Android returns them.",
        checked = settings.sortByName,
        onChange = onSetSortByName
    )

    SettingToggle(
        label = "Rescan library on startup",
        description = "Reloads the track list every time v0idsound opens.",
        checked = settings.rescanOnStartup,
        onChange = onSetRescanOnStartup
    )

    HorizontalDivider()

    Button(
        onClick = onRescan,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Rescan library now")
    }
}

@Composable
private fun CustomizationSettings(
    settings: IndexerSettings,
    onSetDarkTheme: (Boolean) -> Unit,
    onSetAccentColor: (AccentColor) -> Unit,
    onSetAccentArgb: (Int) -> Unit,
    onSetMarqueeLongTitles: (Boolean) -> Unit
) {
    var hexInput by remember {
        mutableStateOf(settings.accent.toHexColor())
    }

    var isPickerOpen by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(settings.accentArgb) {
        hexInput = settings.accent.toHexColor()
    }

    Text(
        text = "Customization",
        style = MaterialTheme.typography.titleLarge
    )

    SettingToggle(
        label = "Dark theme",
        description = "Use v0idsound’s dark color scheme.",
        checked = settings.useDarkTheme,
        onChange = onSetDarkTheme
    )

    SettingToggle(
        label = "Scroll long song titles",
        description = "Move long titles sideways in the player so the full title can be read.",
        checked = settings.marqueeLongTitles,
        onChange = onSetMarqueeLongTitles
    )

    HorizontalDivider()

    Text(
        text = "Accent color",
        style = MaterialTheme.typography.titleLarge
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(
                    color = settings.accent,
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(20.dp)
                )
        )

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Selected color",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = settings.accent.toHexColor(),
                style = MaterialTheme.typography.titleMedium
            )
        }
    }

    Button(
        onClick = {
            isPickerOpen = true
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Open color picker")
    }

    Text(
        text = "Quick colors",
        style = MaterialTheme.typography.titleSmall
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AccentColor.entries.forEach { accent ->
            val isSelected =
                settings.accentArgb == accent.color.toArgb()

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = accent.color,
                        shape = CircleShape
                    )
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape
                    )
                    .clickable {
                        onSetAccentColor(accent)
                    }
            )
        }
    }

    OutlinedTextField(
        value = hexInput,
        onValueChange = { newValue ->
            val normalized = newValue
                .uppercase()
                .filter { character ->
                    character == '#' ||
                            character.isDigit() ||
                            character in 'A'..'F'
                }
                .take(9)

            hexInput = normalized

            normalized.toArgbOrNull()?.let { argb ->
                onSetAccentArgb(argb)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text("Custom hex color")
        },
        supportingText = {
            Text("Use #RRGGBB or #AARRGGBB")
        },
        singleLine = true,
        isError = hexInput.isNotBlank() &&
                hexInput.toArgbOrNull() == null
    )

    if (isPickerOpen) {
        AccentColorPickerDialog(
            initialColor = settings.accent,
            onDismiss = {
                isPickerOpen = false
            },
            onApply = { selectedColor ->
                onSetAccentArgb(selectedColor.toArgb())
                isPickerOpen = false
            }
        )
    }
}

@Composable
private fun AccentColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onApply: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { hsv ->
            AndroidColor.colorToHSV(
                initialColor.toArgb(),
                hsv
            )
        }
    }

    var hue by remember(initialColor) {
        mutableFloatStateOf(initialHsv[0])
    }

    var saturation by remember(initialColor) {
        mutableFloatStateOf(initialHsv[1])
    }

    var value by remember(initialColor) {
        mutableFloatStateOf(initialHsv[2])
    }

    val selectedColor = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(
                hue,
                saturation,
                value
            )
        )
    )

    val pureHue = Color(
        AndroidColor.HSVToColor(
            floatArrayOf(
                hue,
                1f,
                1f
            )
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Choose accent color")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(
                                color = selectedColor,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(14.dp)
                            )
                    )

                    Column {
                        Text(
                            text = "Preview",
                            style = MaterialTheme.typography.bodySmall
                        )

                        Text(
                            text = selectedColor.toHexColor(),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }

                Text(
                    text = "Color and brightness",
                    style = MaterialTheme.typography.bodyMedium
                )

                SaturationValuePicker(
                    hueColor = pureHue,
                    saturation = saturation,
                    value = value,
                    onChange = { newSaturation, newValue ->
                        saturation = newSaturation
                        value = newValue
                    }
                )

                Text(
                    text = "Hue",
                    style = MaterialTheme.typography.bodyMedium
                )

                HuePicker(
                    hue = hue,
                    onHueChange = { newHue ->
                        hue = newHue
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApply(selectedColor)
                }
            ) {
                Text("Apply")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun SaturationValuePicker(
    hueColor: Color,
    saturation: Float,
    value: Float,
    onChange: (Float, Float) -> Unit
) {
    fun updateFromPosition(
        position: Offset,
        width: Float,
        height: Float
    ) {
        if (width <= 0f || height <= 0f) {
            return
        }

        val newSaturation = (position.x / width)
            .coerceIn(0f, 1f)

        val newValue = (1f - (position.y / height))
            .coerceIn(0f, 1f)

        onChange(
            newSaturation,
            newValue
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .drawBehind {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.White,
                            hueColor
                        )
                    )
                )

                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black
                        )
                    )
                )

                val markerX = saturation * size.width
                val markerY = (1f - value) * size.height

                drawCircle(
                    color = Color.White,
                    radius = 12.dp.toPx(),
                    center = Offset(
                        markerX,
                        markerY
                    ),
                    style = Stroke(
                        width = 3.dp.toPx()
                    )
                )

                drawCircle(
                    color = Color.Black,
                    radius = 15.dp.toPx(),
                    center = Offset(
                        markerX,
                        markerY
                    ),
                    style = Stroke(
                        width = 1.dp.toPx()
                    )
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateFromPosition(
                        position = offset,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    updateFromPosition(
                        position = change.position,
                        width = size.width.toFloat(),
                        height = size.height.toFloat()
                    )
                }
            }
    )
}

@Composable
private fun HuePicker(
    hue: Float,
    onHueChange: (Float) -> Unit
) {
    fun updateHue(
        position: Offset,
        width: Float
    ) {
        if (width <= 0f) {
            return
        }

        onHueChange(
            ((position.x / width) * 360f)
                .coerceIn(0f, 360f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .drawBehind {
                val hueColors = listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                )

                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = hueColors
                    )
                )

                val markerX = (hue / 360f) * size.width

                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx(),
                    center = Offset(
                        markerX,
                        size.height / 2f
                    ),
                    style = Stroke(
                        width = 3.dp.toPx()
                    )
                )

                drawCircle(
                    color = Color.Black,
                    radius = 13.dp.toPx(),
                    center = Offset(
                        markerX,
                        size.height / 2f
                    ),
                    style = Stroke(
                        width = 1.dp.toPx()
                    )
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    updateHue(
                        position = offset,
                        width = size.width.toFloat()
                    )
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    updateHue(
                        position = change.position,
                        width = size.width.toFloat()
                    )
                }
            }
    )
}

@Composable
private fun SettingToggle(
    label: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(label)

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onChange
        )
    }
}

private fun Color.toHexColor(): String {
    return String.format(
        "#%06X",
        toArgb() and 0x00FFFFFF
    )
}

private fun String.toArgbOrNull(): Int? {
    val text = trim()

    val normalized = when {
        text.matches(Regex("^#[0-9A-Fa-f]{6}$")) -> text
        text.matches(Regex("^#[0-9A-Fa-f]{8}$")) -> text
        else -> return null
    }

    return runCatching {
        AndroidColor.parseColor(normalized)
    }.getOrNull()
}

enum class SettingsCategory {
    INDEXING,
    CUSTOMIZATION
}