package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.live.GeminiLiveClient

/**
 * Settings dialog for configuring Gemini API key and selecting the Live model.
 */
@Composable
fun SettingsDialog(
    currentApiKey: String,
    currentModel: String,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var apiKeyText by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var selectedModel by remember(currentModel) { mutableStateOf(currentModel) }
    var isKeyVisible by remember { mutableStateOf(false) }

    val models = listOf(
        GeminiLiveClient.MODEL_LIVE to "Gemini 2.0 Flash Live (Recommended)",
        GeminiLiveClient.MODEL_LIVE_FALLBACK to "Gemini 3.1 Flash Live"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF19172B),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Arushi Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF00E5FF)
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Gemini API Key",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = "Saved locally on this device.",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = apiKeyText,
                    onValueChange = { apiKeyText = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("AIzaSy...", color = Color.DarkGray) },
                    leadingIcon = {
                        Icon(Icons.Default.Key, contentDescription = null, tint = Color(0xFF00E5FF))
                    },
                    trailingIcon = {
                        IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                            Icon(
                                imageVector = if (isKeyVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (isKeyVisible) "Hide key" else "Show key",
                                tint = Color.Gray
                            )
                        }
                    },
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF3B375E),
                        cursorColor = Color(0xFF00E5FF)
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Live Model",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                models.forEach { (modelId, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedModel == modelId),
                            onClick = { selectedModel = modelId },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Color(0xFF00E5FF),
                                unselectedColor = Color.Gray
                            )
                        )
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (selectedModel == modelId) Color.White else Color.Gray,
                                fontWeight = if (selectedModel == modelId) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(apiKeyText.trim(), selectedModel)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color.Black
                )
            ) {
                Text("Save", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}
