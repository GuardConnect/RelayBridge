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
    private int selectedFilter=0,typeFilter=-1;
    private final TextView[] metricCaptions=new TextView[4];
    private final LinearLayout[] metricViews=new LinearLayout[4];
    private final Button[] typeButtons=new Button[3];
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private List<QueueDb.HistoryRow> rows=Collections.emptyList();
    private boolean loading;
    private int limit=50;
    private String signature="";
    private long loadedRevision=-1,loadedAt;
    HistoryView(Activity c){
        super(c);activity=c;setOrientation(VERTICAL);
        summary=text("Загрузка журнала…",12);summary.setTextColor(UiColors.MUTED);
        String[] labels={"Все","Доставлено","В очереди","Ошибки"};
        LinearLayout metrics=new LinearLayout(c);metrics.setPadding(dp(4),dp(4),dp(4),dp(4));metrics.setBackground(background(UiColors.SURFACE,UiColors.BORDER));addView(metrics,new LayoutParams(-1,-2));
        for(int i=0;i<4;i++){final int index=i;LinearLayout metric=new LinearLayout(c);metricViews[i]=metric;metric.setOrientation(VERTICAL);metric.setGravity(Gravity.CENTER);metric.setPadding(dp(2),dp(8),dp(2),dp(8));metrics.addView(metric,new LayoutParams(0,-2,1));counters[i]=text("0",22);counters[i].setTypeface(null,Typeface.BOLD);metric.addView(counters[i]);metricCaptions[i]=text(labels[i],10);metric.addView(metricCaptions[i]);metric.setFocusable(true);metric.setOnClickListener(v->{selectedFilter=index;limit=50;render();});}
        search=new EditText(c);search.setHint("Поиск по событиям");search.setSingleLine();search.setTextSize(14);search.setTextColor(UiColors.TEXT);search.setHintTextColor(UiColors.MUTED);search.setPadding(dp(12),0,dp(12),0);search.setBackground(background(UiColors.FIELD,UiColors.BORDER));LayoutParams searchLp=new LayoutParams(-1,dp(48));searchLp.topMargin=dp(10);addView(search,searchLp);
        LinearLayout types=new LinearLayout(c);LayoutParams typeLp=new LayoutParams(-1,-2);typeLp.topMargin=dp(10);typeLp.bottomMargin=dp(12);addView(types,typeLp);String[] names={"SMS","Звонки","Уведомления"};
        for(int i=0;i<3;i++){final int index=i;Button button=new Button(c);typeButtons[i]=button;button.setText(names[i]);button.setAllCaps(false);button.setTextSize(11);button.setMinimumWidth(0);button.setMinWidth(0);button.setPadding(dp(4),0,dp(4),0);LayoutParams lp=new LayoutParams(0,dp(44),1);if(i>0)lp.leftMargin=dp(6);types.addView(button,lp);button.setOnClickListener(v->{typeFilter=typeFilter==index?-1:index;limit=50;render();});}
        summary.setVisibility(GONE);addView(summary);
        cards=new LinearLayout(c);cards.setOrientation(VERTICAL);addView(cards);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int n,int a){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int b,int n){render();}});

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
        for(int i=0;i<4;i++){boolean on=i==selectedFilter;metricViews[i].setBackground(background(on?UiColors.TEXT:UiColors.SURFACE,on?UiColors.TEXT:UiColors.SURFACE));counters[i].setTextColor(on?UiColors.BACKGROUND:UiColors.TEXT);metricCaptions[i].setTextColor(on?UiColors.BACKGROUND:UiColors.MUTED);}
        for(int i=0;i<3;i++){typeButtons[i].setBackground(background(typeFilter==i?UiColors.TEXT:UiColors.SUCCESS_BG,UiColors.BORDER));typeButtons[i].setTextColor(typeFilter==i?UiColors.BACKGROUND:UiColors.TEXT);}
        cards.setBackground(background(UiColors.SURFACE,UiColors.BORDER));
        cards.removeAllViews();String q=search.getText().toString().toLowerCase(Locale.ROOT);int selected=selectedFilter,shown=0;
        for(var r:rows){boolean done=(r.tg==1||r.mail==1)&&r.tg!=0&&r.mail!=0&&r.tg!=3&&r.mail!=3;
            if(selected==1&&!done||selected==2&&r.tg!=0&&r.mail!=0||selected==3&&r.tg!=3&&r.mail!=3)continue;
            int kind=r.kind.contains("SMS")?0:r.kind.contains("звонок")?1:2;if(typeFilter>=0&&kind!=typeFilter)continue;
            if(!(r.kind+" "+r.body).toLowerCase(Locale.ROOT).contains(q))continue;
            shown++;if(shown>limit)continue;LinearLayout card=new LinearLayout(activity);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(14),dp(14),dp(14),dp(14));
            if(cards.getChildCount()>0){View line=new View(activity);line.setBackgroundColor(UiColors.BORDER);cards.addView(line,new LayoutParams(-1,dp(1)));}cards.addView(card,new LayoutParams(-1,-2));
            ImageView icon=new ImageView(activity);icon.setImageDrawable(new UiIcon(kind==0?4:kind==1?5:6,UiColors.TEXT,getResources().getDisplayMetrics().density));icon.setPadding(dp(10),dp(10),dp(10),dp(10));icon.setBackground(background(UiColors.SUCCESS_BG,UiColors.SUCCESS_BG));card.addView(icon,new LayoutParams(dp(40),dp(40)));
            LinearLayout words=new LinearLayout(activity);words.setOrientation(VERTICAL);LayoutParams wordLp=new LayoutParams(0,-2,1);wordLp.leftMargin=dp(12);wordLp.rightMargin=dp(6);card.addView(words,wordLp);
            TextView heading=text(displayTitle(r),14);heading.setTypeface(null,Typeface.BOLD);heading.setMaxLines(1);heading.setEllipsize(TextUtils.TruncateAt.END);words.addView(heading);
            boolean failedRow=r.tg==3||r.mail==3,pendingRow=r.tg==0||r.mail==0;String state=failedRow?"! Ошибка":pendingRow?"◷ В очереди":done?"✓ Доставлено":"Выключено";
            String channels=(r.tg!=2?"Telegram":"")+(r.tg!=2&&r.mail!=2?", ":"")+(r.mail!=2?"Email":"");TextView caption=text(state+(channels.isEmpty()?"":" · "+channels),12);caption.setTextColor(done?UiColors.SUCCESS:UiColors.MUTED);caption.setMaxLines(2);caption.setEllipsize(TextUtils.TruncateAt.END);words.addView(caption);
            TextView time=text(android.text.format.DateFormat.format("HH:mm\ndd.MM",r.created).toString(),11);time.setTypeface(Typeface.MONOSPACE);time.setGravity(Gravity.END);time.setTextColor(UiColors.MUTED);card.addView(time);
            card.setFocusable(true);card.setContentDescription(r.kind+", Telegram "+QueueDb.label(r.tg)+", Email "+QueueDb.label(r.mail)+". Открыть подробности");card.setOnClickListener(v->details(r));
        }
        if(shown>limit){Button more=new Button(activity);more.setText("Показать ещё 50");more.setAllCaps(false);more.setTextColor(UiColors.MUTED);more.setBackgroundColor(UiColors.BACKGROUND);more.setOnClickListener(v->{limit+=50;render();});cards.addView(more);}
        if(shown==0)cards.addView(text(rows.isEmpty()?"Здесь появятся новые события после включения пересылки.":"По выбранному фильтру событий нет.",14));
    }
    static String displayTitle(QueueDb.HistoryRow r){
        if(!r.body.startsWith("RelayBridge · "))return r.kind;int start=r.body.indexOf("\n\n");if(start<0)return r.kind;String data=r.body.substring(start+2);
        if(r.kind.equals("SMS")){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?m)^От: ([^\n]+)").matcher(data);if(m.find())return "SMS от "+m.group(1);}
        if(r.kind.equals("Уведомление")){String[] lines=data.split("\n",3);if(lines.length>=2){String app=lines[0];int pkg=app.indexOf(" (");if(pkg>0)app=app.substring(0,pkg);return app+" · "+lines[1];}}
        return r.kind;
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
