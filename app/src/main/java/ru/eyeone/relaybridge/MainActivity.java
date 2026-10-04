package ru.eyeone.relaybridge;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.*;
import android.text.method.PasswordTransformationMethod;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Executors;

public class MainActivity extends androidx.activity.ComponentActivity {
    private final androidx.activity.result.ActivityResultLauncher<String> permissionLauncher =
        registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),this::permissionResult);
    private Config cfg;
    AlertDialog appDialog;
    private LinearLayout page, area, navigation;
    private final List<View> sections=new ArrayList<>();
    private final List<Integer> sectionTabs=new ArrayList<>();
    private int sectionTab=0,selectedTab=0;
    private HistoryView history;
    private ScrollView scroll;
    private TextView status,journal,tgResult,mailResult,permissionStatus,simSummary,callSimSummary,overviewDetails,overviewReadiness;
    private CheckBox sms,calls,pushes,ongoing,tg,email;
    private EditText token,chat,host,port,user,password,from,to,templateField;
    private Spinner tls,auth;
    private Button appsButton,tgTestButton,mailTestButton;
    private int viewId=2000;
    private int pendingPermissionRequest;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable refresh=new Runnable(){public void run(){updateStatus();handler.postDelayed(this,2500);}};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static final class Draft {Config config;String port;int scroll,tab;}

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);pendingPermissionRequest=saved==null?0:saved.getInt("permission-request",0);getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        try {cfg=Config.load(this);}catch(Exception e){new AlertDialog.Builder(this).setTitle("Настройки недоступны")
            .setMessage("Не удалось расшифровать настройки. Можно сбросить данные и настроить приложение заново.")
            .setPositiveButton("Сбросить",(d,w)->{Config.prefs(this).edit().clear().apply();Events.clear(this);recreate();})
            .setNegativeButton("Закрыть",(d,w)->finish()).setCancelable(false).show();return;}
        Draft draft=getLastCustomNonConfigurationInstance() instanceof Draft?(Draft)getLastCustomNonConfigurationInstance():null;
        if(draft!=null)cfg=draft.config;
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(UiColors.BACKGROUND);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.VERTICAL);brand.setPadding(dp(16),dp(10),dp(16),dp(8));shell.addView(brand);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(16),0,dp(16),dp(16));page.setBackgroundColor(UiColors.BACKGROUND);
        scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(shell);area=brand;
        shell.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;});
        title("RelayBridge",24);note(BuildConfig.VERSION_NAME+" · TELEGRAM / SMTP").setTextSize(10);
        navigation=new LinearLayout(this);navigation.setPadding(dp(12),dp(10),dp(12),dp(12));navigation.setBackgroundColor(UiColors.BACKGROUND);shell.addView(navigation);area=page;
        String[] tabs={"Обзор","Отправка","Доступ","Журнал"};
        for(int i=0;i<tabs.length;i++){final int tab=i;Button button=new Button(this);button.setText(tabs[i]);button.setAllCaps(false);button.setTextSize(12);button.setMinWidth(0);button.setMinimumWidth(0);button.setPadding(0,0,0,0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(46),1);if(i>0)lp.leftMargin=dp(6);navigation.addView(button,lp);button.setOnClickListener(v->selectTab(tab));}
        buildOverview();buildChannels();buildAccess();buildHistory();
        selectTab(draft==null?0:draft.tab);
        if(draft!=null){port.setText(draft.port);scroll.post(()->scroll.scrollTo(0,draft.scroll));}

    }
    private void consent(Runnable next){
        new AlertDialog.Builder(this).setTitle("Передача личных данных")
            .setMessage(Consent.DISCLOSURE).setPositiveButton("Согласен",(d,w)->{try{Config paused=Config.load(this);paused.enabled=false;paused.save(this);if(Config.prefs(this).edit().putInt("consent-version",Consent.VERSION).commit())next.run();else toast("Не удалось сохранить согласие");}catch(Exception e){toast("Не удалось сохранить согласие");}})
            .setNegativeButton("Отмена",null).show();
    }
    private void privacy(){
        TextView text=new TextView(this);text.setText(Consent.PRIVACY);text.setTextColor(UiColors.TEXT);text.setPadding(dp(16),dp(12),dp(16),dp(12));text.setTextIsSelectable(true);ScrollView view=new ScrollView(this);view.addView(text);
        new AlertDialog.Builder(this).setTitle("Конфиденциальность RelayBridge").setView(view).setPositiveButton("Закрыть",null).show();
    }
    private void buildOverview(){
        sectionTab=0;
        section("Пересылка");status=note("");overviewDetails=note("");
        button("Включить пересылку",this::activate);
        secondary("Остановить и очистить очередь",()->{try{Indicator.stop(this);cfg.enabled=false;updateStatus();toast("Пересылка остановлена");}catch(Exception e){toast("Не удалось остановить пересылку");}});
        section("Готовность");overviewReadiness=note("");
        pair(()->secondary("Настроить отправку",()->selectTab(1)),()->secondary("Разрешения",()->selectTab(2)),1,1);

    }
    private void buildChannels(){
        sectionTab=1;section("SMS");sms=check("Входящие SMS",cfg.sms);
        secondary("SIM для SMS",this::chooseSims);simSummary=note("");updateSimSummary();
        section("Звонки");calls=check("Входящий звонок и номер",cfg.calls);
        secondary("SIM для звонков",()->chooseSims(true));callSimSummary=note("");updateSimSummary();
        section("Уведомления приложений");pushes=check("Уведомления выбранных приложений",cfg.pushes);
        note("В списке отображаются видимые Android приложения. Общий доступ к списку установленных пакетов не запрашивается.");
        appsButton=secondary("Приложения · "+cfg.apps.size(),this::chooseApps);ongoing=check("Включать постоянные уведомления",cfg.ongoing);

        section("Telegram");tg=check("Пересылать события в Telegram",cfg.telegram);
        token=field("BotToken",cfg.token,true);chat=field("ChatID",cfg.chat,false);
        note("Сначала отправьте боту /start. Для группы нужен доступ бота к отправке.");
        tgTestButton=button("Отправить тест в Telegram",()->testChannel(true));tgResult=note(ConnectionTests.telegramStatus);

        section("Email / SMTP");email=check("Пересылать события на email",cfg.email);
        subsection("Подключение");
        pair(()->host=field("SMTP сервер",cfg.host,false),()->{port=field("Порт",String.valueOf(cfg.port),false);port.setInputType(InputType.TYPE_CLASS_NUMBER);},2,1);
        pair(()->{note("Шифрование");tls=new Spinner(this);tls.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"STARTTLS","SSL"}));tls.setSelection(cfg.tls.equals("SSL")?1:0);area.addView(tls);styleSpinner(tls);},
            ()->{note("Авторизация");auth=new Spinner(this);String[] modes={"AUTO","LOGIN","PLAIN","NONE"};auth.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));auth.setSelection(java.util.Arrays.asList(modes).indexOf(cfg.auth)<0?0:java.util.Arrays.asList(modes).indexOf(cfg.auth));area.addView(auth);styleSpinner(auth);},1,1);
        subsection("Учётные данные");
        user=field("SMTP логин",cfg.user,false);password=field("Пароль SMTP",cfg.password,true);
        subsection("Адреса письма");
        from=field("Email отправителя",cfg.from,false);from.setHint("Пусто — SMTP логин");to=field("Email получателя",cfg.to,false);
        subsection("Проверка и диагностика");
        secondary("Помощь по SMTP",()->new AlertDialog.Builder(this).setTitle("Настройка SMTP").setMessage("STARTTLS обычно использует порт 587, SSL — 465. Уточняйте настройки у провайдера. AUTO выбирает LOGIN/PLAIN. NONE в авторизации — relay без входа; защищённое TLS-соединение обязательно. Принятие сервером не гарантирует попадание во Входящие — проверьте Спам.").setPositiveButton("Понятно",null).show());
        pair(()->secondary("Лог SMTP",this::showSmtpLog),()->secondary("Копировать лог",this::copySmtpLog),1,1);
        mailTestButton=button("Отправить тестовое письмо SMTP",()->testChannel(false));mailResult=note(ConnectionTests.emailStatus);

        section("Формат сообщений");note("Один шаблон для SMS, звонков и уведомлений в Telegram и SMTP.");
        templateField=new EditText(this);templateField.setId(viewId++);templateField.setText(cfg.messageTemplate);templateField.setTextSize(14);templateField.setTextColor(UiColors.TEXT);templateField.setBackground(border(UiColors.FIELD));templateField.setPadding(dp(12),dp(10),dp(12),dp(10));templateField.setGravity(Gravity.TOP|Gravity.START);templateField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);templateField.setMinLines(4);templateField.setMaxLines(8);templateField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});area.addView(templateField,new LinearLayout.LayoutParams(-1,-2));
        pair(()->secondary("Предпросмотр",this::previewTemplate),()->secondary("По умолчанию",()->templateField.setText(MessageTemplate.DEFAULT)),1,1);
        secondary("Переменные шаблона",this::templateHelp);
        section("Настройки отправки");button("Сохранить настройки",()->save(false));
    }
    private void buildAccess(){
        sectionTab=2;section("Проверка доступа");permissionStatus=note("");
        section("Ограничения Android");
        secondary("Открыть карточку приложения / ограничения",()->openAppSettings());
        secondary("Почему Android блокирует разрешения?",this::permissionHelp);
        note("Ограниченные разрешения и предупреждения Play Protect регулирует Android.");

        section("SMS и телефон");
        button("Разрешить получение новых SMS",()->requestAccess(PermissionRequests.SMS));
        button("Разрешить состояние телефона и список SIM",()->requestAccess(PermissionRequests.PHONE));
        button("Разрешить журнал звонков для номера",()->requestAccess(PermissionRequests.CALL_LOG));
        section("Уведомления");
        button("Разрешить доступ к уведомлениям",()->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        secondary("Переподключить службу уведомлений",this::reconnectListener);
        button("Разрешить уведомление о работе",()->requestAccess(PermissionRequests.STATUS));
        section("Работа в фоне");
        button("Разрешить работу в фоне без ограничений",this::requestUnrestrictedBackground);
        button("Отключить приостановку в неактивный период",this::manageUnusedApp);
        button("Настройки оптимизации батареи",()->open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)));
        note("Первая кнопка запрашивает исключение из оптимизации для RelayBridge; настройки оптимизации открывают общий список. Режим «Без ограничений» и автозапуск могут дополнительно настраиваться в карточке приложения и зависят от прошивки.");
        secondary("О фоновой работе",()->new AlertDialog.Builder(this).setTitle("Работа в фоне").setMessage("Разрешите исключение из оптимизации батареи. В карточке RelayBridge отключите приостановку неиспользуемого приложения и включите Автозапуск, если он предусмотрен прошивкой. Отдельного разрешения на низкий заряд нет. Система может задерживать фоновые задания. После принудительной остановки откройте приложение снова. Эти переключатели изменяются в системных настройках.").setPositiveButton("Понятно",null).show());
    }
    private void buildHistory(){
        sectionTab=3;section("История передачи");journal=note("");
        history=new HistoryView(this);area.addView(history,new LinearLayout.LayoutParams(-1,-2));
        section("Очередь и данные");
        secondary("Конфиденциальность",this::privacy);
        secondary("Отозвать согласие и удалить все данные",()->new AlertDialog.Builder(this).setTitle("Удалить данные RelayBridge?").setMessage("Пересылка будет остановлена. Удалятся настройки, пароли, локальная очередь и журнал. Уже отправленные копии в Telegram и почте останутся у получателей.").setPositiveButton("Удалить",(d,w)->{try{Indicator.stop(this);Events.clear(this);SmtpLog.clear();Config.prefs(this).edit().clear().commit();recreate();}catch(Exception e){toast("Не удалось удалить данные");}}).setNegativeButton("Отмена",null).show());
        secondary("Проверить и восстановить очередь",()->{Events.recover(this);updateStatus();toast("Ожидающие задания восстановлены");});
        secondary("Очистить очередь и журнал",()->new AlertDialog.Builder(this).setMessage("Удалить также ещё не доставленные события?").setPositiveButton("Удалить",(d,w)->{Events.clear(this);updateStatus();}).setNegativeButton("Отмена",null).show());
        note("Записи зашифрованы. Нажмите событие для подробностей. Хранится до 500 событий за 7 дней; Android может скрывать чувствительные уведомления.");
    }
    @Override public Object onRetainCustomNonConfigurationInstance(){
        if(token==null)return null;Draft d=new Draft();d.config=readForm(false);d.port=port.getText().toString();d.scroll=scroll.getScrollY();d.tab=selectedTab;return d;
    }
    @Override protected void onResume(){super.onResume();if(status!=null){try{Indicator.update(this,Config.load(this).enabled);}catch(Exception ignored){}updateSimSummary();handler.removeCallbacks(refresh);handler.post(refresh);if(listenerGranted()&&!RelayListener.connected)rebindQuietly();}}
    @Override protected void onPause(){handler.removeCallbacks(refresh);super.onPause();}
    @Override protected void onSaveInstanceState(Bundle out){out.putInt("permission-request",pendingPermissionRequest);super.onSaveInstanceState(out);}
    private void requestAccess(int request){
        if(!Consent.accepted(this)){consent(()->requestAccess(request));return;}
        String permission=PermissionRequests.permission(request);if(permission==null)return;
        if(pendingPermissionRequest!=0){toast("Завершите текущий запрос разрешения");return;}
        if(granted(permission)){
            if(request==PermissionRequests.SIM_PHONE||request==PermissionRequests.CALL_SIM_PHONE)chooseSims(request==PermissionRequests.CALL_SIM_PHONE);
            else if(request==PermissionRequests.STATUS&&!Indicator.allowed(this))openStatusNotificationSettings();
            else toast("Разрешение уже выдано");
            updateStatus();updateSimSummary();return;
        }
        if(shouldShowRequestPermissionRationale(permission)){
            new AlertDialog.Builder(this).setTitle("Зачем нужен доступ")
                .setMessage(permissionReason(request)).setPositiveButton("Продолжить",(d,w)->launchPermission(request))
                .setNegativeButton("Отмена",null).show();
        }else launchPermission(request);
    }
    private String permissionReason(int request){return switch(request){
        case PermissionRequests.SMS -> "RECEIVE_SMS позволяет получать новые SMS для выбранных SIM и пересылать их указанным вами получателям. Старые SMS не читаются. Без этого доступа SMS-пересылка недоступна; остальные функции остаются доступны.";
        case PermissionRequests.PHONE,PermissionRequests.SIM_PHONE,PermissionRequests.CALL_SIM_PHONE -> "Доступ к состоянию телефона нужен для события входящего звонка и определения активных SIM. Без него эти функции недоступны.";
        case PermissionRequests.CALL_LOG -> "Android требует READ_CALL_LOG для номера входящего звонка. Приложение не извлекает историю звонков. Можно отказаться: событие звонка может передаваться без номера.";
        default -> "Уведомление RelayBridge показывает, что пересылка включена, и позволяет её остановить. Без него фоновая пересылка приостановлена.";
    };}
    private void launchPermission(int request){
        String permission=PermissionRequests.permission(request);if(permission==null||pendingPermissionRequest!=0)return;
        pendingPermissionRequest=request;
        try{permissionLauncher.launch(permission);}catch(SecurityException|IllegalStateException|IllegalArgumentException e){pendingPermissionRequest=0;toast("Android не разрешил запрос. Проверьте настройки приложения.");}
    }
    private void permissionResult(boolean result){
        int request=pendingPermissionRequest;pendingPermissionRequest=0;
        String permission=PermissionRequests.permission(request);if(permission==null)return;
        // A registry result alone is insufficient; re-check the actual OS permission.
        boolean accepted=result&&granted(permission);
        updateStatus();updateSimSummary();
        if(accepted&&(request==PermissionRequests.SIM_PHONE||request==PermissionRequests.CALL_SIM_PHONE))chooseSims(request==PermissionRequests.CALL_SIM_PHONE);
        else if(!accepted){
            if(request==PermissionRequests.STATUS)new AlertDialog.Builder(this).setTitle("Уведомление о работе отключено").setMessage("Разрешите уведомления RelayBridge в системных настройках. Это отдельный доступ от чтения уведомлений других приложений.").setPositiveButton("Открыть настройки",(d,w)->openStatusNotificationSettings()).setNegativeButton("Закрыть",null).show();
            else if(shouldShowRequestPermissionRationale(permission))toast("Доступ не выдан. Остальные функции доступны.");
            else new AlertDialog.Builder(this).setTitle("Доступ не выдан")
                .setMessage("Android не выдал разрешение. Причиной может быть отказ без повторного диалога, ограничение установщика, настройка безопасности или политика администратора. Результат запроса не сообщает точную причину. Вы можете проверить доступ в карточке приложения; без разрешения соответствующая функция отключена.")
                .setPositiveButton("Проверить настройки",(d,w)->openAppSettings()).setNegativeButton("Закрыть",null).show();
        }
        try{Indicator.update(this,Config.load(this).enabled);}catch(Exception ignored){}
    }
    private void openStatusNotificationSettings(){
        NotificationManager manager=getSystemService(NotificationManager.class);NotificationChannel channel=manager.getNotificationChannel("relay-status");
        Intent intent=new Intent(channel!=null&&channel.getImportance()==NotificationManager.IMPORTANCE_NONE?Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS:Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());if(channel!=null)intent.putExtra(Settings.EXTRA_CHANNEL_ID,"relay-status");open(intent);
    }
    private boolean granted(String p){return checkSelfPermission(p)==PackageManager.PERMISSION_GRANTED;}
    private boolean listenerGranted(){return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(new ComponentName(this,RelayListener.class));}
    private void rebindQuietly(){try{NotificationListenerService.requestRebind(new ComponentName(this,RelayListener.class));}catch(Exception ignored){}}
    private void reconnectListener(){if(!listenerGranted()){toast("Сначала разрешите доступ к уведомлениям в системном списке");return;}rebindQuietly();toast("Запрошено переподключение службы");}
    private void permissionHelp(){new AlertDialog.Builder(this).setTitle("Разрешение не выдаётся Android")
        .setMessage("Для APK из файла Android 15 может блокировать SMS и доступ к уведомлениям до отдельного подтверждения.\n\nОткройте Настройки → Приложения → RelayBridge → меню ⋮ → «Разрешить ограниченные настройки». После подтверждения вернитесь и снова запросите разрешение.\n\nЕсли пункта нет или запрет сохраняется, установщик/прошивка или администратор может не разрешать этот доступ. Само приложение не может отменить запрет. Тесты Telegram и email работают без этих разрешений.")
        .setPositiveButton("Открыть карточку",(d,w)->openAppSettings()).setNegativeButton("Закрыть",null).show();}
    private void openAppSettings(){open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    private void updateStatus(){
        if(status==null)return;
        try{
            Config stored=Config.load(this);cfg.enabled=stored.enabled;
            boolean smsReady=granted(Manifest.permission.RECEIVE_SMS),phoneReady=granted(Manifest.permission.READ_PHONE_STATE),notificationsReady=listenerGranted();
            String listener=notificationsReady?(RelayListener.connected?"подключена":"доступ разрешён; служба не подключена"):"доступ не выдан / ограничен";
            PowerManager pm=getSystemService(PowerManager.class);
            String state=cfg.enabled?"● Пересылка активна":"● Пересылка остановлена";
            android.text.SpannableStringBuilder overview=new android.text.SpannableStringBuilder(state);
            overview.setSpan(new android.text.style.ForegroundColorSpan(cfg.enabled?UiColors.SUCCESS:UiColors.ERROR),0,state.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            status.setText(overview);status.setTextColor(UiColors.MUTED);status.setTextSize(18);status.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            overviewDetails.setText((stored.telegram||stored.email?"Отправка: "+(stored.telegram?"Telegram":"")+(stored.telegram&&stored.email?" · ":"")+(stored.email?"SMTP":""):"Отправка не настроена")+"\nПриложений: "+stored.apps.size());
            List<String> missing=new ArrayList<>();if(stored.sms&&!smsReady)missing.add("SMS");if(stored.calls&&!phoneReady)missing.add("телефон");if(stored.calls&&!granted(Manifest.permission.READ_CALL_LOG))missing.add("номер звонка");if(stored.pushes&&!notificationsReady)missing.add("уведомления");
            boolean channelReady=stored.telegram||stored.email;overviewReadiness.setText(!channelReady?"Настройте отправку и выполните тест.":missing.isEmpty()?"Доступ к выбранным источникам разрешён.":"Нужен доступ: "+String.join(", ",missing));overviewReadiness.setTextColor(channelReady&&missing.isEmpty()?UiColors.SUCCESS:UiColors.PENDING);
            android.text.SpannableStringBuilder access=new android.text.SpannableStringBuilder();
            permissionLine(access,"Получение SMS",smsReady,null);
            permissionLine(access,"Состояние телефона",phoneReady,null);
            permissionLine(access,"Журнал звонков / номер",granted(Manifest.permission.READ_CALL_LOG),null);
            permissionLine(access,"Доступ к уведомлениям",notificationsReady,null);
            permissionLine(access,"Разрешение уведомлений приложения",granted(Manifest.permission.POST_NOTIFICATIONS),null);
            permissionLine(access,"Уведомление о работе (приложение и канал)",Indicator.allowed(this),null);
            permissionLine(access,"Без оптимизации батареи",pm.isIgnoringBatteryOptimizations(getPackageName()),null);
            if(notificationsReady){int begin=access.length();access.append("Служба уведомлений: ").append(RelayListener.connected?"подключена":"ожидает подключения");access.setSpan(new android.text.style.ForegroundColorSpan(RelayListener.connected?UiColors.SUCCESS:UiColors.PENDING),begin,access.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            permissionStatus.setText(access);permissionStatus.setTextColor(UiColors.MUTED);permissionStatus.setLineSpacing(dp(7),1f);
            long last=Config.prefs(this).getLong("last-capture",0);
            String received=last>0?"Последнее событие: "+android.text.format.DateFormat.format("dd.MM HH:mm:ss",last)+"\n\n":"Новых событий в очереди пока нет.\n\n";
            journal.setText(received+Config.prefs(this).getString("fault",""));
            if(selectedTab==3)history.refresh();
            tgResult.setText(ConnectionTests.telegramStatus);mailResult.setText(ConnectionTests.emailStatus);
            tgTestButton.setEnabled(!ConnectionTests.telegramRunning);mailTestButton.setEnabled(!ConnectionTests.emailRunning);
        }catch(Exception e){status.setText("Ошибка чтения настроек. Закройте и снова откройте приложение.");}
    }
    private void permissionLine(android.text.SpannableStringBuilder out,String label,boolean allowed,String detail){
        out.append(label).append(": ");int begin=out.length();out.append(allowed?"● разрешено":"● не выдано");
        out.setSpan(new android.text.style.ForegroundColorSpan(allowed?UiColors.SUCCESS:UiColors.ERROR),begin,out.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);out.append("\n");
    }
    private Config readForm(boolean strictPort){
        Config next=new Config();next.messageTemplate=templateField.getText().toString();next.callAllSims=cfg.callAllSims;next.callSubscriptions.addAll(cfg.callSubscriptions);next.smsAllSims=cfg.smsAllSims;next.smsSubscriptions.addAll(cfg.smsSubscriptions);next.enabled=cfg.enabled;next.sms=sms.isChecked();next.calls=calls.isChecked();next.pushes=pushes.isChecked();next.ongoing=ongoing.isChecked();next.telegram=tg.isChecked();next.email=email.isChecked();
        next.token=token.getText().toString().trim();next.chat=chat.getText().toString().trim();next.host=host.getText().toString().trim();
        try{next.port=Integer.parseInt(port.getText().toString().trim());}catch(NumberFormatException e){if(strictPort)throw new IllegalArgumentException("Порт должен быть числом 1–65535");next.port=cfg.port;}
        next.tls=tls.getSelectedItem().toString();next.auth=auth.getSelectedItem().toString();next.user=user.getText().toString().trim();next.password=password.getText().toString();
        next.from=from.getText().toString().trim();if(next.from.isBlank())next.from=next.user;
        next.to=to.getText().toString().trim();next.apps.addAll(cfg.apps);return next;
    }
    private void templateHelp(){new AlertDialog.Builder(this).setTitle("Переменные сообщений").setMessage("{{time}} — дата и время с часовым поясом\n{{date}} — дата\n{{type}} — тип события\n{{data}} — все данные события, включая отправителя/SIM или приложение\n{{title}} — заголовок уведомления, SMS от номера или Входящий звонок\n{{message}} — текст SMS/уведомления или номер звонка с подписью\n{{number}} — номер отправителя/звонящего\n{{sim}} — SIM для SMS/звонка, если доступна\n{{app}} — название приложения\n{{package}} — пакет приложения\n\nНедоступные значения пустые. Переносы строк сохраняются. HTML/Markdown автоматически не включается. Тесты соединения отправляют свой технический текст; для шаблона используйте Предпросмотр. Нажмите Сохранить настройки для применения к новым событиям.").setPositiveButton("Понятно",null).show();}
    private String previewMessage(String template,Map<String,String> values){
        try{return MessageTemplate.render(template,values);}catch(IllegalArgumentException e){return "[Для этого типа событий текст пустой. Добавьте {{data}} или {{message}}.]";}
    }
    private void previewTemplate(){
        try{String template=templateField.getText().toString();MessageTemplate.validate(template);long now=System.currentTimeMillis();String sms=previewMessage(template,MessageTemplate.values("SMS","SMS от +70000000000","Пример SMS","От: +70000000000\nSIM: Sim 1 MTS\nПример SMS","+70000000000","Sim 1 MTS","","",now));
            String call=previewMessage(template,MessageTemplate.values("Входящий звонок","Входящий звонок","Номер: +70000000000","SIM: Sim 1 MTS\nНомер: +70000000000","+70000000000","Sim 1 MTS","","",now));
            String push=previewMessage(template,MessageTemplate.values("Уведомление","Новое сообщение","Пример уведомления","Signal (org.thoughtcrime.securesms)\nНовое сообщение\nПример уведомления","","","Signal","org.thoughtcrime.securesms",now));
            TextView content=new TextView(this);content.setTextColor(UiColors.TEXT);content.setTextSize(14);content.setTextIsSelectable(true);content.setPadding(dp(16),dp(12),dp(16),dp(12));content.setText("SMS\n"+sms+"\n\nЗВОНОК\n"+call+"\n\nУВЕДОМЛЕНИЕ\n"+push);ScrollView view=new ScrollView(this);view.addView(content);new AlertDialog.Builder(this).setTitle("Предпросмотр · пример данных").setView(view).setPositiveButton("Закрыть",null).show();
        }catch(IllegalArgumentException e){toast(e.getMessage());}
    }
    private void copySmtpLog(){
        android.content.ClipboardManager clipboard=getSystemService(android.content.ClipboardManager.class);
        if(clipboard==null){toast("Буфер обмена недоступен");return;}
        clipboard.setPrimaryClip(ClipData.newPlainText("RelayBridge SMTP",SmtpLog.snapshot()));toast("SMTP-лог скопирован");
    }
    private void requestUnrestrictedBackground(){
        PowerManager pm=getSystemService(PowerManager.class);
        if(pm.isIgnoringBatteryOptimizations(getPackageName())){updateStatus();toast("Исключение из оптимизации батареи уже разрешено");return;}
        Intent intent=new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName()));
        try{startActivity(intent);}catch(ActivityNotFoundException|SecurityException e){openAppSettings();toast("Откройте Батарея → Без ограничений / Разрешить работу в фоне");}
    }
    private void manageUnusedApp(){
        new AlertDialog.Builder(this).setTitle("Приостановка неиспользуемого приложения")
            .setMessage("В карточке RelayBridge отключите «Приостановить работу, если приложение не используется» или «Приостановить в неактивный период». Это также предотвращает автоматический отзыв разрешений при долгом неиспользовании. Изменение доступно в системных настройках.")
            .setPositiveButton("Открыть настройки",(d,w)->openAppSettings()).setNegativeButton("Закрыть",null).show();
    }
    private void showSmtpLog(){
        TextView content=new TextView(this);content.setTextColor(UiColors.TEXT);content.setTypeface(Typeface.MONOSPACE);content.setTextSize(12);content.setTextIsSelectable(true);content.setPadding(dp(14),dp(10),dp(14),dp(10));
        ScrollView view=new ScrollView(this);view.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Лог SMTP · этапы и коды").setView(view).setPositiveButton("Закрыть",null).setNegativeButton("Копировать",null).setNeutralButton("Очистить",(d,w)->SmtpLog.clear()).create();
        Runnable update=new Runnable(){public void run(){if(!dialog.isShowing()||isDestroyed())return;content.setText(SmtpLog.snapshot());handler.postDelayed(this,1000);}};
        dialog.setOnDismissListener(d->handler.removeCallbacks(update));dialog.show();dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->copySmtpLog());handler.post(update);
    }
    private void testChannel(boolean telegram){
        try{
            Config snapshot;
            if(telegram){snapshot=new Config();snapshot.token=token.getText().toString().trim();snapshot.chat=chat.getText().toString().trim();snapshot.validateTelegram();ConnectionTests.telegram(snapshot);}
            else{snapshot=readForm(true);snapshot.validateEmail();ConnectionTests.email(snapshot);}
            updateStatus();
        }catch(IllegalArgumentException e){if(telegram)ConnectionTests.telegramStatus="Telegram: "+e.getMessage();else ConnectionTests.emailStatus="Email: "+e.getMessage();updateStatus();}
    }
    private boolean save(boolean activate){
        try{
            Config next=readForm(email.isChecked());next.enabled=activate||Config.load(this).enabled;
            if(next.enabled)next.validate();else next.validateSelected();
            next.save(this);Config.prefs(this).edit().remove("fault").apply();cfg=next;
            Indicator.update(this,cfg.enabled);Events.recover(this);if(listenerGranted()&&!RelayListener.connected)rebindQuietly();updateStatus();toast("Настройки сохранены");return true;
        }catch(IllegalArgumentException e){toast(e.getMessage());return false;}
        catch(Exception e){toast("Не удалось сохранить настройки");return false;}
    }
    private void activate(){
        if(!Consent.accepted(this)){consent(this::activate);return;}
        if(!Indicator.allowed(this)){toast("Для прозрачной фоновой работы разрешите уведомление RelayBridge");requestAccess(PermissionRequests.STATUS);return;}
        try{
            Config next=readForm(email.isChecked());next.validate();
            boolean ready=(next.sms&&granted(Manifest.permission.RECEIVE_SMS))||(next.calls&&granted(Manifest.permission.READ_PHONE_STATE))||(next.pushes&&listenerGranted()&&!next.apps.isEmpty());
            if(!ready){new AlertDialog.Builder(this).setTitle("Нет готового источника")
                .setMessage("Выдайте доступ хотя бы к одному включённому источнику. Для push также выберите приложения. Тесты отправки доступны без разрешений.")
                .setPositiveButton("Разрешения Android",(d,w)->permissionHelp()).setNegativeButton("Закрыть",null).show();return;}
            String missing="";if(next.sms&&!granted(Manifest.permission.RECEIVE_SMS))missing+="\nSMS пока не будут пересылаться.";
            if(next.calls&&!granted(Manifest.permission.READ_PHONE_STATE))missing+="\nСобытия звонков пока недоступны.";
            if(next.pushes&&(!listenerGranted()||next.apps.isEmpty()))missing+="\nУведомления пока не настроены.";
            new AlertDialog.Builder(this).setTitle("Включить пересылку?")
                .setMessage("Новые события разрешённых источников уйдут указанным вами получателям. Подтвердите передачу SMS, номера звонящего и/или текста выбранных уведомлений."+missing)
                .setPositiveButton("Включить",(d,w)->save(true)).setNegativeButton("Отмена",null).show();
        }catch(IllegalArgumentException e){toast(e.getMessage());}
    }
    private void updateSimSummary(){
        updateSimSummary(simSummary,"SMS",cfg.smsAllSims,cfg.smsSubscriptions);
        updateSimSummary(callSimSummary,"Звонки",cfg.callAllSims,cfg.callSubscriptions);
    }
    private void updateSimSummary(TextView summary,String source,boolean all,Set<Integer> selected){
        if(summary==null)return;
        summary.setTextColor(UiColors.MUTED);
        if(all){summary.setText(source+": все SIM-карты");return;}
        if(selected.isEmpty()){summary.setText(source+": SIM-карты не выбраны");summary.setTextColor(UiColors.ERROR);return;}
        List<String> names=new ArrayList<>();for(SimCards.Card card:SimCards.active(this))if(selected.contains(card.id))names.add(card.label);
        summary.setText(source+": "+(names.isEmpty()?"выбранные SIM недоступны или нет разрешения телефона":String.join(", ",names)));
        summary.setTextColor(names.size()==selected.size()?UiColors.MUTED:UiColors.PENDING);
    }
    private void chooseSims(){chooseSims(false);}
    private void chooseSims(boolean forCalls){
        if(!SimCards.permitted(this)){
            new AlertDialog.Builder(this).setTitle("Доступ к SIM-картам").setMessage("Для списка SIM и названия оператора разрешите доступ к состоянию телефона. После разрешения откроется выбор SIM.")
                .setPositiveButton("Разрешить",(d,w)->requestAccess(forCalls?PermissionRequests.CALL_SIM_PHONE:PermissionRequests.SIM_PHONE)).setNegativeButton("Отмена",null).show();return;
        }
        boolean allSelected=forCalls?cfg.callAllSims:cfg.smsAllSims;
        Set<Integer> selectedIds=forCalls?cfg.callSubscriptions:cfg.smsSubscriptions;
        List<SimCards.Card> cards=SimCards.active(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(12));
        CheckBox all=new CheckBox(this);all.setText("Все SIM-карты, включая новые");all.setTextColor(UiColors.TEXT);all.setChecked(allSelected);content.addView(all);
        List<CheckBox> choices=new ArrayList<>();for(SimCards.Card card:cards){CheckBox choice=new CheckBox(this);choice.setText(card.label);choice.setTextColor(UiColors.TEXT);choice.setChecked(allSelected||selectedIds.contains(card.id));choice.setEnabled(!all.isChecked());content.addView(choice);choices.add(choice);}
        all.setOnCheckedChangeListener((v,on)->{for(CheckBox choice:choices)choice.setEnabled(!on);});
        TextView hint=new TextView(this);hint.setTextColor(UiColors.MUTED);hint.setTextSize(13);hint.setPadding(0,dp(12),0,0);
        hint.setText("Отключите «Все SIM-карты» и отметьте нужные. Без выбора захват этого источника отключён. После замены SIM или eSIM проверьте выбор снова."+(forCalls?" Если Android не сообщает SIM звонка и её нельзя однозначно определить, при выборе отдельных SIM звонок будет пропущен.":"")+(cards.isEmpty()?" Активные SIM не найдены.":""));content.addView(hint);
        ScrollView view=new ScrollView(this);view.addView(content);
        new AlertDialog.Builder(this).setTitle(forCalls?"SIM для захвата звонков":"SIM для захвата SMS").setView(view).setPositiveButton("Сохранить выбор",(d,w)->{
            try{Set<Integer> selected=new HashSet<>();if(!all.isChecked())for(int n=0;n<cards.size();n++)if(choices.get(n).isChecked())selected.add(cards.get(n).id);
                Config stored=Config.load(this);
                if(forCalls){stored.callAllSims=all.isChecked();stored.callSubscriptions.clear();stored.callSubscriptions.addAll(selected);cfg.callAllSims=stored.callAllSims;cfg.callSubscriptions.clear();cfg.callSubscriptions.addAll(selected);}
                else{stored.smsAllSims=all.isChecked();stored.smsSubscriptions.clear();stored.smsSubscriptions.addAll(selected);cfg.smsAllSims=stored.smsAllSims;cfg.smsSubscriptions.clear();cfg.smsSubscriptions.addAll(selected);}
                stored.save(this);updateSimSummary();toast("Выбор SIM сохранён");
            }catch(Exception e){toast("Не удалось сохранить выбор SIM");}
        }).setNegativeButton("Отмена",null).show();
    }
    private void chooseApps(){
        appsButton.setEnabled(false);var executor=Executors.newSingleThreadExecutor();
        executor.execute(()->{List<AppRow> all=new ArrayList<>();
            try{for(ApplicationInfo info:getPackageManager().getInstalledApplications(0))if(!info.packageName.equals(getPackageName())){
                    String label=info.packageName;try{label=info.loadLabel(getPackageManager()).toString();}catch(Exception ignored){}
                    all.add(new AppRow(info,label));
                }
                all.sort(Comparator.comparing((AppRow a)->a.label.toLowerCase(Locale.ROOT)).thenComparing(a->a.info.packageName));
                runOnUiThread(()->{appsButton.setEnabled(true);if(!isFinishing()&&!isDestroyed())showApps(all);});
            }catch(Exception e){runOnUiThread(()->{appsButton.setEnabled(true);toast("Не удалось загрузить список приложений");});}finally{executor.shutdown();}
        });
    }
    private void showApps(List<AppRow> all){
        LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(14),0,dp(14),0);
        EditText search=new EditText(this);search.setSingleLine();search.setHint("Название или имя пакета");search.setTextColor(UiColors.TEXT);layout.addView(search);
        TextView count=new TextView(this);count.setTextColor(UiColors.TEXT);count.setPadding(0,dp(8),0,dp(8));layout.addView(count);
        ListView list=new ListView(this);int height=Math.min(dp(440),getResources().getDisplayMetrics().heightPixels/2);layout.addView(list,new LinearLayout.LayoutParams(-1,height));
        Set<String> selected=new HashSet<>(cfg.apps);AppsAdapter adapter=new AppsAdapter(all,selected,count);list.setAdapter(adapter);adapter.updateCount();
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){adapter.filter(s.toString());}public void afterTextChanged(Editable e){}});
        appDialog=new AlertDialog.Builder(this).setTitle("Приложения для пересылки").setView(layout).setPositiveButton("Сохранить выбор",(d,w)->{
            try{Config stored=Config.load(this);stored.apps.clear();stored.apps.addAll(selected);stored.save(this);cfg.apps.clear();cfg.apps.addAll(selected);appsButton.setText("Приложения · "+cfg.apps.size());updateStatus();toast("Выбор приложений сохранён");}
            catch(Exception e){toast("Не удалось сохранить выбор");}
        }).setNegativeButton("Отмена",null).show();
    }
    private static final class AppRow{final ApplicationInfo info;final String label;AppRow(ApplicationInfo i,String l){info=i;label=l;}}
    private final class AppsAdapter extends BaseAdapter {
        final List<AppRow> all,visible=new ArrayList<>();final Set<String> selected;final TextView count;
        final android.util.LruCache<String,Drawable> icons=new android.util.LruCache<>(96);
        AppsAdapter(List<AppRow> rows,Set<String> s,TextView c){all=rows;selected=s;count=c;visible.addAll(rows);}
        void updateCount(){count.setText("Выбрано: "+selected.size()+" · Показано: "+visible.size());}
        void filter(String value){String q=value.toLowerCase(Locale.ROOT);visible.clear();for(AppRow a:all)if((a.label+" "+a.info.packageName).toLowerCase(Locale.ROOT).contains(q))visible.add(a);notifyDataSetChanged();updateCount();}
        public int getCount(){return visible.size();}public AppRow getItem(int p){return visible.get(p);}public long getItemId(int p){return p;}
        public View getView(int position,View convert,ViewGroup parent){
            LinearLayout row;RowHolder h;
            if(convert==null){row=new LinearLayout(MainActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(10),dp(2),dp(10));row.setMinimumHeight(dp(76));
                h=new RowHolder();h.icon=new ImageView(MainActivity.this);LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(dp(40),dp(40));image.setMarginEnd(dp(12));row.addView(h.icon,image);
                LinearLayout text=new LinearLayout(MainActivity.this);text.setOrientation(LinearLayout.VERTICAL);row.addView(text,new LinearLayout.LayoutParams(0,-2,1));
                h.name=new TextView(MainActivity.this);h.name.setTextColor(UiColors.TEXT);h.name.setTextSize(16);h.name.setTypeface(null,Typeface.BOLD);h.name.setMaxLines(2);h.name.setEllipsize(TextUtils.TruncateAt.END);text.addView(h.name);
                h.pkg=new TextView(MainActivity.this);h.pkg.setTextColor(UiColors.MUTED);h.pkg.setTextSize(12);h.pkg.setTypeface(Typeface.MONOSPACE);h.pkg.setSingleLine(false);h.pkg.setTextDirection(View.TEXT_DIRECTION_LTR);text.addView(h.pkg);
                h.box=new CheckBox(MainActivity.this);h.box.setButtonTintList(ColorStateList.valueOf(UiColors.TEXT));row.addView(h.box);row.setTag(h);
            }else{row=(LinearLayout)convert;h=(RowHolder)row.getTag();}
            AppRow a=getItem(position);String pkg=a.info.packageName;Drawable icon=icons.get(pkg);
            if(icon==null){try{icon=a.info.loadIcon(getPackageManager());}catch(Exception ignored){icon=getPackageManager().getDefaultActivityIcon();}if(icon==null)icon=getPackageManager().getDefaultActivityIcon();icons.put(pkg,icon);}
            h.icon.clearColorFilter();h.icon.setImageTintList(null);h.icon.setImageDrawable(icon);h.icon.setContentDescription(null);h.name.setText(a.label);h.pkg.setText(pkg);
            h.box.setOnCheckedChangeListener(null);h.box.setChecked(selected.contains(pkg));h.box.setContentDescription("Пересылать уведомления: "+a.label+", "+pkg);
            h.box.setOnCheckedChangeListener((b,checked)->{if(checked)selected.add(pkg);else selected.remove(pkg);updateCount();});
            CheckBox box=h.box;row.setOnClickListener(v->box.setChecked(!box.isChecked()));return row;
        }
    }
    private static final class RowHolder{ImageView icon;TextView name,pkg;CheckBox box;}
    private void open(Intent intent){if(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS.equals(intent.getAction())&&!Consent.accepted(this)){consent(()->open(intent));return;}try{startActivity(intent);}catch(ActivityNotFoundException e){toast("Экран недоступен; откройте настройки приложения вручную");}}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    private void subsection(String caption){
        View line=new View(this);line.setBackgroundColor(UiColors.BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.topMargin=dp(8);lp.bottomMargin=dp(6);area.addView(line,lp);
        TextView label=note(caption);label.setTextSize(12);label.setTextColor(UiColors.TEXT);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
    }
    private void pair(Runnable left,Runnable right,float first,float second){
        LinearLayout parent=area,row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setBaselineAligned(false);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout a=new LinearLayout(this),b=new LinearLayout(this);a.setOrientation(LinearLayout.VERTICAL);b.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,first),q=new LinearLayout.LayoutParams(0,-2,second);q.leftMargin=dp(8);row.addView(a,p);row.addView(b,q);
        try{area=a;left.run();area=b;right.run();}finally{area=parent;}
    }
    private void title(String text,int size){TextView label=note(text);label.setTextSize(size);label.setTextColor(UiColors.TEXT);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));label.setPadding(0,0,0,dp(4));}
    private void section(String caption){
        area=page;TextView heading=note(caption.toUpperCase(Locale.ROOT));heading.setTextSize(11);heading.setLetterSpacing(0.12f);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heading.setPadding(dp(2),dp(12),0,dp(6));
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(10),dp(12),dp(12));card.setBackground(border(UiColors.SURFACE));page.addView(card,new LinearLayout.LayoutParams(-1,-2));sections.add(heading);sectionTabs.add(sectionTab);sections.add(card);sectionTabs.add(sectionTab);area=card;
    }
    private void selectTab(int tab){
        selectedTab=tab;for(int i=0;i<sections.size();i++)sections.get(i).setVisibility(sectionTabs.get(i)==tab?View.VISIBLE:View.GONE);
        for(int i=0;i<navigation.getChildCount();i++){Button button=(Button)navigation.getChildAt(i);button.setTextColor(i==tab?UiColors.BACKGROUND:UiColors.MUTED);button.setBackground(border(i==tab?UiColors.TEXT:UiColors.SURFACE));}
        scroll.smoothScrollTo(0,0);if(tab==3&&history!=null)history.refresh();
    }
    private GradientDrawable border(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(14));d.setStroke(dp(1),UiColors.BORDER);return d;}
    private TextView note(String text){TextView t=new TextView(this);t.setText(text);t.setTextSize(13);t.setTextColor(UiColors.MUTED);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(4),0,dp(6));area.addView(t);return t;}
    private CheckBox check(String text,boolean value){CheckBox box=new CheckBox(this);box.setText(text);box.setTextSize(14);box.setTextColor(UiColors.TEXT);box.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{UiColors.TEXT,UiColors.MUTED}));box.setChecked(value);box.setMinHeight(dp(48));area.addView(box);return box;}
    private void styleSpinner(Spinner spinner){spinner.setBackground(border(UiColors.FIELD));spinner.setPadding(dp(10),0,dp(10),0);spinner.setMinimumHeight(dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(8);spinner.setLayoutParams(lp);}
    private EditText field(String label,String value,boolean secret){
        note(label).setTextSize(12);EditText input=new EditText(this);input.setId(viewId++);input.setSingleLine();input.setText(value);input.setTextSize(15);input.setTextColor(UiColors.TEXT);input.setHintTextColor(UiColors.MUTED);input.setTextDirection(View.TEXT_DIRECTION_LTR);input.setBackground(border(UiColors.FIELD));input.setPadding(dp(14),0,dp(14),0);input.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        if(secret){input.setTransformationMethod(PasswordTransformationMethod.getInstance());input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setSaveEnabled(false);}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(8);area.addView(input,lp);return input;
    }
    private Button button(String caption,Runnable action){return makeButton(caption,action,sectionTab!=2);}
    private Button secondary(String caption,Runnable action){return makeButton(caption,action,false);}
    private Button makeButton(String caption,Runnable action,boolean primary){Button button=new Button(this);button.setText(caption);button.setAllCaps(false);button.setTextSize(14);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));button.setTextColor(primary?UiColors.BACKGROUND:UiColors.TEXT);button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22777777),border(primary?UiColors.TEXT:UiColors.FIELD),border(UiColors.TEXT)));button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(dp(48));button.setPadding(dp(10),dp(8),dp(10),dp(8));button.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(3);lp.bottomMargin=dp(3);area.addView(button,lp);return button;}
}
