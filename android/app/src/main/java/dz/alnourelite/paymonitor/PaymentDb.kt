package dz.alnourelite.paymonitor

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Pending(val id:Long,val eventId:String,val fingerprint:String,val amount:Long,val receivedAt:String)
class PaymentDb(ctx:Context):SQLiteOpenHelper(ctx,"payments.db",null,1){
 override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE queue(id INTEGER PRIMARY KEY AUTOINCREMENT,event_id TEXT NOT NULL UNIQUE,fingerprint TEXT NOT NULL UNIQUE,amount INTEGER NOT NULL,received_at TEXT NOT NULL)")}
 override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int){}
 fun add(eventId:String,fingerprint:String,amount:Long,time:String){val v=ContentValues().apply {put("event_id",eventId);put("fingerprint",fingerprint);put("amount",amount);put("received_at",time)};writableDatabase.insertWithOnConflict("queue",null,v,SQLiteDatabase.CONFLICT_IGNORE)}
 fun pending():List<Pending>{val out=mutableListOf<Pending>();readableDatabase.rawQuery("SELECT id,event_id,fingerprint,amount,received_at FROM queue ORDER BY id ASC LIMIT 40",null).use {while(it.moveToNext())out.add(Pending(it.getLong(0),it.getString(1),it.getString(2),it.getLong(3),it.getString(4)))};return out}
 fun remove(id:Long){writableDatabase.delete("queue","id=?",arrayOf(id.toString()))}
 fun count():Long=readableDatabase.rawQuery("SELECT COUNT(*) FROM queue",null).use{it.moveToFirst();it.getLong(0)}
}
