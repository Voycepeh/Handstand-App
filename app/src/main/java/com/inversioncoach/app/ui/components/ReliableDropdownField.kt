package com.inversioncoach.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.menuAnchor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

data class DropdownOption<T>(
    val value: T,
    val label: String,
)

@Composable
fun <T> ReliableDropdownField(
    label: String,
    selected: DropdownOption<T>,
    options: List<DropdownOption<T>>,
    onOptionSelected: (DropdownOption<T>) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { shouldExpand ->
            if (enabled) expanded = shouldExpand
        },
        modifier = modifier.zIndex(2f),
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = { },
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 280.dp)
                .zIndex(3f),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
fun <T> MultiSelectChipsField(
    label: String,
    options: List<DropdownOption<T>>,
    selectedValues: Set<T>,
    onToggle: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                ElevatedFilterChip(
                    selected = option.value in selectedValues,
                    onClick = { onToggle(option.value) },
                    label = { Text(option.label) },
                )
            }
        }
    }
}
