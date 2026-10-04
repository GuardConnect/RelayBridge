package ru.eyeone.relaybridge;
import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.graphics.Color;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;
import java.util.UUID;
/** Run only on a clean development emulator: these checks reset test-app data. */
public class SmokeInstrumentation extends Instrumentation {
    private final StringBuilder report=new StringBuilder();
    private int passed;
    private void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private void pass(String name) {passed++;report.append("PASS: ").append(name).append('\n');}
    @Override public void onCreate(Bundle arguments) {super.onCreate(arguments);start();}
    @Override public void onStart() {
        Context c=getTargetContext();Config original=null;Activity activity=null;
        try {
            original=Config.load(c);Config test=new Config();test.callAllSims=false;test.callSubscriptions.add(99);test.smsAllSims=false;test.smsSubscriptions.add(4);test.save(c);
            Config simConfig=Config.load(c);check(!simConfig.callAllSims&&simConfig.callSubscriptions.equals(java.util.Set.of(99))&&!simConfig.smsAllSims&&simConfig.smsSubscriptions.equals(java.util.Set.of(4)),"Independent SIM filters did not persist");pass("Independent encrypted call/SMS SIM configuration round trip");
            Config.prefs(c).edit().putBoolean("intro",true).commit();Events.clear(c);
            activity=startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            check(activity instanceof MainActivity,"MainActivity did not launch");
            Activity screen=activity;
            runOnMainSync(()->{
                EditText token=screen.findViewById(2000);
                check(token!=null,"Telegram field missing");
                check(findText(screen.getWindow().getDecorView(),"v"+BuildConfig.VERSION_NAME)!=null,"Header version differs from installed package");
                try{check(c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionName.equals(BuildConfig.VERSION_NAME),"BuildConfig version differs from package version");}catch(android.content.pm.PackageManager.NameNotFoundException e){throw new AssertionError(e);}
                check(token.getCurrentTextColor()==UiColors.TEXT,"Dark theme field contrast missing");
            });pass("MainActivity launches with dark theme fields");
            runOnMainSync(()->{
                ((EditText)screen.findViewById(2002)).setText("smtp.example.com");
                ((EditText)screen.findViewById(2004)).setText("some.sender@example.com");
                ((EditText)screen.findViewById(2005)).setText("abcd\u00a0efgh\u202fijkl\nmnop");
                ((EditText)screen.findViewById(2006)).setText("");
                ((EditText)screen.findViewById(2007)).setText("receiver@example.com");
                View emailLabel=findText(screen.getWindow().getDecorView(),"Email / SMTP");CompoundButton email=emailLabel==null?null:findToggle((View)emailLabel.getParent());
                check(email!=null,"Email toggle missing");email.setChecked(true);
                View save=findText(screen.getWindow().getDecorView(),"Сохранить настройки");
                check(save!=null,"Save button missing");save.performClick();
            });
            Config saved=Config.load(c);
            check(saved.email && saved.from.equals("some.sender@example.com"),"Valid SMTP fields were not saved");
            check(saved.password.equals("abcd\u00a0efgh\u202fijkl\nmnop"),"SMTP password was modified");
            check(!saved.enabled,"Saving settings accidentally enabled forwarding");
            check(saved.callSubscriptions.equals(java.util.Set.of(99))&&saved.smsSubscriptions.equals(java.util.Set.of(4)),"GUI save lost SIM selections");
            pass("GUI saves valid SMTP settings, fills From, preserves password exactly");
            String raw=Config.prefs(c).getString("config","");
            check(!raw.contains("some.sender")&&!raw.contains("abcdefghijklmnop"),"Settings contain plaintext credentials");
            pass("Keystore-encrypted configuration round trip");
            Config route=new Config();route.telegram=true;route.email=true;route.token="123456:TEST_only";
            route.chat="-100123";route.user="some.sender@example.com";route.password="test";route.from=route.user;route.to="receiver@example.com";
            QueueDb db=QueueDb.get(c);String key="instrumentation-"+UUID.randomUUID();
            long id=db.add(key,"Тест очереди","Synthetic private test body",route);
            check(id>0 && db.add(key,"Тест очереди","Synthetic private test body",route)==-1,"Duplicate event inserted");
            QueueDb.Event event=db.event(id);check(event!=null&&event.body.equals("Synthetic private test body"),"Encrypted queue cannot be read");
            check(event.route.chat.equals("-100123")&&event.tg==0&&event.mail==0,"Delivery snapshot incorrect");
            db.update(id,"tg",1);event=db.event(id);check(event.tg==1&&event.mail==0,"Channels do not have independent statuses");
            db.update(id,"mail",1);db.redactDone(id);check(db.event(id)==null,"Completed event remains pending");
            pass("Encrypted queue, deduplication, destination snapshot, independent channel statuses");
            runOnMainSync(()->{
                check(findText(screen.getWindow().getDecorView(),"Отправить тест")!=null,"Telegram test button missing");
                check(findText(screen.getWindow().getDecorView(),"Тестовое письмо")!=null,"SMTP test button missing");
                check(findText(screen.getWindow().getDecorView(),"Карточка приложения")!=null,"Restricted-settings link missing");
            });pass("Independent Telegram/SMTP test controls and restricted-settings help exist");
            runOnMainSync(()->{
                View choice=findPrefix(screen.getWindow().getDecorView(),"Приложения ·");
                check(choice!=null,"Application selector missing");choice.performClick();
            });
            final boolean[] selector={false};
            for(int attempt=0;attempt<120;attempt++){
                Thread.sleep(500);runOnMainSync(()->{AlertDialog dialog=((MainActivity)screen).appDialog;selector[0]=dialog!=null&&dialog.isShowing()&&dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null;});
                if(selector[0])break;
            }
            check(selector[0],"Application selector did not load");
            pass("Application selector dialog loads");
            java.util.List<QueueDb.HistoryRow> history=db.history();
            check(history.size()==1&&history.get(0).body.equals("Synthetic private test body")&&history.get(0).tg==1&&history.get(0).mail==1,"Delivered history lost encrypted content/status");
            saved.enabled=true;check(!db.retryFailed(id,saved),"Successful channels were repeated");
            try(android.database.Cursor record=db.getReadableDatabase().rawQuery("SELECT payload,route FROM events WHERE id=?",new String[]{""+id})){
                check(record.moveToFirst()&&!record.getString(0).contains("Synthetic")&&record.getString(1).isEmpty(),"History encryption or credential cleanup failed");
            }
            db.update(id,"mail",3);check(db.retryFailed(id,saved),"Failed email could not be retried with current credentials");
            QueueDb.Event retry=db.event(id);check(retry.tg==1&&retry.mail==0&&retry.route.user.equals(saved.user),"Retry changed successful channel or used stale credentials");
            db.deleteEvent(id);check(db.history().isEmpty(),"History deletion failed");
            pass("Delivered history retains encrypted content, independent status and deletion");
            saved.enabled=true;saved.save(c);
            runOnMainSync(()->{
                try{java.lang.reflect.Method refresh=MainActivity.class.getDeclaredMethod("updateStatus");refresh.setAccessible(true);refresh.invoke(screen);java.lang.reflect.Field field=MainActivity.class.getDeclaredField("relayToggle");field.setAccessible(true);Switch toggle=(Switch)field.get(screen);check(toggle.isChecked(),"Enabled state was not reflected by relay switch");toggle.setChecked(false);check(!toggle.isChecked(),"Relay switch failed to stop");}catch(ReflectiveOperationException e){throw new AssertionError(e);}
                check(findText(screen.getWindow().getDecorView(),"О фоновой работе")==null,"Background help button was not removed");
            });check(!Config.load(c).enabled,"Switch did not persist the stopped state");pass("Main relay switch reflects saved state and persists stopping");
            runOnMainSync(()->{
                ((MainActivity)screen).appDialog.dismiss();
                check(findText(screen.getWindow().getDecorView(),"Войти в Google")==null,"Removed sign-in control remains");
                check(findText(screen.getWindow().getDecorView(),"SMTP сервер")!=null,"SMTP server field missing");
                navigate(screen,0);
            });
            Thread.sleep(800);runOnMainSync(()->checkGrid(screen.getWindow().getDecorView()));capture(screen,"overview.png");
            runOnMainSync(()->{navigate(screen,1);View smsLabel=findText(screen.getWindow().getDecorView(),"Входящие SMS");findToggle((View)smsLabel.getParent()).setChecked(true);});Thread.sleep(500);runOnMainSync(()->checkGrid(screen.getWindow().getDecorView()));capture(screen,"channels.png");
            runOnMainSync(()->navigate(screen,2));Thread.sleep(500);runOnMainSync(()->checkGrid(screen.getWindow().getDecorView()));capture(screen,"access.png");
            runOnMainSync(()->navigate(screen,3));Thread.sleep(1000);capture(screen,"history-empty.png");
            long mock=db.add("visual-notification","Уведомление","RelayBridge · Уведомление\n2026-10-04 07:00\n\nSignal (org.thoughtcrime.securesms)\nТест интерфейса\nСообщение передано в выбранные каналы.",route);db.update(mock,"tg",1);db.update(mock,"mail",1);db.redactDone(mock);
            runOnMainSync(()->navigate(screen,3));Thread.sleep(1000);runOnMainSync(()->checkGrid(screen.getWindow().getDecorView()));capture(screen,"history.png");
            pass("Ordinary SMTP controls, dark theme navigation and synthetic UI captures");
            report.append("Completed: ").append(passed).append(" runtime checks. No real network messages sent.\n");
            Bundle result=new Bundle();result.putString("stream",report.toString());result.putInt("passed",passed);finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            Bundle result=new Bundle();result.putString("stream",report+"FAIL: "+error.getClass().getSimpleName()+": "+error.getMessage()+"\n");finish(Activity.RESULT_CANCELED,result);
        } finally {
            try {Events.clear(c);if(original!=null)original.save(c);if(activity!=null){Activity a=activity;runOnMainSync(a::finish);}}catch(Exception ignored){}
        }
    }
    private void navigate(Activity screen,int index){
        try{java.lang.reflect.Field field=MainActivity.class.getDeclaredField("navigation");field.setAccessible(true);ViewGroup nav=(ViewGroup)field.get(screen);check(nav.getChildAt(index).performClick(),"Navigation click failed");}catch(ReflectiveOperationException e){throw new AssertionError(e);}
    }
    private void capture(Activity screen,String name)throws Exception{
        android.graphics.Bitmap[] rendered=new android.graphics.Bitmap[1];
        runOnMainSync(()->{View root=screen.findViewById(android.R.id.content);check(root.getWidth()>0&&root.getHeight()>0,"UI has no size");rendered[0]=android.graphics.Bitmap.createBitmap(root.getWidth(),root.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(rendered[0]));});
        android.graphics.Bitmap bitmap=rendered[0];
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(screen.getFilesDir(),name))){bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
    }
    private void checkGrid(View view){
        if(view.getVisibility()!=View.VISIBLE||view.getWidth()==0)return;
        if(view instanceof Switch){ViewGroup row=(ViewGroup)view.getParent();check(view.getTop()>=0&&view.getBottom()<=row.getHeight(),"Switch clipped vertically");for(int i=0;i<row.getChildCount();i++){View sibling=row.getChildAt(i);if(sibling instanceof TextView&&sibling!=view)check(sibling.getRight()<=view.getLeft(),"Switch overlaps its label");}}
        if(view instanceof LinearLayout){LinearLayout row=(LinearLayout)view;if(row.getOrientation()==LinearLayout.VERTICAL&&row.getChildCount()==2&&row.getChildAt(0) instanceof ImageView&&row.getChildAt(1) instanceof TextView){View icon=row.getChildAt(0),label=row.getChildAt(1);check(Math.abs((icon.getLeft()+icon.getRight())-(label.getLeft()+label.getRight()))<=2,"Navigation icon and label are not centered");}}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)checkGrid(((ViewGroup)view).getChildAt(i));
    }
    private CompoundButton findToggle(View v){if(v instanceof CompoundButton)return (CompoundButton)v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){CompoundButton found=findToggle(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private View findText(View v,String text) {
        if(v instanceof TextView && text.contentEquals(((TextView)v).getText()))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findText(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private View findPrefix(View v,String text) {
        if(v instanceof TextView && ((TextView)v).getText().toString().startsWith(text))return v;
        if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View found=findPrefix(((ViewGroup)v).getChildAt(i),text);if(found!=null)return found;}
        return null;
    }
    private boolean hasText(AccessibilityNodeInfo node,String value){
        if(node.getText()!=null&&value.equalsIgnoreCase(node.getText().toString()))return true;
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null&&hasText(child,value))return true;}
        return false;
    }
    private String labels(AccessibilityNodeInfo node){
        StringBuilder text=new StringBuilder();
        if(node.getText()!=null)text.append(node.getText()).append(" | ");
        for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo child=node.getChild(i);if(child!=null)text.append(labels(child));}
        return text.toString();
    }
}
