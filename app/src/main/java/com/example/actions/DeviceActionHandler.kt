package com.example.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * Executes safe Android device actions triggered by Gemini Live function calling:
 * - openWhatsApp
 * - openApp (safe allowlist and package resolution)
 * - openUrl (validated https URLs)
 * - makeCall (phone number dialing)
 * - callContact (contact search with exact/multiple/not-found resolution)
 */
class DeviceActionHandler(private val context: Context, private val onLog: (String) -> Unit) {

    companion object {
        private const val TAG = "DeviceActionHandler"

        // Curated safe app package mapping
        private val KNOWN_APPS = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "chrome" to "com.android.chrome",
            "google maps" to "com.google.android.apps.maps",
            "maps" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "spotify" to "com.spotify.music",
            "play store" to "com.android.vending",
            "calculator" to "com.google.android.calculator",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos"
        )
    }

    /**
     * Dispatch function call by name and arguments.
     * Returns JSON response string to send back in Gemini toolResponse.
     */
    fun executeAction(functionName: String, args: JSONObject): JSONObject {
        onLog("[DEVICE ACTION] Executing tool: $functionName with args: $args")
        return try {
            when (functionName) {
                "openWhatsApp" -> openWhatsApp()
                "openApp" -> {
                    val appName = args.optString("appName", "")
                    openApp(appName)
                }
                "openUrl" -> {
                    val url = args.optString("url", "")
                    openUrl(url)
                }
                "makeCall" -> {
                    val phoneNumber = args.optString("phoneNumber", "")
                    makeCall(phoneNumber)
                }
                "callContact" -> {
                    val contactName = args.optString("contactName", "")
                    callContact(contactName)
                }
                else -> {
                    JSONObject().apply {
                        put("status", "error")
                        put("message", "Unknown device action: $functionName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action $functionName", e)
            onLog("[DEVICE ACTION ERROR] $functionName failed: ${e.message}")
            JSONObject().apply {
                put("status", "error")
                put("message", "Failed to execute $functionName: ${e.message}")
            }
        }
    }

    fun openWhatsApp(): JSONObject {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage("com.whatsapp")
        return if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onLog("[ACTION] Successfully launched WhatsApp")
            JSONObject().apply {
                put("status", "success")
                put("message", "WhatsApp application opened successfully.")
            }
        } else {
            // Fallback: try opening WhatsApp web/link or report not installed
            try {
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
                onLog("[ACTION] WhatsApp app not found; opened WhatsApp web link")
                JSONObject().apply {
                    put("status", "success")
                    put("message", "WhatsApp app not installed, opened WhatsApp link in browser.")
                }
            } catch (e: Exception) {
                onLog("[ACTION] WhatsApp is not installed on this device")
                JSONObject().apply {
                    put("status", "not_installed")
                    put("message", "WhatsApp is not installed on this device.")
                }
            }
        }
    }

    fun openApp(appName: String): JSONObject {
        val cleanName = appName.trim().lowercase()
        if (cleanName.isEmpty()) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "No application name provided.")
            }
        }

        // Special system actions
        if (cleanName.contains("camera")) {
            return launchIntentSafely(Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE), "Camera")
        }
        if (cleanName.contains("setting")) {
            return launchIntentSafely(Intent(android.provider.Settings.ACTION_SETTINGS), "Settings")
        }
        if (cleanName.contains("dialer") || cleanName.contains("phone")) {
            return launchIntentSafely(Intent(Intent.ACTION_DIAL), "Phone")
        }

        val pm = context.packageManager

        // Check curated map
        val targetPackage = KNOWN_APPS[cleanName]
        if (targetPackage != null) {
            val intent = pm.getLaunchIntentForPackage(targetPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                onLog("[ACTION] Opened $appName ($targetPackage)")
                return JSONObject().apply {
                    put("status", "success")
                    put("message", "Opened $appName successfully.")
                }
            }
        }

        // Search installed apps by label
        val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in installedApps) {
            val label = pm.getApplicationLabel(app).toString().lowercase()
            if (label == cleanName || label.contains(cleanName)) {
                val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    onLog("[ACTION] Opened installed app: $label (${app.packageName})")
                    return JSONObject().apply {
                        put("status", "success")
                        put("message", "Opened $label successfully.")
                    }
                }
            }
        }

        onLog("[ACTION] App '$appName' is not installed or cannot be opened")
        return JSONObject().apply {
            put("status", "not_found")
            put("message", "Application '$appName' is not installed on this device.")
        }
    }

    fun openUrl(url: String): JSONObject {
        var cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "URL cannot be empty.")
            }
        }

        if (!cleanUrl.startsWith("http://", ignoreCase = true) &&
            !cleanUrl.startsWith("https://", ignoreCase = true)) {
            cleanUrl = "https://$cleanUrl"
        }

        return try {
            val uri = Uri.parse(cleanUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onLog("[ACTION] Opened URL: $cleanUrl")
            JSONObject().apply {
                put("status", "success")
                put("message", "Opened $cleanUrl in browser.")
            }
        } catch (e: Exception) {
            onLog("[ACTION ERROR] Failed to open URL: ${e.message}")
            JSONObject().apply {
                put("status", "error")
                put("message", "Could not open $cleanUrl: ${e.message}")
            }
        }
    }

    fun makeCall(phoneNumber: String): JSONObject {
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (cleanNumber.isEmpty()) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "Invalid phone number provided.")
            }
        }

        // Open dialer with number pre-filled (safest and works reliably)
        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            onLog("[ACTION] Opened dialer with number: $cleanNumber")
            JSONObject().apply {
                put("status", "success")
                put("message", "Dialer opened ready to call $cleanNumber.")
            }
        } catch (e: Exception) {
            onLog("[ACTION ERROR] Failed to dial number: ${e.message}")
            JSONObject().apply {
                put("status", "error")
                put("message", "Could not initiate call to $cleanNumber: ${e.message}")
            }
        }
    }

    data class ContactMatch(val name: String, val number: String)

    fun callContact(contactName: String): JSONObject {
        val query = contactName.trim()
        if (query.isEmpty()) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "Contact name cannot be empty.")
            }
        }

        // Check permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            onLog("[ACTION WARNING] READ_CONTACTS permission not granted")
            return JSONObject().apply {
                put("status", "permission_needed")
                put("message", "Contacts permission is required to search contacts. Please grant permission in the app.")
            }
        }

        val matches = mutableListOf<ContactMatch>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val selectionArgs = arrayOf("%$query%")

            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && matches.size < 10) {
                    val name = cursor.getString(nameIndex) ?: ""
                    val number = cursor.getString(numberIndex) ?: ""
                    if (name.isNotEmpty() && number.isNotEmpty()) {
                        matches.add(ContactMatch(name, number))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying contacts", e)
            return JSONObject().apply {
                put("status", "error")
                put("message", "Failed to query contacts: ${e.message}")
            }
        }

        return when {
            matches.isEmpty() -> {
                onLog("[ACTION] No contacts found matching '$query'")
                JSONObject().apply {
                    put("status", "not_found")
                    put("message", "Could not find any contact matching '$query'.")
                }
            }
            matches.size == 1 -> {
                val match = matches.first()
                onLog("[ACTION] Exact contact match found: ${match.name} (${match.number})")
                makeCall(match.number).apply {
                    put("contactName", match.name)
                    put("message", "Calling ${match.name} at ${match.number}.")
                }
            }
            else -> {
                val names = matches.joinToString(", ") { "${it.name} (${it.number})" }
                onLog("[ACTION] Multiple contacts found: $names")
                JSONObject().apply {
                    put("status", "multiple_found")
                    put("message", "Found ${matches.size} contacts matching '$query': $names. Please ask the user which one they would like to call.")
                }
            }
        }
    }

    private fun launchIntentSafely(intent: Intent, appLabel: String): JSONObject {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            onLog("[ACTION] Successfully launched $appLabel")
            JSONObject().apply {
                put("status", "success")
                put("message", "Opened $appLabel successfully.")
            }
        } catch (e: Exception) {
            onLog("[ACTION ERROR] Could not launch $appLabel: ${e.message}")
            JSONObject().apply {
                put("status", "error")
                put("message", "Could not open $appLabel: ${e.message}")
            }
        }
    }
}
