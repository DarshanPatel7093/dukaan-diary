package com.shop.dukaan

import android.content.Context
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MessageHelper {
    private const val PREFS_SETTINGS = "dukaan_settings"
    const val KEY_MESSAGE_LANG = "message_language"
    const val KEY_SHOP_NAME = "shop_name"
    const val KEY_SHOP_PHONE = "shop_phone"

    const val LANG_EN = "en"
    const val LANG_GU = "gu"
    const val LANG_HI = "hi"

    private val moneyFmt = DecimalFormat("#,##,###.##")
    private val shortDateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun getShopName(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        val name = sp.getString(KEY_SHOP_NAME, "")?.trim() ?: ""
        return if (name.isNotBlank()) name else "Our Shop"
    }

    fun setShopName(context: Context, name: String) {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SHOP_NAME, name.trim()).apply()
    }

    fun getShopPhone(context: Context): String {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        return sp.getString(KEY_SHOP_PHONE, "")?.trim() ?: ""
    }

    fun setShopPhone(context: Context, phone: String) {
        val sp = context.getSharedPreferences(PREFS_SETTINGS, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_SHOP_PHONE, phone.trim()).apply()
    }

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
        val shopName = getShopName(context)
        val dateStr = shortDateFmt.format(Date(lead.dateMillis))
        val amountStr = moneyFmt.format(lead.amount)
        val productStr = lead.product.trim()

        return when (lead.type) {
            TYPE_PAYMENT -> {
                when (lang) {
                    LANG_GU -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "નમસ્તે ${lead.name},\n$shopName તરફથી યાદ: તમારું ₹$amountStr$prodText નું બાકી પેમેન્ટ ચૂકવવાની તારીખ $dateStr છે. કૃપા કરીને સમયસર જમા કરાવવા વિનંતી. આભાર!"
                    }
                    LANG_HI -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "नमस्ते ${lead.name},\n$shopName की ओर से अनुस्मारक: आपका ₹$amountStr$prodText का बकाया भुगतान $dateStr तक देय है। कृपया समय पर भुगतान करें। धन्यवाद!"
                    }
                    else -> {
                        val prodText = if (productStr.isNotBlank()) " for $productStr" else ""
                        "Hello ${lead.name},\nGentle reminder from $shopName regarding pending payment of ₹$amountStr$prodText due on $dateStr. Thank you!"
                    }
                }
            }
            TYPE_PAYMENT_PAY -> {
                when (lang) {
                    LANG_GU -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "નમસ્તે ${lead.name},\nતમારું ₹$amountStr$prodText ના પેમેન્ટની તારીખ $dateStr નક્કી કરેલ છે. આભાર! [$shopName]"
                    }
                    LANG_HI -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "नमस्ते ${lead.name},\nआपके ₹$amountStr$prodText के भुगतान की तिथि $dateStr निर्धारित की गई है। धन्यवाद! [$shopName]"
                    }
                    else -> {
                        val prodText = if (productStr.isNotBlank()) " ($productStr)" else ""
                        "Hello ${lead.name},\nPayment of ₹$amountStr$prodText is scheduled for $dateStr. Thank you! [$shopName]"
                    }
                }
            }
            else -> {
                // Service visit reminder to customer
                when (lang) {
                    LANG_GU -> {
                        val prodText = if (productStr.isNotBlank()) productStr else "તમારા સાધન"
                        "નમસ્તે ${lead.name},\nતમારા $prodText ની સર્વિસ વિઝીટ તારીખ $dateStr ના રોજ રાખેલ છે તેની યાદ અપાવવા માટે. અમારો ટેકનિશિયન તમને સંપર્ક કરશે. આભાર! [$shopName]"
                    }
                    LANG_HI -> {
                        val prodText = if (productStr.isNotBlank()) productStr else "उपकरण"
                        "नमस्ते ${lead.name},\nआपके $prodText की सर्विस विज़िट $dateStr को निर्धारित की गई है। हमारे तकनीशियन आपसे संपर्क करेंगे। धन्यवाद! [$shopName]"
                    }
                    else -> {
                        val prodText = if (productStr.isNotBlank()) productStr else "appliance"
                        "Hello ${lead.name},\nReminder regarding service visit for $prodText scheduled on $dateStr. Thank you! [$shopName]"
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
        val shopName = getShopName(context)
        return when (lang) {
            LANG_GU -> {
                StringBuilder().apply {
                    append("🔧 *સર્વિસ કામ સોંપણી [$shopName]*\n")
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
                    append("🔧 *सर्विस कार्य आवंटन [$shopName]*\n")
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
                    append("🔧 *Service Visit Assignment [$shopName]*\n")
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
        val shopName = getShopName(context)
        return when (lang) {
            LANG_GU -> "નમસ્તે $employeeName, $shopName તરફથી સંદેશ."
            LANG_HI -> "नमस्ते $employeeName, $shopName की ओर से संदेश।"
            else -> "Hello $employeeName, message from $shopName."
        }
    }

    /**
     * Formats an instant payment receipt / thank you message for customer when payment received.
     */
    fun formatPaymentReceivedReceipt(
        context: Context,
        customerName: String,
        amount: Double,
        item: String = "",
        dateTimeStr: String
    ): String {
        val lang = getMessageLanguage(context)
        val shopName = getShopName(context)
        val amountStr = moneyFmt.format(amount)
        val itemText = if (item.isNotBlank()) " ($item)" else ""

        return when (lang) {
            LANG_GU -> {
                "💐 *પેમેન્ટ પાવતી - $shopName* 💐\n" +
                "નમસ્તે $customerName,\n" +
                "અમને તમારું ₹$amountStr$itemText નું પેમેન્ટ સફળતાપૂર્વક મળી ગયેલ છે.\n" +
                "📅 તારીખ: $dateTimeStr\n" +
                "તમારા હિસાબમાં આ રકમ જમા થયેલ છે. તમારા સાથ સહકાર બદલ ખૂબ ખૂબ આભાર!\n" +
                "— $shopName"
            }
            LANG_HI -> {
                "💐 *भुगतान रसीद - $shopName* 💐\n" +
                "नमस्ते $customerName,\n" +
                "हमें आपका ₹$amountStr$itemText का भुगतान सफलतापूर्वक प्राप्त हो गया है।\n" +
                "📅 तिथि: $dateTimeStr\n" +
                "यह राशि आपके खाते में जमा कर दी गई है। आपके सहयोग के लिए धन्यवाद!\n" +
                "— $shopName"
            }
            else -> {
                "💐 *Payment Receipt - $shopName* 💐\n" +
                "Dear $customerName,\n" +
                "Thank you! We have received your payment of ₹$amountStr$itemText.\n" +
                "📅 Date: $dateTimeStr\n" +
                "Your account is updated. Thank you for your business!\n" +
                "— $shopName"
            }
        }
    }

    /**
     * Formats a payment settlement confirmation for distributor.
     */
    fun formatPaymentPaidConfirmation(
        context: Context,
        vendorName: String,
        amount: Double,
        item: String = "",
        dateTimeStr: String
    ): String {
        val lang = getMessageLanguage(context)
        val shopName = getShopName(context)
        val amountStr = moneyFmt.format(amount)
        val itemText = if (item.isNotBlank()) " ($item)" else ""

        return when (lang) {
            LANG_GU -> {
                "✅ *પેમેન્ટ ચુકવણી પુષ્ટિ - $shopName*\n" +
                "નમસ્તે $vendorName,\n" +
                "અમારા તરફથી તમારું ₹$amountStr$itemText નું પેમેન્ટ/ચેક તારીખ $dateTimeStr ના રોજ ચૂકવી દેવામાં આવેલ છે.\n" +
                "કૃપા કરીને હિસાબ નોંધી લેશો. આભાર!\n" +
                "— $shopName"
            }
            LANG_HI -> {
                "✅ *भुगतान पुष्टि - $shopName*\n" +
                "नमस्ते $vendorName,\n" +
                "हमारी ओर से आपका ₹$amountStr$itemText का भुगतान $dateTimeStr को कर दिया गया है।\n" +
                "कृपया अपने खाते में नोट करें। धन्यवाद!\n" +
                "— $shopName"
            }
            else -> {
                "✅ *Payment Confirmation - $shopName*\n" +
                "Hello $vendorName,\n" +
                "Payment of ₹$amountStr$itemText has been completed on $dateTimeStr.\n" +
                "Please update in your books. Thank you!\n" +
                "— $shopName"
            }
        }
    }
}
