package com.voltic.app.ui.components

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voltic.app.R
import kotlinx.coroutines.launch

/**
 * Reusable Copy Button component.
 * Automatically copies `textToCopy` to clipboard and displays a Toast notification.
 */
@Composable
fun CopyButton(
    textToCopy: String,
    modifier: Modifier = Modifier,
    toastMessage: String = "Copied to clipboard!",
    tint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 18.dp
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    IconButton(
        onClick = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Copied Text", textToCopy)))
                Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
            }
        },
        modifier = modifier
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_copy),
            contentDescription = "Copy",
            tint = tint,
            modifier = Modifier.size(iconSize)
        )
    }
}