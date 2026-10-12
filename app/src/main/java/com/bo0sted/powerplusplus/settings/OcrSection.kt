package com.bo0sted.powerplusplus.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bo0sted.powerplusplus.R
import com.bo0sted.powerplusplus.download.ComponentState
import com.bo0sted.powerplusplus.ocr.OcrCatalog
import com.bo0sted.powerplusplus.ocr.OcrComponent

/** Download, check and remove the OCR engine and models. Nothing OCR-related ships in the APK. */
@Composable
internal fun OcrSection(
    states: Map<String, ComponentState>,
    onInstall: (OcrComponent) -> Unit,
    onRemove: (OcrComponent) -> Unit,
) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.ocr_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.ocr_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OcrCatalog.ALL.forEach { component ->
                val engine = component.kind == OcrComponent.Kind.Engine
                ComponentRow(
                    name = if (engine) stringResource(R.string.ocr_engine_name, component.version) else component.version,
                    source = stringResource(if (engine) R.string.ocr_engine_source else R.string.ocr_model_source),
                    downloadBytes = component.downloadBytes,
                    state = states[component.id] ?: ComponentState.NotInstalled,
                    onInstall = { onInstall(component) },
                    onRemove = { onRemove(component) },
                )
            }
        }
    }
}
