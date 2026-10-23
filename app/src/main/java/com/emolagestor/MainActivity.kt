package mz.emola.gestor

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val pt = Locale("pt", "PT")
    private val money = NumberFormat.getNumberInstance(pt).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }
    private val selected = Calendar.getInstance()
    private var period = 0 // 0 dia, 1 mês, 2 ano
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render() }
    override fun onResume() { super.onResume(); render() }
    private fun label(text: String, size: Float = 17f) = TextView(this).apply { this.text = text; textSize = size; setPadding(0, 10, 0, 10) }
    private fun add(container: LinearLayout, text: String, size: Float = 17f) { container.addView(label(text, size)) }
    private fun recordDate(row: JSONObject): Calendar = Calendar.getInstance().apply { timeInMillis = row.optLong("time") }
    private fun inPeriod(row: JSONObject): Boolean {
        val date = recordDate(row)
        if (date.get(Calendar.YEAR) != selected.get(Calendar.YEAR)) return false
        if (period <= 1 && date.get(Calendar.MONTH) != selected.get(Calendar.MONTH)) return false
        return period != 0 || date.get(Calendar.DAY_OF_MONTH) == selected.get(Calendar.DAY_OF_MONTH)
    }
    private fun dateTitle(): String = when (period) {
        0 -> SimpleDateFormat("dd/MM/yyyy", pt).format(selected.time)
        1 -> SimpleDateFormat("MMMM yyyy", pt).format(selected.time)
        else -> SimpleDateFormat("yyyy", pt).format(selected.time)
    }
    private fun shift(amount: Int) { selected.add(when (period) { 0 -> Calendar.DAY_OF_MONTH; 1 -> Calendar.MONTH; else -> Calendar.YEAR }, amount); render() }
    private fun render() {
        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(30, 30, 30, 24) }
        scroll.addView(layout); setContentView(scroll)
        add(layout, "e-Mola Gestor", 28f)
        add(layout, "Relatórios de transferências por SMS", 14f)
        val granted = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        add(layout, if (granted) "✓ Recepção automática de SMS autorizada" else "Autorize a recepção de SMS", 14f)
        if (!granted) layout.addView(Button(this).apply { text = "Autorizar SMS"; setOnClickListener { requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 101) } })
        val picker = Spinner(this)
        picker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Diário", "Mensal", "Anual"))
        picker.setSelection(period)
        picker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (period != position) { period = position; render() }
            }
        }
        layout.addView(picker)
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navigation.addView(Button(this).apply { text = "‹"; setOnClickListener { shift(-1) } })
        navigation.addView(Button(this).apply { text = dateTitle(); setOnClickListener {
            DatePickerDialog(this@MainActivity, { _, y, m, d -> selected.set(y, m, d); render() }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH)).show()
        }; layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        navigation.addView(Button(this).apply { text = "›"; setOnClickListener { shift(1) } })
        layout.addView(navigation)
        val records = runCatching { JSONArray(getSharedPreferences("transfers", MODE_PRIVATE).getString("records", "[]")) }.getOrDefault(JSONArray())
        val rows = (0 until records.length()).mapNotNull { records.optJSONObject(it) }.filter { inPeriod(it) }.sortedByDescending { it.optLong("time") }
        val received = rows.filter { it.optString("type") == "Recebido" }.sumOf { it.optDouble("amount") }
        val sent = rows.filter { it.optString("type") == "Enviado" }.sumOf { it.optDouble("amount") }
        add(layout, "Resumo — ${dateTitle()}", 21f)
        add(layout, "Recebido: ${money.format(received)} MT", 20f)
        add(layout, "Transferido: ${money.format(sent)} MT", 20f)
        add(layout, "Diferença: ${money.format(received - sent)} MT", 19f)
        add(layout, "${rows.size} transacção(ões) no período", 14f)
        layout.addView(Button(this).apply { text = "Actualizar"; setOnClickListener { render() } })
        add(layout, "Movimentos", 22f)
        val fmt = SimpleDateFormat("dd/MM/yyyy HH:mm", pt)
        if (rows.isEmpty()) add(layout, "Sem movimentos registados neste período.", 15f)
        rows.forEach { row -> add(layout, "${row.optString("type")} • ${money.format(row.optDouble("amount"))} MT\n${fmt.format(Date(row.optLong("time")))}", 16f) }
        add(layout, "Os relatórios incluem apenas SMS novos reconhecidos após a instalação. Diferença não é o saldo real da conta. Dados guardados apenas no dispositivo.", 13f)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) render()
    }
}
