package com.example.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

object ToolExecutionEngine {
    private const val TAG = "ToolExecutionEngine"

    fun openApp(context: Context, packageName: String?, appName: String?): String {
        Log.d(TAG, "openApp called with pkg: $packageName, appName: $appName")
        val pm = context.packageManager
        
        // 1. Try directly with provided package name if valid
        if (!packageName.isNull_or_blank()) {
            val launchIntent = packageName?.let { pm.getLaunchIntentForPackage(it) }
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                return "Successfully launched $packageName"
            }
        }

        // 2. Map common app names to known packages
        val nameQuery = (appName ?: packageName ?: "").lowercase().trim()
        val commonApps = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "instagram" to "com.instagram.android",
            "calculator" to "com.google.android.calculator",
            "camera" to "com.android.camera",
            "chrome" to "com.android.chrome",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "settings" to "com.android.settings",
            "spotify" to "com.spotify.music",
            "facebook" to "com.facebook.katana"
        )

        for ((key, pkg) in commonApps) {
            if (nameQuery.contains(key)) {
                val intent = pm.getLaunchIntentForPackage(pkg)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    return "Opened ${key.replaceFirstChar { it.uppercase() }}!"
                }
            }
        }

        // 3. Search installed applications by label
        try {
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (app in installedApps) {
                val label = pm.getApplicationLabel(app).toString().lowercase()
                if (label.contains(nameQuery) || nameQuery.contains(label)) {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        return "Opened ${pm.getApplicationLabel(app)}!"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error listing packages", e)
        }

        return "Sorry babe, couldn't find an app matching '$nameQuery' installed on your phone."
    }

    fun searchAndCallContact(context: Context, contactName: String): String {
        Log.d(TAG, "searchAndCallContact called for: $contactName")
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "PERMISSION_REQUIRED: READ_CONTACTS. Tell user gracefully to grant contact permissions."
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return "PERMISSION_REQUIRED: CALL_PHONE. Tell user gracefully to grant call permissions."
        }

        val phoneNumber = queryPhoneNumber(context, contactName)
            ?: return "Couldn't find any phone number for '$contactName' in your contacts list, gorgeous."

        return try {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(callIntent)
            "Calling $contactName at $phoneNumber now!"
        } catch (e: Exception) {
            // Fallback to dialer
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(dialIntent)
            "Opened dialer with $phoneNumber for $contactName!"
        }
    }

    fun sendWhatsAppMessage(context: Context, contactName: String, message: String): String {
        Log.d(TAG, "sendWhatsAppMessage called for $contactName with msg: $message")
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return "PERMISSION_REQUIRED: READ_CONTACTS. Tell user gracefully to grant contact permission."
        }

        val rawPhone = queryPhoneNumber(context, contactName)
        val cleanPhone = rawPhone?.replace(Regex("[^0-9]"), "")

        return try {
            if (!cleanPhone.isNull_or_empty()) {
                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened WhatsApp chat with $contactName and filled in your message!"
            } else {
                // Fallback: send text via generic intent targeting WhatsApp
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    setPackage("com.whatsapp")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened WhatsApp with your message pre-filled!"
            }
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp not installed or error", e)
            "Looks like WhatsApp isn't installed or couldn't open deep link for $contactName."
        }
    }

    fun sendGmail(context: Context, recipientEmail: String, subject: String, body: String): String {
        Log.d(TAG, "sendGmail called for $recipientEmail")
        return try {
            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$recipientEmail")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(emailIntent)
            "Drafted email to $recipientEmail in your mail app!"
        } catch (e: Exception) {
            Log.e(TAG, "Error opening mail intent", e)
            "Couldn't launch mail app for $recipientEmail."
        }
    }

    private fun queryPhoneNumber(context: Context, name: String): String? {
        val cr = context.contentResolver
        val cursor = cr.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"),
            null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val phoneIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (phoneIdx != -1) {
                    return it.getString(phoneIdx)
                }
            }
        }
        return null
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()
    private fun String?.isNull_or_empty(): Boolean = this == null || this.isEmpty()
}
