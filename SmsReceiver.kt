package mz.emola.gestor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale

object Transfers {
    // O texto real não inclui necessariamente a marca e-Mola.
    // Por segurança, exige remetente identificável e formato de transferência conhecido.
    private val transfer = Regex("(?i)\\b(Transferiste|Recebeste)\\s+([0-9][0-9.,]*)\\s*MT\\b")
    private val transactionId = Regex("(?i)\\bID\\s+da\\s+transa[cç][aã]o\\s+([A-Za-z0-9.]+)")
    private val destination = Regex("(?i)\\bpara\\s+conta\\s+[0-9]{7,12}\\b")
    fun parse(sender: String, body: String): Pair<String, Double>? {
        val recognizedSender = sender.contains("e-mola", true) || sender.contains("emola", true)
        if (!recognizedSender) return null
        if (!transactionId.containsMatchIn(body)) return null
        val match = transfer.find(body) ?: return null
        val direction = if (match.groupValues[1].equals("Transferiste", true)) "Enviado" else "Recebido"
        if (direction == "Enviado" && !destination.containsMatchIn(body)) return null
        val raw = match.groupValues[2]
        val normalized = when {
            raw.contains(',') && raw.contains('.') -> if (raw.lastIndexOf('.') > raw.lastIndexOf(',')) raw.replace(",", "") else raw.replace(".", "").replace(',', '.')
            raw.contains(',') -> if (raw.substringAfterLast(',').length == 3) raw.replace(",", "") else raw.replace(',', '.')
            else -> raw
        }
        val amount = normalized.toDoubleOrNull() ?: return null
        return if (amount > 0 && amount.isFinite()) direction to amount else null
    }
    private val smsDate = Regex("(?i)\\b(?:de|em)\\s+(\\d{1,2}/\\d{1,2}/\\d{4})\\b")
    private val smsTime = Regex("(?i)\\b(?:as|às)\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?)\\b")
    private fun transactionTime(body: String, fallback: Long): Long {
        val date = smsDate.find(body)?.groupValues?.get(1) ?: return fallback
        val time = smsTime.find(body)?.groupValues?.get(1) ?: return fallback
        val pattern = if (time.count { it == ':' } == 2) "dd/MM/yyyy HH:mm:ss" else "dd/MM/yyyy HH:mm"
        return runCatching { SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }.parse("$date $time")?.time ?: fallback }.getOrDefault(fallback)
    }
    fun store(context: Context, sender: String, body: String, timestamp: Long) {
        val parsed = parse(sender, body) ?: return
        val digest = MessageDigest.getInstance("SHA-256").digest("${transactionId.find(body)?.groupValues?.get(1) ?: "$sender|$body|$timestamp"}".toByteArray()).joinToString("") { "%02x".format(it) }
        val prefs = context.getSharedPreferences("transfers", Context.MODE_PRIVATE)
        synchronized(this) {
            val records = runCatching { JSONArray(prefs.getString("records", "[]")) }.getOrDefault(JSONArray())
            for (i in 0 until records.length()) if (records.getJSONObject(i).optString("id") == digest) return
            val obj = JSONObject().put("id", digest).put("type", parsed.first).put("amount", parsed.second).put("time", transactionTime(body, timestamp))
            records.put(obj)
            prefs.edit().putString("records", records.toString()).apply()
        }
    }
}

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isEmpty()) return
        val grouped = messages.groupBy { (it.originatingAddress ?: "") to (it.timestampMillis / 60000L) }
        for ((key, parts) in grouped) {
            val body = parts.joinToString("") { it.messageBody ?: "" }
            Transfers.store(context, key.first, body, parts.first().timestampMillis)
        }
    }
}
