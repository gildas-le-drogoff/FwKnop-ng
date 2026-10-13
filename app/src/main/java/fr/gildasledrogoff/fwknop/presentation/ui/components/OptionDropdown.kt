package fr.gildasledrogoff.fwknop.presentation.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import fr.gildasledrogoff.fwknop.domain.model.Protocol
import fr.gildasledrogoff.fwknop.presentation.ui.theme.FwknopTheme

@Immutable
data class DropdownOptions<T>(
    val label: String,
    val options: List<T>,
    val optionLabel: (T) -> String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> OptionDropdown(
    spec: DropdownOptions<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = it && enabled },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = spec.optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(spec.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }) {
            spec.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(spec.optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(
                    expanded,
                    Modifier.menuAnchor(ExposedDropdownMenuAnchorType.SecondaryEditable),
                )
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            suggestions.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

@AdaptivePreviews
@Composable
private fun OptionDropdownPreview() {
    FwknopTheme {
        OptionDropdown(
            DropdownOptions("Protocol", Protocol.entries, Protocol::name),
            Protocol.UDP,
            {})
    }
}
