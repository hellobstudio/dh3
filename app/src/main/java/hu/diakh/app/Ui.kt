package hu.diakh.app

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate

// ---------------------------------------------------------------- közös

private val LESSONS = listOf("?", "1", "2", "3", "4", "5", "6", "7")

class Form(a: Absence? = null) {
    var from by mutableStateOf(a?.from ?: LocalDate.now().toString())
    var to by mutableStateOf(a?.to ?: LocalDate.now().toString())
    var allDay by mutableStateOf(a?.allDay ?: true)
    var lFrom by mutableStateOf(a?.lFrom?.takeIf { it.isNotEmpty() } ?: "1")
    var lTo by mutableStateOf(a?.lTo?.takeIf { it.isNotEmpty() } ?: "7")
    var reporter by mutableStateOf(a?.reporter ?: "")
    var reason by mutableStateOf(a?.reason ?: "")
    var note by mutableStateOf(a?.note ?: "")

    fun validate(): String? =
        if (to != "?" && to < from) "A „meddig” nem lehet korábbi a „mettől” dátumnál." else null
}

private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

private fun showDate(d: String) = when (d) {
    "" -> "–"
    "?" -> "?"
    else -> d.replace('-', '.')
}

private fun Absence.timeText(): String {
    val days = if (to == from) showDate(from) else "${showDate(from)} – ${showDate(to)}"
    val t = if (allDay) "egész nap" else "$lFrom–$lTo. óra"
    return "$days • $t"
}

@Composable
fun DateBtn(label: String, iso: String, modifier: Modifier = Modifier, enabled: Boolean = true, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = {
            val d = runCatching { LocalDate.parse(iso) }.getOrElse { LocalDate.now() }
            DatePickerDialog(ctx, { _, y, m, day ->
                onPick(String.format("%04d-%02d-%02d", y, m + 1, day))
            }, d.year, d.monthValue - 1, d.dayOfMonth).show()
        },
        enabled = enabled,
        modifier = modifier
    ) { Text("$label: ${showDate(iso)}") }
}

@Composable
fun Dropdown(label: String, value: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${value.ifEmpty { "válassz" }} ▾", maxLines = 1)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach {
                DropdownMenuItem(text = { Text(it) }, onClick = { onSelect(it); open = false })
            }
        }
    }
}

@Composable
fun AbsenceFields(f: Form, reporters: List<String>, reasons: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DateBtn("Mettől", f.from, Modifier.fillMaxWidth()) { f.from = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBtn("Meddig", f.to, Modifier.weight(1f), enabled = f.to != "?") { f.to = it }
            Checkbox(checked = f.to == "?", onCheckedChange = { f.to = if (it) "?" else f.from })
            Text("?")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = f.allDay, onClick = { f.allDay = true }, label = { Text("Egész nap") })
            FilterChip(selected = !f.allDay, onClick = { f.allDay = false }, label = { Text("Órák (1–7)") })
        }
        if (!f.allDay) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Dropdown("Órától", f.lFrom, LESSONS, Modifier.weight(1f)) { f.lFrom = it }
                Dropdown("Óráig", f.lTo, LESSONS, Modifier.weight(1f)) { f.lTo = it }
            }
        }
        Dropdown("Bejelentő", f.reporter, reporters, Modifier.fillMaxWidth()) { f.reporter = it }
        Dropdown("Ok", f.reason, reasons, Modifier.fillMaxWidth()) { f.reason = it }
        OutlinedTextField(
            value = f.note, onValueChange = { f.note = it },
            label = { Text("Megjegyzés") }, modifier = Modifier.fillMaxWidth(), minLines = 2
        )
    }
}

// ---------------------------------------------------------------- ÚJ

@Composable
fun NewScreen(db: Db, ver: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    val students = remember(ver) { db.students() }
    val reporters = remember(ver) { db.names("reporters") }
    val reasons = remember(ver) { db.names("reasons") }
    var q by remember { mutableStateOf("") }
    val sel = remember { mutableStateListOf<Long>() }
    val form = remember { Form() }
    val shown = students.filter { it.name.contains(q, ignoreCase = true) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Diákok kijelölése (kijelölve: ${sel.size})", fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text("Keresés névre") }, modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier.fillMaxWidth().height(280.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
        ) {
            if (students.isEmpty()) {
                Text("Nincs diák. Importálj egy Excel táblázatot a Beállításokban.", Modifier.padding(12.dp))
            } else {
                LazyColumn {
                    items(shown, key = { it.id }) { s ->
                        val on = s.id in sel
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { if (on) sel.remove(s.id) else sel.add(s.id) }
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = on, onCheckedChange = { if (on) sel.remove(s.id) else sel.add(s.id) })
                            Column {
                                Text(s.name, fontWeight = FontWeight.SemiBold)
                                Text("szül.: ${s.birth} • anyja: ${s.mother}", fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        AbsenceFields(form, reporters, reasons)

        Button(
            onClick = {
                val err = form.validate()
                if (sel.isEmpty()) toast(ctx, "Jelölj ki legalább egy diákot.")
                else if (err != null) toast(ctx, err)
                else {
                    db.addAbsences(sel.toList(), form)
                    toast(ctx, "${sel.size} hiányzás rögzítve.")
                    sel.clear()
                    form.note = ""
                    bump()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("✔ Rögzítés") }
    }
}

// ---------------------------------------------------------------- HIÁNYZÁSOK

private fun exportAndShare(ctx: Context, list: List<Absence>) {
    val rows = mutableListOf(
        listOf("Név", "Születési dátum", "Anyja neve", "Mettől", "Meddig", "Időtartam", "Bejelentő", "Ok", "Megjegyzés")
    )
    list.forEach {
        rows.add(listOf(
            it.name, it.birth, it.mother, showDate(it.from), showDate(it.to),
            if (it.allDay) "Egész nap" else "${it.lFrom}–${it.lTo}. óra",
            it.reporter, it.reason, it.note
        ))
    }
    val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, "hianyzasok.xlsx")
    Xlsx.write(file, rows)
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", file)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, "DiákH – hiányzások")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Megosztás e-mailben"))
}

@Composable
fun AbsScreen(db: Db, ver: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    val all = remember(ver) { db.absences() }
    val reporters = remember(ver) { db.names("reporters") }
    val reasons = remember(ver) { db.names("reasons") }
    var q by remember { mutableStateOf("") }
    var day by remember { mutableStateOf("") }
    var edit by remember { mutableStateOf<Absence?>(null) }
    var del by remember { mutableStateOf<Absence?>(null) }
    var delAll by remember { mutableStateOf(false) }

    val list = all.filter { a ->
        a.name.contains(q, ignoreCase = true) &&
            (day.isEmpty() || (a.from <= day && (a.to == "?" || day <= a.to)))
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            label = { Text("Keresés diák neve alapján") }, modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateBtn("Dátum", day, Modifier.weight(1f)) { day = it }
            if (day.isNotEmpty()) TextButton(onClick = { day = "" }) { Text("✕") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { runCatching { exportAndShare(ctx, list) }.onFailure { toast(ctx, "Export sikertelen.") } },
                enabled = list.isNotEmpty(), modifier = Modifier.weight(1f)
            ) { Text("📤 Excel, e-mail", maxLines = 1) }
            OutlinedButton(
                onClick = { delAll = true }, enabled = all.isNotEmpty(), modifier = Modifier.weight(1f)
            ) { Text("🗑 Összes törlése", maxLines = 1) }
        }
        Text("${list.size} találat", fontSize = 12.sp)

        LazyColumn(Modifier.weight(1f)) {
            items(list, key = { it.id }) { a ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(a.name, fontWeight = FontWeight.Bold)
                        Text("szül.: ${a.birth} • anyja: ${a.mother}", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(a.timeText(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                        Text("Bejelentő: ${a.reporter.ifEmpty { "–" }} • Ok: ${a.reason.ifEmpty { "–" }}")
                        if (a.note.isNotEmpty()) Text("Megjegyzés: ${a.note}")
                        Row {
                            TextButton(onClick = { edit = a }) { Text("✏️ Módosítás") }
                            TextButton(onClick = { del = a }) { Text("🗑 Törlés") }
                        }
                    }
                }
            }
        }
    }

    edit?.let { a ->
        val f = remember(a.id) { Form(a) }
        AlertDialog(
            onDismissRequest = { edit = null },
            title = { Text("Módosítás: ${a.name}") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { AbsenceFields(f, reporters, reasons) } },
            confirmButton = {
                TextButton(onClick = {
                    val err = f.validate()
                    if (err != null) toast(ctx, err) else { db.updateAbsence(a.id, f); edit = null; bump() }
                }) { Text("Mentés") }
            },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Mégse") } }
        )
    }

    del?.let { a ->
        AlertDialog(
            onDismissRequest = { del = null },
            title = { Text("Törlés") },
            text = { Text("Biztosan törlöd ${a.name} hiányzását (${a.timeText()})?") },
            confirmButton = { TextButton(onClick = { db.deleteAbsence(a.id); del = null; bump() }) { Text("Törlés") } },
            dismissButton = { TextButton(onClick = { del = null }) { Text("Mégse") } }
        )
    }

    if (delAll) {
        AlertDialog(
            onDismissRequest = { delAll = false },
            title = { Text("Minden hiányzás törlése") },
            text = { Text("Az összes rögzített hiányzás (${all.size} db) törlődik. Ez nem vonható vissza.") },
            confirmButton = { TextButton(onClick = { db.deleteAllAbsences(); delAll = false; bump() }) { Text("Mindet törlöm") } },
            dismissButton = { TextButton(onClick = { delAll = false }) { Text("Mégse") } }
        )
    }
}

// ---------------------------------------------------------------- BEÁLLÍTÁSOK

private fun fixBirth(v: String): String {
    val s = v.trim()
    val num = s.toDoubleOrNull()
    if (num != null && num > 1000 && num < 80000) {
        val d = LocalDate.of(1899, 12, 30).plusDays(num.toLong())
        return String.format("%04d.%02d.%02d.", d.year, d.monthValue, d.dayOfMonth)
    }
    return s
}

@Composable
fun SettingsScreen(db: Db, ver: Int, bump: () -> Unit, dark: Boolean, setDark: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val count = remember(ver) { db.students().size }
    var confirmDel by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            try {
                val rows = Xlsx.read(ctx, uri)
                val list = rows.mapIndexedNotNull { i, r ->
                    val name = r.getOrElse(0) { "" }.trim()
                    val birth = r.getOrElse(1) { "" }
                    val isHeader = i == 0 && (name.equals("név", true) || name.equals("nev", true) || birth.contains("szül", true))
                    if (name.isEmpty() || isHeader) null
                    else Student(0, name, fixBirth(birth), r.getOrElse(2) { "" }.trim())
                }
                val n = db.importStudents(list)
                toast(ctx, "$n új diák importálva (${list.size - n} már szerepelt).")
                bump()
            } catch (e: Exception) {
                toast(ctx, "Az import nem sikerült. Csak .xlsx fájl támogatott.")
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Téma", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                Text("☀️", fontSize = 22.sp)
                Switch(checked = dark, onCheckedChange = setDark, modifier = Modifier.padding(horizontal = 8.dp))
                Text("🌙", fontSize = 22.sp)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Diákok ($count db)", fontWeight = FontWeight.Bold)
                Text("Excel (.xlsx) oszlopai: A = név, B = születési dátum, C = anyja neve. " +
                    "A már meglévő diákok nem duplázódnak.", fontSize = 12.sp)
                Button(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text("📥 Diákok importálása Excelből")
                }
                OutlinedButton(onClick = { confirmDel = true }, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
                    Text("🗑 Összes diák törlése")
                }
            }
        }

        NameManager("Bejelentők", "reporters", db, ver, bump)
        NameManager("Hiányzás okai", "reasons", db, ver, bump)
    }

    if (confirmDel) {
        AlertDialog(
            onDismissRequest = { confirmDel = false },
            title = { Text("Összes diák törlése") },
            text = { Text("A diákokkal együtt az összes hiányzásuk is törlődik. Ez nem vonható vissza.") },
            confirmButton = { TextButton(onClick = { db.deleteAllStudents(); confirmDel = false; bump() }) { Text("Törlés") } },
            dismissButton = { TextButton(onClick = { confirmDel = false }) { Text("Mégse") } }
        )
    }
}

@Composable
fun NameManager(title: String, table: String, db: Db, ver: Int, bump: () -> Unit) {
    val ctx = LocalContext.current
    val items = remember(ver) { db.names(table) }
    var text by remember { mutableStateOf("") }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it }, singleLine = true,
                    label = { Text("Új hozzáadása") }, modifier = Modifier.weight(1f)
                )
                Button(onClick = {
                    val t = text.trim()
                    if (t.isNotEmpty()) {
                        if (db.addName(table, t)) { text = ""; bump() } else toast(ctx, "Ez már szerepel a listában.")
                    }
                }) { Text("＋") }
            }
            items.forEach { n ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(n, Modifier.weight(1f))
                    TextButton(onClick = { db.deleteName(table, n); bump() }) { Text("✕") }
                }
            }
        }
    }
}
