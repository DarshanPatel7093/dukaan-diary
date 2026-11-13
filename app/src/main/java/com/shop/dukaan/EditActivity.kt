package com.shop.dukaan

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditActivity : AppCompatActivity() {

    private lateinit var db: Db
    private var lead = Lead()
    private var dateMillis = startOfDay()

    // Voice note state
    private var originalAudio: String? = null   // what is saved in the database
    private var audioPath: String? = null       // what is currently attached
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var saved = false

    private lateinit var etName: TextInputEditText
    private lateinit var etPhone: TextInputEditText
    private lateinit var etProduct: TextInputEditText
    private lateinit var etAmount: TextInputEditText
    private lateinit var etAddress: TextInputEditText
    private lateinit var etNote: TextInputEditText
    private lateinit var tvDate: TextView
    private lateinit var tvVoiceStatus: TextView
    private lateinit var btnRecord: MaterialButton
    private lateinit var btnPlay: MaterialButton
    private lateinit var btnDeleteVoice: MaterialButton

    private val dateFmt = SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH)

    // Speech to text state
    private var activeSpeechTarget: TextInputEditText? = null
    private var pendingSpeechTarget: TextInputEditText? = null
    private var pendingSpeechPrompt: String = ""

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    activeSpeechTarget?.let { target ->
                        when (target) {
                            etAmount -> {
                                var num = ""
                                for (cand in matches) {
                                    val ext = extractNumber(cand)
                                    if (ext.isNotEmpty()) {
                                        num = ext
                                        break
                                    }
                                }
                                if (num.isEmpty()) num = extractNumber(matches[0])
                                target.setText(num)
                            }
                            etProduct -> {
                                target.setText(cleanProductFromCandidates(matches))
                            }
                            etName -> {
                                target.setText(capitalizeWords(matches[0]))
                            }
                            etAddress -> {
                                target.setText(capitalizeWords(matches[0]))
                            }
                            etNote -> {
                                val text = matches[0].trim()
                                if (!target.text.isNullOrBlank()) {
                                    target.append(" $text")
                                } else {
                                    target.setText(text)
                                }
                            }
                            else -> {
                                target.setText(matches[0])
                            }
                        }
                        target.setSelection(target.text?.length ?: 0)
                    }
                } else {
                    toast("No speech detected. Please speak clearly.")
                }
            }
        }

    private val speechMicPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingSpeechTarget?.let { startSpeechToText(it, pendingSpeechPrompt) }
            } else {
                toast("Microphone permission is needed for speech-to-text")
            }
        }

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startRecording()
            else toast("Microphone permission is needed to record a voice note")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        db = Db(this)

        val id = intent.getLongExtra("id", 0L)
        if (id != 0L) {
            lead = db.get(id) ?: Lead()
        }
        if (lead.id == 0L) {
            lead.type = intent.getStringExtra("type") ?: TYPE_PAYMENT
        }
        val isPayment = lead.type == TYPE_PAYMENT

        etName = findViewById(R.id.etName)
        etPhone = findViewById(R.id.etPhone)
        etProduct = findViewById(R.id.etProduct)
        etAmount = findViewById(R.id.etAmount)
        etAddress = findViewById(R.id.etAddress)
        etNote = findViewById(R.id.etNote)
        tvDate = findViewById(R.id.tvDate)
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus)
        btnRecord = findViewById(R.id.btnRecord)
        btnPlay = findViewById(R.id.btnPlay)
        btnDeleteVoice = findViewById(R.id.btnDeleteVoice)

        val tvTitle: TextView = findViewById(R.id.tvTitle)
        val tvVoiceLang: TextView = findViewById(R.id.tvVoiceLang)
        tvVoiceLang.text = getSavedLanguageLabel() + " ▾"
        tvVoiceLang.setOnClickListener { showLanguageDialog(tvVoiceLang) }
        val tilName: TextInputLayout = findViewById(R.id.tilName)
        val tilProduct: TextInputLayout = findViewById(R.id.tilProduct)
        val tilAmount: TextInputLayout = findViewById(R.id.tilAmount)
        val tilAddress: TextInputLayout = findViewById(R.id.tilAddress)
        val tilNote: TextInputLayout = findViewById(R.id.tilNote)
        val tvDateLabel: TextView = findViewById(R.id.tvDateLabel)
        val btnDeleteEntry: MaterialButton = findViewById(R.id.btnDeleteEntry)

        if (isPayment) {
            tvTitle.text = if (lead.id == 0L) "New Payment (Udhar)" else "Edit Payment"
            tilProduct.hint = "Product given on credit"
            tvDateLabel.text = "Customer will pay on:"
            tilAddress.visibility = View.GONE
        } else {
            tvTitle.text = if (lead.id == 0L) "New Service Visit" else "Edit Service Visit"
            tilProduct.hint = "Product (e.g. RO filter)"
            tvDateLabel.text = "Worker should visit on:"
            tilAmount.visibility = View.GONE
        }

        // Speech-to-text mic click listeners
        tilName.setEndIconOnClickListener {
            requestSpeech(etName, "Say customer name")
        }
        tilProduct.setEndIconOnClickListener {
            requestSpeech(etProduct, if (isPayment) "Say product given on credit" else "Say product or appliance")
        }
        tilAmount.setEndIconOnClickListener {
            requestSpeech(etAmount, "Say amount (e.g. 500)")
        }
        tilAddress.setEndIconOnClickListener {
            requestSpeech(etAddress, "Say customer address")
        }
        tilNote.setEndIconOnClickListener {
            requestSpeech(etNote, "Say note")
        }

        // Fill existing values
        if (lead.id != 0L) {
            etName.setText(lead.name)
            etPhone.setText(lead.phone)
            etProduct.setText(lead.product)
            if (lead.amount > 0) etAmount.setText(trimZero(lead.amount))
            etAddress.setText(lead.address)
            etNote.setText(lead.note)
            dateMillis = lead.dateMillis
        } else {
            dateMillis = if (isPayment) startOfDay() else addDays(startOfDay(), 1)
        }
        originalAudio = lead.audioPath
        audioPath = lead.audioPath
        updateDate()
        updateVoiceUi()

        findViewById<MaterialButton>(R.id.btnToday).setOnClickListener {
            dateMillis = startOfDay(); updateDate()
        }
        findViewById<MaterialButton>(R.id.btnTomorrow).setOnClickListener {
            dateMillis = addDays(startOfDay(), 1); updateDate()
        }
        findViewById<MaterialButton>(R.id.btnPick).setOnClickListener { pickDate() }

        btnRecord.setOnClickListener {
            if (recorder != null) {
                stopRecording()
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                startRecording()
            } else {
                micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        btnPlay.setOnClickListener { togglePlay() }
        btnDeleteVoice.setOnClickListener { confirmDeleteVoice() }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }

        if (lead.id == 0L) {
            btnDeleteEntry.visibility = View.GONE
        } else {
            btnDeleteEntry.setOnClickListener { confirmDeleteEntry() }
        }
    }

    // ---------- Date ----------

    private fun updateDate() {
        tvDate.text = dateFmt.format(Date(dateMillis))
    }

    private fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = dateMillis }
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = Calendar.getInstance()
                picked.set(y, m, d, 0, 0, 0)
                dateMillis = startOfDay(picked.timeInMillis)
                updateDate()
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    // ---------- Voice note ----------

    private fun updateVoiceUi() {
        val recording = recorder != null
        val playing = player != null
        btnRecord.text = if (recording) "⏹  STOP RECORDING"
        else if (audioPath != null) "🎤  RECORD AGAIN" else "🎤  START RECORDING"
        btnRecord.backgroundTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, if (recording) R.color.red else R.color.accent)
        )
        btnPlay.text = if (playing) "⏹  STOP" else "▶  PLAY"
        btnPlay.isEnabled = audioPath != null && !recording
        btnDeleteVoice.isEnabled = audioPath != null && !recording
        tvVoiceStatus.text = when {
            recording -> "Recording... speak now, then press STOP"
            audioPath != null -> "Voice note is saved"
            else -> "No voice note"
        }
    }

    private fun startRecording() {
        stopPlayback()
        // Throw away an unsaved earlier take; keep the saved one until Save is pressed.
        if (audioPath != null && audioPath != originalAudio) File(audioPath!!).delete()
        audioPath = originalAudio

        val dir = File(filesDir, "voice").apply { mkdirs() }
        val file = File(dir, "note_${System.currentTimeMillis()}.m4a")
        @Suppress("DEPRECATION")
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            audioPath = file.absolutePath
        } catch (e: Exception) {
            r.release()
            file.delete()
            toast("Could not start recording")
        }
        updateVoiceUi()
    }

    private fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        try {
            r.stop()
        } catch (e: RuntimeException) {
            // Recording was too short to be valid
            audioPath?.let { if (it != originalAudio) File(it).delete() }
            audioPath = originalAudio
            toast("Recording too short, try again")
        } finally {
            r.release()
        }
        updateVoiceUi()
    }

    private fun togglePlay() {
        if (player != null) {
            stopPlayback()
            updateVoiceUi()
            return
        }
        val path = audioPath ?: return
        try {
            player = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                setOnCompletionListener {
                    stopPlayback()
                    updateVoiceUi()
                }
                start()
            }
        } catch (e: Exception) {
            player?.release()
            player = null
            toast("Could not play this recording")
        }
        updateVoiceUi()
    }

    private fun stopPlayback() {
        player?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        player = null
    }

    private fun confirmDeleteVoice() {
        AlertDialog.Builder(this)
            .setTitle("Delete this voice note?")
            .setPositiveButton("YES, DELETE") { _, _ ->
                stopPlayback()
                audioPath?.let { File(it).delete() }
                if (audioPath == originalAudio) {
                    originalAudio = null
                    if (lead.id != 0L) db.setAudio(lead.id, null)
                }
                audioPath = null
                updateVoiceUi()
                toast("Voice note deleted")
            }
            .setNegativeButton("NO", null)
            .show()
    }

    // ---------- Save / delete ----------

    private fun save() {
        if (recorder != null) stopRecording()
        val name = etName.text.toString().trim()
        if (name.isEmpty()) {
            etName.error = "Please enter customer name"
            etName.requestFocus()
            return
        }
        lead.name = name
        lead.phone = etPhone.text.toString().trim()
        lead.product = etProduct.text.toString().trim()
        lead.amount = etAmount.text.toString().trim().toDoubleOrNull() ?: 0.0
        lead.address = etAddress.text.toString().trim()
        lead.note = etNote.text.toString().trim()
        lead.dateMillis = dateMillis

        // Replaced recording: remove the old file
        if (originalAudio != null && originalAudio != audioPath) File(originalAudio!!).delete()
        lead.audioPath = audioPath

        db.save(lead)
        saved = true
        toast("Saved")
        finish()
    }

    private fun confirmDeleteEntry() {
        AlertDialog.Builder(this)
            .setTitle("Delete this entry?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("YES, DELETE") { _, _ ->
                stopPlayback()
                // Remove any unsaved new take too
                if (audioPath != null && audioPath != originalAudio) File(audioPath!!).delete()
                db.deleteWithAudio(lead)
                saved = true
                finish()
            }
            .setNegativeButton("NO", null)
            .show()
    }

    override fun onStop() {
        super.onStop()
        if (recorder != null) stopRecording()
        stopPlayback()
        updateVoiceUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (recorder != null) stopRecording()
        stopPlayback()
        // Left without saving: discard a new, unsaved recording
        if (!saved && audioPath != null && audioPath != originalAudio) {
            File(audioPath!!).delete()
        }
    }

    private fun requestSpeech(target: TextInputEditText, prompt: String) {
        if (recorder != null) stopRecording()
        stopPlayback()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startSpeechToText(target, prompt)
        } else {
            pendingSpeechTarget = target
            pendingSpeechPrompt = prompt
            speechMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private val PREFS_NAME = "dukaan_prefs"
    private val KEY_VOICE_LANG = "voice_lang_code"
    private val KEY_VOICE_LANG_LABEL = "voice_lang_label"

    private fun getSavedLanguageCode(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_VOICE_LANG, "en-IN") ?: "en-IN"
    }

    private fun getSavedLanguageLabel(): String {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return sp.getString(KEY_VOICE_LANG_LABEL, "English (India)") ?: "English (India)"
    }

    private fun saveLanguage(code: String, label: String) {
        val sp = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(KEY_VOICE_LANG, code).putString(KEY_VOICE_LANG_LABEL, label).apply()
    }

    private fun showLanguageDialog(tvTarget: TextView) {
        val languages = arrayOf(
            "English (India)",
            "ગુજરાતી (Gujarati)",
            "हिंदी (Hindi)",
            "English (US)"
        )
        val codes = arrayOf("en-IN", "gu-IN", "hi-IN", "en-US")
        val currentCode = getSavedLanguageCode()
        val currentIndex = codes.indexOf(currentCode).let { if (it >= 0) it else 0 }

        AlertDialog.Builder(this)
            .setTitle("Voice Language")
            .setSingleChoiceItems(languages, currentIndex) { dialog, which ->
                saveLanguage(codes[which], languages[which])
                tvTarget.text = languages[which] + " ▾"
                toast("Voice language set to " + languages[which])
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSpeechToText(target: TextInputEditText, prompt: String) {
        activeSpeechTarget = target
        val langCode = getSavedLanguageCode()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
        try {
            speechRecognizerLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Speech recognition is not available on this device")
        }
    }

    private fun cleanProductFromCandidates(candidates: List<String>): String {
        for (raw in candidates) {
            val res = identifyProduct(raw)
            if (res != null) return res
        }
        return capitalizeWords(candidates[0].trim())
    }

    private fun identifyProduct(raw: String): String? {
        val trimmed = raw.trim()
        val lower = trimmed.lowercase(Locale.ENGLISH)

        return when {
            lower.contains("phrease") || lower.contains("phrase") || lower.contains("friedge") ||
            lower.contains("frige") || lower.contains("fridge") || lower.contains("freez") || lower.contains("freeze") ||
            lower.contains("frize") || lower.contains("frij") || lower.contains("friz") -> "Fridge"

            lower.contains("washing") || lower.contains("vashing") || lower.contains("kapda") -> "Washing Machine"
            lower.contains("geyser") || lower.contains("giser") || lower.contains("gizer") || lower.contains("geezer") -> "Geyser"
            lower.contains("cooler") || lower.contains("kooler") -> "Cooler"
            lower.contains("arrow") || lower.contains("ar o") || lower.contains("r o") || lower == "ro" || lower.contains("purifier") -> "RO Filter"
            lower.contains("fan") || lower.contains("pankha") || lower.contains("pankho") -> "Fan"
            lower.contains("ac") || lower.contains("a c") || lower.contains("air conditioner") -> "AC"
            lower.contains("tv") || lower.contains("t v") || lower.contains("television") -> "TV"
            lower.contains("microwave") || lower.contains("oven") -> "Microwave Oven"
            lower.contains("inverter") || lower.contains("invertor") -> "Inverter"
            lower.contains("motor") || lower.contains("pump") -> "Water Motor"
            lower.contains("chimney") || lower.contains("chimni") -> "Chimney"
            lower.contains("mobile") || lower.contains("phone") -> "Mobile Phone"
            lower.contains("laptop") || lower.contains("computer") -> "Laptop"
            else -> null
        }
    }

    private fun capitalizeWords(text: String): String {
        return text.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    private fun extractNumber(text: String): String {
        val cleaned = text.replace(",", "")
            .replace("₹", "")
            .replace("Rs.", "", ignoreCase = true)
            .replace("Rs", "", ignoreCase = true)
            .trim()
        val match = Regex("""\d+(\.\d+)?""").find(cleaned)
        return match?.value ?: cleaned.filter { it.isDigit() || it == '.' }
    }

    private fun trimZero(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
