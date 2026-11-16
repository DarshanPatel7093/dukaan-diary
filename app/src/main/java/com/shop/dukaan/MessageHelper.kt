package com.shop.dukaan

import android.content.Context
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageHelper {
    private const val PREFS_SETTINGS = "dukaan_settings"
    const val KEY_MESSAGE_LANG = "message_language"

    const val LANG_EN = "en"
    const val LANG_GU = "gu"
    const val LANG_HI = "hi"

    private val moneyFmt = DecimalFormat("#,##,###.##")
    private val shortDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun getMessageLanguage(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        return sp.getString(KEY_MESSAGE_LANG, LANG_EN) ?: LANG_EN
    }

    fun setMessageLanguage(context: Context, lang: String) {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_MESSAGE_LANG, lang).apply()
    }

    fun formatReminderMessage(context: Context, lead: Lead): String {
        val lang = getMessageLanguage(context)
        val dateStr = shortDateFmt.format(Date(lead.dateMillis))
        val amountStr = moneyFmt.format(lead.amount)
        val productStr = lead.product.trim()

        return when (lead.type) {
            TYPE_PAYMENT -> {
                when (lang) {
                    LANG_GU -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "નમસ્તે ${lead.name},\nદુકાન ડાયરી તરફથી યાદ: તમારું ₹$amountStr$prodText નું બાકી પેમેન્ટ ચૂકવવાની તારીખ $dateStr છે. કૃપા કરીને સમયસર જમા કરાવવા વિનંતી. આભાર!"
                    }
                    LANG_HI -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "नमस्ते ${lead.name},\nदुकान डायरी की ओर से अनुस्मारक: आपका ₹$amountStr$prodText का बकाया भुगतान $dateStr तक देय है। कृपया समय पर भुगतान करें। धन्यवाद!"
                    }
                    else -> {
                        val prodText = if (productStr.isNotBlank()) " for $productStr" else ""
                        "Hello ${lead.name},\nGentle reminder from Dukaan Diary regarding pending payment of ₹$amountStr$prodText due on $dateStr. Thank you!"
                    }
                }
            }
            TYPE_PAYMENT_PAY -> {
                when (lang) {
                    LANG_GU -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "નમસ્તે ${lead.name},\nતમારું ₹$amountStr$prodText ના પેમેન્ટની તારીખ $dateStr નક્કી કરેલ છે. આભાર! [દુકાન ડાયરી]"
                    }
                    LANG_HI -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "नमस्ते ${lead.name},\nआपके ₹$amountStr$prodText के भुगतान की तिथि $dateStr निर्धारित की गई है। धन्यवाद! [दुकान डायरी]"
                    }
                    else -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "Hello ${lead.name},\nPayment of ₹$amountStr$prodText is scheduled for $dateStr. Thank you! [Dukaan Diary]"
                    }
                }
            }
            else -> {
                // Service visit reminder to customer
                when (lang) {
                    LANG_GU -> {
                        val prodText = if (productStr.isNotBlank()) productStr else "તમારા સાધન"
                        "નમસ્તે ${lead.name},\nતમારા $prodText ની સર્વિસ વિઝીટ તારીખ $dateStr ના રોજ રાખેલ છે તેની યાદ અપાવવા માટે. અમારો ટેકનિશિયન તમને સંપર્ક કરશે. આભાર! [દુકાન ડાયરી]"
                    }
                    LANG_HI -> {
                        val prodText = if (productStr.isNotBlank()) productStr else "उपकरण"
                        "नमस्ते ${lead.name},\nआपके $prodText की सर्विस विज़िट $dateStr को निर्धारित की गई है। हमारे तकनीशियन आपसे संपर्क करेंगे। धन्यवाद! [दुकान डायरी]"
                    }
                    else -> {
                        val prodText = if (productStr.isNotBlank()) productStr else "appliance"
                        "Hello ${lead.name},\nReminder regarding service visit for $prodText scheduled on $dateStr. Thank you! [Dukaan Diary]"
                    }
                }
            }
        }
    }

    fun formatServiceDispatchMessage(
        context: Context,
        customerName: String,
        customerPhone: String,
        address: String,
        product: String,
        dateStr: String,
        notes: String
    ): String {
        val lang = getMessageLanguage(context)
        return when (lang) {
            LANG_GU -> {
                StringBuilder().apply {
                    append("🔧 *સર્વિસ કામ સોંપણી [દુકાન ડાયરી]*\n")
                    if (customerName.isNotBlank()) append("👤 ગ્રાહક: $customerName\n")
                    if (customerPhone.isNotBlank()) append("📞 ગ્રાહક મોબાઇલ: $customerPhone\n")
                    if (address.isNotBlank()) append("📍 સરનામું / લોકેશન: $address\n")
                    if (product.isNotBlank()) append("🛠️ સાધન / પ્રોડક્ટ: $product\n")
                    if (dateStr.isNotBlank()) append("📅 સર્વિસ તારીખ: $dateStr\n")
                    if (notes.isNotBlank()) append("📝 નોંધ: $notes\n")
                }.toString().trimEnd()
            }
            LANG_HI -> {
                StringBuilder().apply {
                    append("🔧 *सर्विस कार्य आवंटन [दुकान डायरी]*\n")
                    if (customerName.isNotBlank()) append("👤 ग्राहक: $customerName\n")
                    if (customerPhone.isNotBlank()) append("📞 ग्राहक मोबाइल: $customerPhone\n")
                    if (address.isNotBlank()) append("📍 पता / लोकेशन: $address\n")
                    if (product.isNotBlank()) append("🛠️ उपकरण / प्रोडक्ट: $product\n")
                    if (dateStr.isNotBlank()) append("📅 निर्धारित तिथि: $dateStr\n")
                    if (notes.isNotBlank()) append("📝 नोट: $notes\n")
                }.toString().trimEnd()
            }
            else -> {
                StringBuilder().apply {
                    append("🔧 *Service Visit Assignment [Dukaan Diary]*\n")
                    if (customerName.isNotBlank()) append("👤 Customer: $customerName\n")
                    if (customerPhone.isNotBlank()) append("📞 Customer Mobile: $customerPhone\n")
                    if (address.isNotBlank()) append("📍 Location / Address: $address\n")
                    if (product.isNotBlank()) append("🛠️ Appliance / Product: $product\n")
                    if (dateStr.isNotBlank()) append("📅 Scheduled Date: $dateStr\n")
                    if (notes.isNotBlank()) append("📝 Notes: $notes\n")
                }.toString().trimEnd()
            }
        }
    }

    fun formatEmployeeGreeting(context: Context, employeeName: String): String {
        val lang = getMessageLanguage(context)
        return when (lang) {
            LANG_GU -> "નમસ્તે $employeeName, દુકાન ડાયરી તરફથી સંદેશ."
            LANG_HI -> "नमस्ते $employeeName, दुकान डायरी की ओर से संदेश।"
            else -> "Hello $employeeName, message from Dukaan Diary."
        }
    }
}
