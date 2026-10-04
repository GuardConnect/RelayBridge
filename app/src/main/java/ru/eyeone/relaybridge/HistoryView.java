package ru.eyeone.relaybridge;
import android.app.*;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
/** Bounded encrypted history, loaded off the UI thread. */
final class HistoryView extends LinearLayout {
    private final Activity activity;
    private final TextView summary;
    private final LinearLayout cards;
    private final TextView[] counters=new TextView[4];
    private final EditText search;
    private final Spinner filter;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private List<QueueDb.HistoryRow> rows=Collections.emptyList();
    private boolean loading;
    private int limit=50;
    private String signature="";
    private long loadedRevision=-1,loadedAt;
    HistoryView(Activity c){
        super(c);activity=c;setOrientation(VERTICAL);
        summary=text("Загрузка журнала…",12);summary.setTextColor(UiColors.MUTED);
        String[] labels={"Всего событий","Доставлено","В очереди","С ошибками"};int[] colors={UiColors.TEXT,UiColors.SUCCESS,UiColors.PENDING,UiColors.ERROR};int[] backgrounds={UiColors.FIELD,UiColors.SUCCESS_BG,UiColors.PENDING_BG,UiColors.ERROR_BG};
        for(int row=0;row<2;row++){LinearLayout line=new LinearLayout(c);LayoutParams lineParams=new LayoutParams(-1,-2);lineParams.bottomMargin=dp(8);addView(line,lineParams);
            for(int col=0;col<2;col++){int i=row*2+col;LinearLayout metric=new LinearLayout(c);metric.setOrientation(VERTICAL);metric.setPadding(dp(14),dp(12),dp(14),dp(12));metric.setBackground(background(backgrounds[i],UiColors.BORDER));LayoutParams params=new LayoutParams(0,-2,1);if(col>0)params.leftMargin=dp(8);line.addView(metric,params);
                counters[i]=text("0",28);counters[i].setTypeface(null,Typeface.BOLD);counters[i].setTextColor(colors[i]);metric.addView(counters[i]);TextView caption=text(labels[i],12);caption.setTextColor(UiColors.MUTED);metric.addView(caption);
            }
        }addView(summary);
        search=new EditText(c);search.setHint("Поиск по типу и содержимому");search.setSingleLine();search.setTextColor(UiColors.TEXT);search.setHintTextColor(UiColors.MUTED);search.setBackgroundTintList(android.content.res.ColorStateList.valueOf(UiColors.MUTED));addView(search);
        filter=new Spinner(c);filter.setAdapter(new ArrayAdapter<>(c,android.R.layout.simple_spinner_dropdown_item,new String[]{"Все события","Доставлено","Ожидает отправки","Ошибки"}));addView(filter);
        cards=new LinearLayout(c);cards.setOrientation(VERTICAL);addView(cards);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int n,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int n){render();}});
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int position,long id){render();}});
    }
    void refresh(){
        if(loading||activity.isDestroyed()||executor.isShutdown())return;
        long revision=QueueDb.get(activity).revision();if(revision==loadedRevision&&android.os.SystemClock.elapsedRealtime()-loadedAt<60000)return;loading=true;
        executor.execute(()->{
            try{List<QueueDb.HistoryRow> result=QueueDb.get(activity).history();
                StringBuilder key=new StringBuilder();for(var r:result)key.append(r.id).append(':').append(r.tg).append(':').append(r.mail).append(r.error).append(';');
                activity.runOnUiThread(()->{loading=false;loadedRevision=revision;loadedAt=android.os.SystemClock.elapsedRealtime();if(activity.isDestroyed())return;if(!signature.equals(key.toString())){signature=key.toString();rows=result;render();}else if(result.isEmpty())render();});
            }catch(Exception e){activity.runOnUiThread(()->{loading=false;summary.setText("Не удалось прочитать журнал");});}
        });
    }
    private void render(){
        int delivered=0,pending=0,failed=0;
        for(var r:rows){if(r.tg==0||r.mail==0)pending++;if(r.tg==3||r.mail==3)failed++;if((r.tg==1||r.mail==1)&&r.tg!=0&&r.mail!=0&&r.tg!=3&&r.mail!=3)delivered++;}
        int[] counts={rows.size(),delivered,pending,failed};for(int i=0;i<counts.length;i++)counters[i].setText(String.valueOf(counts[i]));
        summary.setText("События в сохранённом журнале · статусы отправки");
        cards.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT);int selected=filter.getSelectedItemPosition(),shown=0;
        for(var r:rows){boolean done=(r.tg==1||r.mail==1)&&r.tg!=0&&r.mail!=0&&r.tg!=3&&r.mail!=3;
            if(selected==1&&!done||selected==2&&r.tg!=0&&r.mail!=0||selected==3&&r.tg!=3&&r.mail!=3)continue;
            if(!(r.kind+" "+r.body).toLowerCase(Locale.ROOT).contains(q))continue;
            shown++;if(shown>limit)continue;LinearLayout card=new LinearLayout(activity);card.setOrientation(VERTICAL);card.setPadding(dp(12),dp(12),dp(12),dp(12));
            GradientDrawable bg=new GradientDrawable();bg.setColor(UiColors.FIELD);bg.setCornerRadius(dp(12));bg.setStroke(dp(1),UiColors.BORDER);card.setBackground(bg);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);cards.addView(card,lp);
            TextView heading=text(r.kind,15);heading.setTypeface(null,Typeface.BOLD);card.addView(heading);
            TextView time=text(android.text.format.DateFormat.format("dd.MM.yyyy · HH:mm",r.created).toString(),11);time.setTextColor(UiColors.MUTED);card.addView(time);
            int start=r.body.startsWith("RelayBridge · ")?r.body.indexOf("\n\n"):-1;
            TextView preview=text(start>=0?r.body.substring(start+2):r.body,14);preview.setMaxLines(3);preview.setEllipsize(TextUtils.TruncateAt.END);card.addView(preview);
            LinearLayout badges=new LinearLayout(activity);badges.setOrientation(VERTICAL);card.addView(badges);
            badges.addView(badge("Telegram",r.tg));badges.addView(badge("Email",r.mail));
            if(!r.error.isEmpty()){TextView error=text(r.error,12);error.setTextColor(UiColors.ERROR);error.setMaxLines(2);error.setEllipsize(TextUtils.TruncateAt.END);card.addView(error);}
            card.setFocusable(true);card.setContentDescription(r.kind+", Telegram "+QueueDb.label(r.tg)+", Email "+QueueDb.label(r.mail)+". Открыть подробности");card.setOnClickListener(v->details(r));
        }
        if(shown>limit){Button more=new Button(activity);more.setText("Показать ещё 50");more.setAllCaps(false);more.setOnClickListener(v->{limit+=50;render();});cards.addView(more);}
        if(shown==0)cards.addView(text(rows.isEmpty()?"Здесь появятся новые события после включения пересылки.":"По выбранному фильтру событий нет.",14));
    }
    private void details(QueueDb.HistoryRow r){
        TextView content=text(r.body+"\n\nTelegram: "+mark(r.tg)+"\nEmail: "+mark(r.mail)+"\n"+r.error,14);content.setTextIsSelectable(true);content.setPadding(dp(18),dp(8),dp(18),dp(8));
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        AlertDialog.Builder dialog=new AlertDialog.Builder(activity).setTitle(r.kind).setView(scroll).setPositiveButton("Закрыть",null)
            .setNegativeButton("Удалить",(d,w)->new AlertDialog.Builder(activity).setMessage("Удалить событие и отменить оставшуюся доставку? Уже переданные данные останутся у получателей.").setPositiveButton("Удалить",(a,b)->{androidx.work.WorkManager.getInstance(activity).cancelUniqueWork("event-"+r.id);QueueDb.get(activity).deleteEvent(r.id);refresh();}).setNegativeButton("Отмена",null).show());
        if(r.tg==3||r.mail==3)dialog.setNeutralButton("Повторить",(d,w)->new AlertDialog.Builder(activity).setMessage("Повторить отправку только с ошибками, используя сохранённые сейчас настройки? После сетевого сбоя возможен дубликат у получателя.").setPositiveButton("Повторить",(a,b)->{
            try{if(QueueDb.get(activity).retryFailed(r.id,Config.load(activity))){Events.retry(activity,r.id);refresh();}else Toast.makeText(activity,"Нет доступного способа отправки с ошибкой или содержимое удалено",Toast.LENGTH_LONG).show();}
            catch(Exception e){Toast.makeText(activity,e instanceof IllegalArgumentException?e.getMessage():"Не удалось повторить событие",Toast.LENGTH_LONG).show();}
        }).setNegativeButton("Отмена",null).show());
        dialog.show();
    }
    private GradientDrawable background(int fill,int border){GradientDrawable shape=new GradientDrawable();shape.setColor(fill);shape.setCornerRadius(dp(12));shape.setStroke(dp(1),border);return shape;}
    private TextView badge(String channel,int state){TextView t=text(channel+"  ·  "+mark(state),12);int color=state==1?UiColors.SUCCESS:state==0?UiColors.PENDING:state==3?UiColors.ERROR:UiColors.MUTED;int fill=state==1?UiColors.SUCCESS_BG:state==0?UiColors.PENDING_BG:state==3?UiColors.ERROR_BG:UiColors.SURFACE;
        t.setTextColor(color);t.setPadding(dp(10),dp(7),dp(10),dp(7));t.setBackground(background(fill,fill));LayoutParams lp=new LayoutParams(-1,-2);lp.topMargin=dp(6);t.setLayoutParams(lp);return t;}
    private String mark(int s){return (s==1?"✓ ":s==0?"◷ ":s==3?"! ":"— ")+QueueDb.label(s);}
    private TextView text(String s,int size){TextView t=new TextView(activity);t.setText(s);t.setTextColor(UiColors.TEXT);t.setTextSize(size);t.setPadding(0,dp(4),0,dp(4));return t;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDetachedFromWindow(){super.onDetachedFromWindow();executor.shutdown();}
}
