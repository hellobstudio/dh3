package hu.diakh.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.Collator
import java.util.Locale

data class Student(val id: Long, val name: String, val birth: String, val mother: String)

data class Absence(
    val id: Long, val studentId: Long,
    val name: String, val birth: String, val mother: String,
    val from: String, val to: String, val allDay: Boolean,
    val lFrom: String, val lTo: String,
    val reporter: String, val reason: String, val note: String
)

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "diakh.db", null, 1) {

    private val col: Collator = Collator.getInstance(Locale("hu", "HU"))

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE students(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, birth TEXT NOT NULL, mother TEXT NOT NULL)")
        db.execSQL("CREATE TABLE reporters(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL("CREATE TABLE reasons(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE)")
        db.execSQL(
            "CREATE TABLE absences(id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "student_id INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE, " +
                "d_from TEXT NOT NULL, d_to TEXT NOT NULL, all_day INTEGER NOT NULL, " +
                "l_from TEXT NOT NULL, l_to TEXT NOT NULL, " +
                "reporter TEXT NOT NULL, reason TEXT NOT NULL, note TEXT NOT NULL)"
        )
        listOf("Diák", "Orvos", "Osztályfőnök", "Szülő").forEach {
            db.execSQL("INSERT INTO reporters(name) VALUES(?)", arrayOf<Any?>(it))
        }
        listOf("Betegség", "Családi ok", "Egyéb", "Orvosi vizsgálat", "Verseny, rendezvény").forEach {
            db.execSQL("INSERT INTO reasons(name) VALUES(?)", arrayOf<Any?>(it))
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}

    private fun <T> query(sql: String, f: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        readableDatabase.rawQuery(sql, null).use { c -> while (c.moveToNext()) out.add(f(c)) }
        return out
    }

    private fun exec(sql: String, vararg a: Any?) = writableDatabase.execSQL(sql, arrayOf<Any?>(*a))

    // ---- Diákok ----
    fun students(): List<Student> =
        query("SELECT id,name,birth,mother FROM students") {
            Student(it.getLong(0), it.getString(1), it.getString(2), it.getString(3))
        }.sortedWith { a, b -> col.compare(a.name, b.name) }

    fun importStudents(list: List<Student>): Int {
        val db = writableDatabase
        val known = students().map { Triple(it.name, it.birth, it.mother) }.toHashSet()
        var n = 0
        db.beginTransaction()
        try {
            for (s in list) {
                if (known.add(Triple(s.name, s.birth, s.mother))) {
                    db.execSQL("INSERT INTO students(name,birth,mother) VALUES(?,?,?)", arrayOf<Any?>(s.name, s.birth, s.mother))
                    n++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return n
    }

    fun deleteAllStudents() = exec("DELETE FROM students")

    // ---- Bejelentők / okok (table: "reporters" vagy "reasons") ----
    fun names(table: String): List<String> =
        query("SELECT name FROM $table") { it.getString(0) }.sortedWith { a, b -> col.compare(a, b) }

    fun addName(table: String, name: String): Boolean =
        writableDatabase.insertWithOnConflict(
            table, null, ContentValues().apply { put("name", name) }, SQLiteDatabase.CONFLICT_IGNORE
        ) != -1L

    fun deleteName(table: String, name: String) = exec("DELETE FROM $table WHERE name=?", name)

    // ---- Hiányzások ----
    fun absences(): List<Absence> = query(
        "SELECT a.id,a.student_id,s.name,s.birth,s.mother,a.d_from,a.d_to,a.all_day,a.l_from,a.l_to," +
            "a.reporter,a.reason,a.note FROM absences a JOIN students s ON s.id=a.student_id " +
            "ORDER BY a.d_from DESC, a.id DESC"
    ) {
        Absence(
            it.getLong(0), it.getLong(1), it.getString(2), it.getString(3), it.getString(4),
            it.getString(5), it.getString(6), it.getInt(7) == 1, it.getString(8), it.getString(9),
            it.getString(10), it.getString(11), it.getString(12)
        )
    }

    fun addAbsences(studentIds: List<Long>, f: Form) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in studentIds) {
                db.execSQL(
                    "INSERT INTO absences(student_id,d_from,d_to,all_day,l_from,l_to,reporter,reason,note) VALUES(?,?,?,?,?,?,?,?,?)",
                    arrayOf<Any?>(id, f.from, f.to, if (f.allDay) 1 else 0,
                        if (f.allDay) "" else f.lFrom, if (f.allDay) "" else f.lTo,
                        f.reporter, f.reason, f.note.trim())
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun updateAbsence(id: Long, f: Form) = exec(
        "UPDATE absences SET d_from=?,d_to=?,all_day=?,l_from=?,l_to=?,reporter=?,reason=?,note=? WHERE id=?",
        f.from, f.to, if (f.allDay) 1 else 0,
        if (f.allDay) "" else f.lFrom, if (f.allDay) "" else f.lTo,
        f.reporter, f.reason, f.note.trim(), id
    )

    fun deleteAbsence(id: Long) = exec("DELETE FROM absences WHERE id=?", id)
    fun deleteAllAbsences() = exec("DELETE FROM absences")
}
