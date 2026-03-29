package androidx.compose.material3

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compatibility shim for Material3 versions where [ExposedDropdownMenu] is unavailable.
 */
@Composable
fun ExposedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        content = content,
    )
}

/**
 * Compatibility shim for Material3 versions where [menuAnchor] is unavailable.
 */
fun Modifier.menuAnchor(): Modifier = this
