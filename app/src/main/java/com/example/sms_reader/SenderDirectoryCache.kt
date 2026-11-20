package com.example.sms_reader

import android.content.Context
import com.example.domain.MobileMoneyNetwork
import com.example.domain.NetworkFormatConfig
import org.json.JSONObject

/**
 * Lightweight synchronous cache of configured network sender IDs so SmsBroadcastReceiver
 * can match user-defined custom shortcodes on onReceive() without blocking on SQLCipher I/O.
 */
object SenderDirectoryCache {

    private const val PREFS_NAME = "mauzo_sender_cache"
    private const val KEY_SENDERS_JSON = "custom_senders_json"

    fun save(context: Context, formats: List<NetworkFormatConfig>) {
        val json = JSONObject()
        formats.forEach { fmt ->
            if (fmt.network != MobileMoneyNetwork.UNKNOWN) {
                json.put(fmt.network.code, fmt.senderIdentifiersCsv)
            }
        }
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SENDERS_JSON, json.toString())
            .apply()
    }

    fun load(context: Context): Map<MobileMoneyNetwork, Set<String>> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SENDERS_JSON, null)
            ?: return emptyMap()

        return runCatching {
            val json = JSONObject(raw)
            val result = mutableMapOf<MobileMoneyNetwork, Set<String>>()
            json.keys().forEach { key ->
                val net = MobileMoneyNetwork.fromCode(key)
                if (net != MobileMoneyNetwork.UNKNOWN) {
                    val csv = json.optString(key, "")
                    val ids = csv.split(",")
                        .map { it.trim().uppercase() }
                        .filter { it.isNotEmpty() }
                        .toSet()
                    if (ids.isNotEmpty()) {
                        result[net] = ids
                    }
                }
            }
            result
        }.getOrDefault(emptyMap())
    }
}
