package ru.eyeone.relaybridge;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
final class QueueDb extends SQLiteOpenHelper {
    private static QueueDb instance;
    private volatile long revision;
    long revision(){return revision;}
    static synchronized QueueDb get(Context c) { if(instance==null) instance=new QueueDb(c.getApplicationContext()); return instance; }
    private QueueDb(Context c) { super(c,"outbox.db",null,1); }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT, dedup TEXT UNIQUE, created INTEGER, kind TEXT, payload TEXT, route TEXT, tg INTEGER, mail INTEGER, pos INTEGER DEFAULT 0, error TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE seen(k TEXT PRIMARY KEY, fingerprint TEXT, ts INTEGER)");
    }
    public void onUpgrade(SQLiteDatabase db,int old,int n) { throw new IllegalStateException("Unsupported schema"); }
    synchronized void prune() { if(getWritableDatabase().delete("events","created < ?",new String[]{String.valueOf(System.currentTimeMillis()-7L*86400000)})>0)revision++; }
    synchronized long add(String key,String kind,String body,Config cfg) throws Exception {
        SQLiteDatabase db=getWritableDatabase();
        db.delete("events","created < ?",new String[]{String.valueOf(System.currentTimeMillis()-7L*86400000)});
        ContentValues v=new ContentValues(); v.put("dedup",key);v.put("created",System.currentTimeMillis());v.put("kind",kind);
        v.put("payload",Crypto.seal(body));v.put("route",Crypto.seal(cfg.deliveryJson()));v.put("tg",cfg.telegram?0:2);v.put("mail",cfg.email?0:2);
        long id=db.insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE);
        // Retention cap; oldest rows are dropped, preventing unbounded private-data storage.
        db.execSQL("DELETE FROM events WHERE id NOT IN (SELECT id FROM events ORDER BY id DESC LIMIT 500)");
        revision++;return id;
    }
    synchronized boolean seen(String key,String fingerprint) {
        SQLiteDatabase db=getWritableDatabase();
        try(Cursor c=db.query("seen",new String[]{"fingerprint"},"k=?",new String[]{key},null,null,null)) {
            if(c.moveToFirst() && fingerprint.equals(c.getString(0))) return true;
        }
        ContentValues v=new ContentValues(); v.put("k",key);v.put("fingerprint",fingerprint);v.put("ts",System.currentTimeMillis());
        db.insertWithOnConflict("seen",null,v,SQLiteDatabase.CONFLICT_REPLACE);
        db.execSQL("DELETE FROM seen WHERE k NOT IN (SELECT k FROM seen ORDER BY ts DESC LIMIT 500)");
        return false;
    }
    synchronized void forget(String key) { getWritableDatabase().delete("seen","k=?",new String[]{key}); }
    synchronized Event event(long id) throws Exception {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT payload,tg,mail,pos,kind,route,created FROM events WHERE id=?",new String[]{""+id})) {
            if(!c.moveToFirst() || (c.getInt(1)!=0 && c.getInt(2)!=0)) return null;
            if(c.getLong(6)<System.currentTimeMillis()-7L*86400000){getWritableDatabase().delete("events","id=?",new String[]{""+id});return null;}
            return new Event(Crypto.open(c.getString(0)),c.getInt(1),c.getInt(2),c.getInt(3),c.getString(4),Config.deliveryConfig(Crypto.open(c.getString(5))));
        }
    }
    synchronized void update(long id,String col,int value) { ContentValues v=new ContentValues();v.put(col,value);getWritableDatabase().update("events",v,"id=?",new String[]{""+id});revision++; }
    synchronized void error(long id,String msg) { ContentValues v=new ContentValues();v.put("error",msg);getWritableDatabase().update("events",v,"id=?",new String[]{""+id});revision++; }
    synchronized void redactDone(long id) { getWritableDatabase().execSQL("UPDATE events SET route='' WHERE id=? AND tg<>0 AND mail<>0",new Object[]{id});revision++; }
    synchronized boolean exists(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT 1 FROM events WHERE id=?",new String[]{""+id})) { return c.moveToFirst(); }
    }
    synchronized void clear() { getWritableDatabase().delete("events",null,null);getWritableDatabase().delete("seen",null,null);revision++; }
    synchronized long[] pending() {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id FROM events WHERE (tg=0 OR mail=0) AND created>=? ORDER BY id",new String[]{""+(System.currentTimeMillis()-7L*86400000)})) {
            long[] ids=new long[c.getCount()];int i=0;while(c.moveToNext()) ids[i++]=c.getLong(0);return ids;
        }
    }
    synchronized java.util.List<HistoryRow> history() {
        prune();java.util.List<HistoryRow> rows=new java.util.ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,created,kind,payload,tg,mail,error FROM events ORDER BY id DESC LIMIT 500",null)) {
            while(c.moveToNext()) {
                String body="Содержимое удалено предыдущей версией";
                if(!c.getString(3).isEmpty())try{body=Crypto.open(c.getString(3));}catch(Exception ignored){body="Содержимое недоступно";}
                rows.add(new HistoryRow(c.getLong(0),c.getLong(1),c.getString(2),body,c.getInt(4),c.getInt(5),c.getString(6)));
            }
        }return rows;
    }
    synchronized void deleteEvent(long id){getWritableDatabase().delete("events","id=?",new String[]{""+id});revision++;}
    synchronized boolean retryFailed(long id,Config cfg) throws Exception {
        cfg.validate();if(!cfg.enabled)throw new IllegalArgumentException("Сначала включите пересылку");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT tg,mail,payload FROM events WHERE id=?",new String[]{""+id})) {
            if(!c.moveToFirst()||c.getString(2).isEmpty())return false;
            // Never replace credentials of an in-flight delivery or repeat a successful channel.
            if(c.getInt(0)==0||c.getInt(1)==0)throw new IllegalArgumentException("Дождитесь завершения текущей доставки");
            ContentValues v=new ContentValues();boolean retry=false;
            if(c.getInt(0)==3&&cfg.telegram){v.put("tg",0);v.put("pos",0);retry=true;}
            if(c.getInt(1)==3&&cfg.email){v.put("mail",0);retry=true;}
            if(!retry)return false;
            v.put("route",Crypto.seal(cfg.deliveryJson()));v.put("error","");
            getWritableDatabase().update("events",v,"id=?",new String[]{""+id});revision++;return true;
        }
    }
    static final class HistoryRow {
        final long id,created;final String kind,body,error;final int tg,mail;
        HistoryRow(long i,long c,String k,String b,int t,int m,String e){id=i;created=c;kind=k;body=b;tg=t;mail=m;error=e;}
    }
    synchronized String status() {
        prune();
        StringBuilder s=new StringBuilder("Последние события (без содержимого)\n");
        try(Cursor c=getReadableDatabase().rawQuery("SELECT created,kind,tg,mail,error FROM events ORDER BY id DESC LIMIT 15",null)) {
            while(c.moveToNext()) s.append(android.text.format.DateFormat.format("dd.MM HH:mm:ss",c.getLong(0))).append(" · ").append(c.getString(1))
                .append("\nTelegram: ").append(label(c.getInt(2))).append(" · Email: ").append(label(c.getInt(3))).append(c.getString(4).isEmpty()?"":"\n"+c.getString(4)).append("\n\n");
        }
        return s.toString();
    }
    static String label(int v) { return v==0?"в очереди":v==1?"доставлено":v==2?"выключено":"ошибка"; }
    static final class Event { final String body,kind;final int tg,mail,pos;final Config route;Event(String b,int t,int m,int p,String k,Config r){body=b;tg=t;mail=m;pos=p;kind=k;route=r;} }
}
