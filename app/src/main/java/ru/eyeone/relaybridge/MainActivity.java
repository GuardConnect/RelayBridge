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
    private TextView brandTitle,recipientSummary,sourceSummary,lastEvent;
    private UiSwitch relayToggle;
    private TextView relayMode;
    private boolean syncingRelayToggle;
    private LinearLayout missingAccess,grantedAccess;
    private TextView accessCount;
    private View[] accessSegments=new View[8];
    private String accessSignature="";
    private long overviewRevision=-1;private boolean overviewLoading;
    private final java.util.concurrent.ExecutorService overviewExecutor=Executors.newSingleThreadExecutor();

    private LinearLayout simList,callSimList,selectedAppsList;
    private TextView selectedAppsCount;
    private int selectedAppsGeneration;
    private TextView status,journal,tgResult,mailResult,permissionStatus,overviewDetails,overviewReadiness;
    private CompoundButton sms,calls,pushes,ongoing,tg,email;
    private final TextView[] navLabels=new TextView[4];
    private final ImageView[] navIcons=new ImageView[4];
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
        super.onCreate(saved);pendingPermissionRequest=saved==null?0:saved.getInt("permission-request",0);
        try {cfg=Config.load(this);}catch(Exception e){dialogBuilder().setTitle("Настройки недоступны")
            .setMessage("Не удалось расшифровать настройки. Можно сбросить данные и настроить приложение заново.")
            .setPositiveButton("Сбросить",(d,w)->{Config.prefs(this).edit().clear().apply();Events.clear(this);recreate();})
            .setNegativeButton("Закрыть",(d,w)->finish()).setCancelable(false).show();return;}
        Draft draft=getLastCustomNonConfigurationInstance() instanceof Draft?(Draft)getLastCustomNonConfigurationInstance():null;
        if(draft!=null)cfg=draft.config;
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setBackgroundColor(UiColors.BACKGROUND);
        LinearLayout brand=new LinearLayout(this);brand.setOrientation(LinearLayout.HORIZONTAL);brand.setGravity(Gravity.CENTER_VERTICAL);brand.setPadding(dp(16),dp(16),dp(16),dp(18));shell.addView(brand);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);page.setPadding(dp(16),0,dp(16),dp(16));page.setBackgroundColor(UiColors.BACKGROUND);
        scroll.addView(page);shell.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(shell);area=brand;
        shell.setOnApplyWindowInsetsListener((v,insets)->{Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());int keyboard=insets.getInsets(WindowInsets.Type.ime()).bottom;v.setPadding(bars.left,bars.top,bars.right,Math.max(bars.bottom,keyboard));if(navigation!=null)navigation.setVisibility(keyboard>bars.bottom?View.GONE:View.VISIBLE);return insets;});
        brandTitle=new TextView(this);brandTitle.setText("RelayBridge");brandTitle.setTextSize(26);brandTitle.setIncludeFontPadding(false);brandTitle.setTextColor(UiColors.TEXT);brandTitle.setTypeface(null,Typeface.BOLD);brand.addView(brandTitle,new LinearLayout.LayoutParams(0,-2,1));
        TextView version=new TextView(this);version.setText("v"+BuildConfig.VERSION_NAME);version.setTextSize(13);version.setIncludeFontPadding(false);version.setTypeface(Typeface.MONOSPACE);version.setTextColor(UiColors.MUTED);version.setPadding(dp(9),dp(5),dp(9),dp(5));UiLayout.background(version,border(UiColors.BACKGROUND));brand.addView(version);
        navigation=new LinearLayout(this);navigation.setPadding(dp(12),dp(10),dp(12),dp(12));navigation.setBackgroundColor(UiColors.BACKGROUND);shell.addView(navigation);area=page;
        String[] tabs={"Обзор","Отправка","Доступ","Журнал"};
        navigation.setPadding(0,dp(8),0,dp(8));navigation.setGravity(Gravity.CENTER_VERTICAL);
        for(int i=0;i<tabs.length;i++){final int tab=i;LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setPadding(0,dp(4),0,dp(4));item.setMinimumHeight(dp(56));UiLayout.background(item,new RippleDrawable(ColorStateList.valueOf(0x22777777),null,null));navigation.addView(item,new LinearLayout.LayoutParams(0,-2,1));
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);navIcons[i]=icon;item.addView(icon,new LinearLayout.LayoutParams(dp(24),dp(24)));
            TextView label=new TextView(this);label.setText(tabs[i]);label.setTextSize(11);label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER);navLabels[i]=label;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);item.addView(label,lp);item.setOnClickListener(v->selectTab(tab));label.setOnClickListener(v->selectTab(tab));icon.setOnClickListener(v->selectTab(tab));item.setContentDescription(tabs[i]);item.setFocusable(true);}
        buildOverview();buildChannels();buildAccess();buildHistory();
        selectTab(draft==null?0:draft.tab);
        if(draft!=null){port.setText(draft.port);scroll.post(()->scroll.scrollTo(0,draft.scroll));}

    }
    private void consent(Runnable next){
        dialogBuilder().setTitle("Передача личных данных")
            .setMessage(Consent.DISCLOSURE).setPositiveButton("Согласен",(d,w)->{try{Config paused=Config.load(this);paused.enabled=false;paused.save(this);if(Config.prefs(this).edit().putInt("consent-version",Consent.VERSION).commit())next.run();else toast("Не удалось сохранить согласие");}catch(Exception e){toast("Не удалось сохранить согласие");}})
            .setNegativeButton("Отмена",null).show();
    }
    private void privacy(){
        TextView text=new TextView(this);text.setText(Consent.PRIVACY);text.setTextColor(UiColors.TEXT);text.setPadding(dp(16),dp(12),dp(16),dp(12));text.setTextIsSelectable(true);ScrollView view=new ScrollView(this);view.addView(text);
        dialogBuilder().setTitle("Конфиденциальность RelayBridge").setView(view).setPositiveButton("Закрыть",null).show();
    }
    private void buildOverview(){
        sectionTab=0;section("Пересылка");
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setMinimumHeight(dp(56));area.addView(row,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams labelLp=new LinearLayout.LayoutParams(0,-2,1);labelLp.rightMargin=dp(16);row.addView(words,labelLp);
        LinearLayout card=area;area=words;status=note("");status.setTextSize(20);status.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));relayMode=note("");relayMode.setTextSize(12);area=card;
        relayToggle=new UiSwitch(this);relayToggle.setContentDescription("Включить пересылку");row.addView(relayToggle,new LinearLayout.LayoutParams(-2,-2));
        relayToggle.setOnCheckedChangeListener((button,on)->{if(syncingRelayToggle)return;if(on){syncRelayToggle(cfg.enabled);activate();}else{try{Indicator.stop(this);cfg.enabled=false;toast("Пересылка остановлена");}catch(Exception e){toast("Не удалось остановить пересылку");}updateStatus();}});
        row.setOnClickListener(v->relayToggle.setChecked(!relayToggle.isChecked()));status.setOnClickListener(v->relayToggle.setChecked(!relayToggle.isChecked()));
        overviewDetails=note("");
        section("Готовность");recipientSummary=linkRow("Получатели","Настройте получателей",()->selectTab(1),1);sourceSummary=linkRow("Источники","Выберите источники",()->selectTab(1),4);overviewReadiness=linkRow("Доступ","Проверка доступа",()->selectTab(2),2);
        section("Последнее событие");lastEvent=linkRow("События","После включения пересылки",()->selectTab(3),3);
    }
    private void buildChannels(){
        sectionTab=1;section("Источники");sms=check("Входящие SMS",cfg.sms);
        button("SIM для SMS",this::chooseSims);simList=simStatus();updateSimSummary();
        divider();calls=check("Входящие звонки",cfg.calls);
        button("SIM для звонков",()->chooseSims(true));callSimList=simStatus();updateSimSummary();
        divider();pushes=check("Уведомления",cfg.pushes);
        
        appsButton=button("Выберите приложения из списка установленных",this::chooseApps);
        selectedAppsCount=note("");selectedAppsList=new LinearLayout(this);selectedAppsList.setOrientation(LinearLayout.VERTICAL);area.addView(selectedAppsList,new LinearLayout.LayoutParams(-1,-2));updateSelectedApps();
        ongoing=check("Включать постоянные уведомления",cfg.ongoing);
        TextView ongoingHelp=formHelp("Пересылать также постоянные уведомления выбранных приложений — например, о VPN, воспроизведении музыки или фоновой работе. Когда выключено, такие уведомления пропускаются.");ongoingHelp.setTextColor(UiColors.MUTED);

        section("Получатели · Telegram");tg=check("Telegram",cfg.telegram);
        token=field("Токен бота",cfg.token,true);token.setHint("Токен от @BotFather");chat=field("Chat ID",cfg.chat,false);chat.setHint("Числовой ID или @имя_канала");
        formHelp("Для личного чата отправьте боту /start. В группе или канале разрешите боту отправлять сообщения.");
        tgTestButton=button("Отправить тест",()->testChannel(true));tgResult=formHelp(ConnectionTests.telegramStatus);

        section("Email / SMTP");email=check("Email / SMTP",cfg.email);
        
        pair(()->{host=field("SMTP сервер",cfg.host,false);host.setHint("smtp.example.com");},()->{port=field("Порт",String.valueOf(cfg.port),false);port.setInputType(InputType.TYPE_CLASS_NUMBER);},2,1);
        formLabel("Шифрование");tls=segments(new String[]{"STARTTLS","SSL"},cfg.tls);formHelp("STARTTLS обычно использует порт 587, SSL — 465. Уточните параметры у почтового провайдера.");
        formLabel("Авторизация");auth=segments(new String[]{"AUTO","LOGIN","PLAIN","NONE"},cfg.auth);formHelp("AUTO выбирает способ входа автоматически. NONE — отправка без авторизации.");
        user=field("SMTP логин",cfg.user,false);password=field("Пароль SMTP",cfg.password,true);formHelp("Используйте пароль приложения. Для Gmail пробелы между группами 16-значного пароля удаляются при отправке.");
        
        from=field("Email отправителя",cfg.from,false);from.setHint("sender@example.com");formHelp("Если адрес пуст, будет использован SMTP-логин.");to=field("Email получателя",cfg.to,false);to.setHint("recipient@example.com");formHelp("Несколько адресов — через запятую или точку с запятой.");
        
        secondary("Помощь по SMTP",()->dialogBuilder().setTitle("Настройка SMTP").setMessage("STARTTLS обычно использует порт 587, SSL — 465. Уточняйте настройки у провайдера. AUTO выбирает LOGIN/PLAIN. NONE в авторизации — relay без входа; защищённое TLS-соединение обязательно. Принятие сервером не гарантирует попадание во Входящие — проверьте Спам.").setPositiveButton("Понятно",null).show());
        pair(()->secondary("Лог SMTP",this::showSmtpLog),()->secondary("Копировать лог",this::copySmtpLog),1,1);
        mailTestButton=button("Тестовое письмо",()->testChannel(false));mailResult=formHelp(ConnectionTests.emailStatus);

        section("Формат сообщений");formHelp("Общий шаблон для SMS, звонков и уведомлений в Telegram и на почту.");formLabel("Текст сообщения");
        templateField=new EditText(this);templateField.setId(viewId++);templateField.setText(cfg.messageTemplate);templateField.setTextSize(14);templateField.setIncludeFontPadding(false);templateField.setTextColor(UiColors.TEXT);UiLayout.background(templateField,border(UiColors.FIELD));templateField.setPadding(dp(12),dp(10),dp(12),dp(10));templateField.setGravity(Gravity.TOP|Gravity.START);templateField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);templateField.setMinLines(4);templateField.setMaxLines(8);templateField.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4000)});area.addView(templateField,new LinearLayout.LayoutParams(-1,-2));
        formHelp("Переносы строк сохраняются. Изменения применятся к новым событиям после сохранения.");
        pair(()->secondary("Предпросмотр",this::previewTemplate),()->secondary("По умолчанию",()->templateField.setText(MessageTemplate.DEFAULT)),1,1);
        formLabel("Добавить переменную");
        LinearLayout parent=area;LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.VERTICAL);parent.addView(chips);String[] variables={"time","date","type","data","title","message","number","sender","sim","app","package"};
        for(int row=0;row<(variables.length+1)/2;row++){LinearLayout line=new LinearLayout(this);line.setBaselineAligned(false);chips.addView(line,new LinearLayout.LayoutParams(-1,-2));for(int j=row*2;j<Math.min(variables.length,row*2+2);j++){String value="{{"+variables[j]+"}}";TextView chip=new TextView(this);chip.setText(value);chip.setTextColor(UiColors.TEXT);chip.setTextSize(12);chip.setIncludeFontPadding(false);chip.setGravity(Gravity.CENTER);chip.setTypeface(Typeface.MONOSPACE);chip.setPadding(dp(8),dp(8),dp(8),dp(8));chip.setMinHeight(dp(40));UiLayout.background(chip,border(UiColors.SUCCESS_BG));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);if(j%2==0)lp.rightMargin=dp(6);lp.topMargin=dp(6);line.addView(chip,lp);chip.setOnClickListener(v->{int start=Math.max(0,templateField.getSelectionStart()),end=Math.max(start,templateField.getSelectionEnd());templateField.getText().replace(start,end,value);});}}
        formHelp("Нажмите переменную, чтобы вставить её в позицию курсора.");
        secondary("Справка по переменным",this::templateHelp);
        section(" ");button("Сохранить настройки",()->save(false));
    }
    private void buildAccess(){
        sectionTab=2;section("Проверка доступа");area.setBackgroundColor(UiColors.BACKGROUND);area.setPadding(0,0,0,0);permissionStatus=note("");permissionStatus.setTextSize(20);permissionStatus.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));permissionStatus.setTextColor(UiColors.TEXT);
        accessCount=note("");accessCount.setTextSize(28);accessCount.setTypeface(null,Typeface.BOLD);accessCount.setTextColor(UiColors.ACCENT);accessCount.setPadding(0,dp(2),0,dp(8));
        LinearLayout progress=new LinearLayout(this);area.addView(progress);for(int i=0;i<accessSegments.length;i++){View bar=new View(this);accessSegments[i]=bar;LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(4),1);lp.setMargins(0,dp(8),dp(4),dp(8));progress.addView(bar,lp);}
        section("Нужно выдать");missingAccess=area;missingAccess.setBackgroundColor(UiColors.BACKGROUND);missingAccess.setPadding(0,0,0,0);
        section("Уже выдано");grantedAccess=area;
        section("Системные настройки");
        linkRow("Переподключить службу уведомлений","",this::reconnectListener,9);
        linkRow("Приостановка при неактивности","",this::manageUnusedApp,10);
        linkRow("Оптимизация батареи","",()->open(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)),11);
        linkRow("Карточка приложения","",this::openAppSettings,12);
        linkRow("Почему Android блокирует разрешения?","",this::permissionHelp,13);
    }
    private void grantedRow(String label){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setMinimumHeight(dp(60));row.setPadding(0,dp(10),0,dp(10));grantedAccess.addView(row,new LinearLayout.LayoutParams(-1,-2));
        ImageView check=new ImageView(this);check.setImageDrawable(new UiToggleArtwork(UiToggleArtwork.BADGE,getResources().getDisplayMetrics().density));row.addView(check,new LinearLayout.LayoutParams(dp(36),dp(36)));check.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        TextView text=new TextView(this);text.setText(label);text.setTextColor(UiColors.TEXT);text.setTextSize(14);text.setIncludeFontPadding(false);text.setGravity(Gravity.CENTER);text.setLineSpacing(dp(2),1);LinearLayout.LayoutParams words=new LinearLayout.LayoutParams(0,-2,1);words.leftMargin=dp(8);words.rightMargin=dp(8);row.addView(text,words);
        View balance=new View(this);balance.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(balance,new LinearLayout.LayoutParams(dp(36),dp(1)));
        View line=new View(this);line.setBackgroundColor(UiColors.BORDER);grantedAccess.addView(line,new LinearLayout.LayoutParams(-1,dp(1)));
    }
    private void updateAccess(boolean[] flags){
        String key=Arrays.toString(flags)+RelayListener.connected;if(key.equals(accessSignature))return;accessSignature=key;
        missingAccess.removeAllViews();grantedAccess.removeAllViews();LinearLayout old=area;
        String[] labels={"Получение SMS","Состояние телефона и SIM","Журнал звонков","Доступ к уведомлениям","Уведомления приложения","Уведомление о работе","Работа без ограничений","Имена отправителей из контактов"};
        String[] hints={"Для новых входящих SMS.","Для звонков и выбора SIM.","Для номера входящего звонка. Без него номер может быть недоступен.","Для уведомлений выбранных приложений.","Для отображения состояния RelayBridge.","Разрешите уведомления RelayBridge и канал состояния.","Исключение из оптимизации батареи для фоновой доставки.","Необязательно. Для имени отправителя SMS и звонка; без доступа номер и пересылка сохраняются."};
        Runnable[] actions={()->requestAccess(PermissionRequests.SMS),()->requestAccess(PermissionRequests.PHONE),()->requestAccess(PermissionRequests.CALL_LOG),()->open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),()->requestAccess(PermissionRequests.STATUS),this::openStatusNotificationSettings,this::requestUnrestrictedBackground,()->requestAccess(PermissionRequests.CONTACTS)};
        int count=0;for(int i=0;i<flags.length;i++){if(flags[i]){count++;grantedRow(labels[i]+(i==3&&RelayListener.connected?" · подключена":""));}else{LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(16),dp(16),dp(16));UiLayout.background(card,border(UiColors.SURFACE));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);missingAccess.addView(card,lp);area=card;title(labels[i],17);note(hints[i]);makeButton("Разрешить",actions[i],true);}}
        if(count==flags.length){area=missingAccess;note("Все разрешения выданы");}if(count==0){area=grantedAccess;note("Выданных разрешений пока нет");}
        permissionStatus.setText(count==flags.length?"Всё готово":"Разрешения");accessCount.setText(count+" / "+flags.length);for(int i=0;i<accessSegments.length;i++)accessSegments[i].setBackgroundColor(i<count?UiColors.ACCENT:UiColors.BORDER);area=old;
    }
    private void buildHistory(){
        sectionTab=3;section(" ");journal=note("");journal.setVisibility(View.GONE);area.setPadding(0,0,0,0);area.setBackgroundColor(UiColors.BACKGROUND);
        history=new HistoryView(this);area.addView(history,new LinearLayout.LayoutParams(-1,-2));
        section("Очередь и данные");
        secondary("Конфиденциальность",this::privacy);
        secondary("Отозвать согласие и удалить все данные",()->dialogBuilder().setTitle("Удалить данные RelayBridge?").setMessage("Пересылка будет остановлена. Удалятся настройки, пароли, локальная очередь и журнал. Уже отправленные копии в Telegram и почте останутся у получателей.").setPositiveButton("Удалить",(d,w)->{try{Indicator.stop(this);Events.clear(this);SmtpLog.clear();Config.prefs(this).edit().clear().commit();recreate();}catch(Exception e){toast("Не удалось удалить данные");}}).setNegativeButton("Отмена",null).show());
        secondary("Проверить и восстановить очередь",()->{Events.recover(this);updateStatus();toast("Ожидающие задания восстановлены");});
        secondary("Очистить очередь и журнал",()->dialogBuilder().setMessage("Удалить также ещё не доставленные события?").setPositiveButton("Удалить",(d,w)->{Events.clear(this);updateStatus();}).setNegativeButton("Отмена",null).show());
        note("Записи зашифрованы. Нажмите событие для подробностей. Хранится до 500 событий за 7 дней; Android может скрывать чувствительные уведомления.");
    }
    @Override public Object onRetainCustomNonConfigurationInstance(){
        if(token==null)return null;Draft d=new Draft();d.config=readForm(false);d.port=port.getText().toString();d.scroll=scroll.getScrollY();d.tab=selectedTab;return d;
    }
    @Override protected void onResume(){super.onResume();if(status!=null){try{Indicator.update(this,Config.load(this).enabled);}catch(Exception ignored){}updateSimSummary();updateSelectedApps();handler.removeCallbacks(refresh);handler.post(refresh);if(listenerGranted()&&!RelayListener.connected)rebindQuietly();}}
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
            dialogBuilder().setTitle("Зачем нужен доступ")
                .setMessage(permissionReason(request)).setPositiveButton("Продолжить",(d,w)->launchPermission(request))
                .setNegativeButton("Отмена",null).show();
        }else launchPermission(request);
    }
    private String permissionReason(int request){return switch(request){
        case PermissionRequests.SMS -> "RECEIVE_SMS позволяет получать новые SMS для выбранных SIM и пересылать их указанным вами получателям. Старые SMS не читаются. Без этого доступа SMS-пересылка недоступна; остальные функции остаются доступны.";
        case PermissionRequests.PHONE,PermissionRequests.SIM_PHONE,PermissionRequests.CALL_SIM_PHONE -> "Доступ к состоянию телефона нужен для события входящего звонка и определения активных SIM. Без него эти функции недоступны.";
        case PermissionRequests.CONTACTS -> "Доступ к контактам нужен только для локального поиска имени отправителя нового SMS или звонка. Имя будет включено в пересылаемое сообщение. Адресная книга целиком не отправляется. Доступ необязателен: без него пересылка по номеру продолжит работать.";
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
            if(request==PermissionRequests.STATUS)dialogBuilder().setTitle("Уведомление о работе отключено").setMessage("Разрешите уведомления RelayBridge в системных настройках. Это отдельный доступ от чтения уведомлений других приложений.").setPositiveButton("Открыть настройки",(d,w)->openStatusNotificationSettings()).setNegativeButton("Закрыть",null).show();
            else if(shouldShowRequestPermissionRationale(permission))toast("Доступ не выдан. Остальные функции доступны.");
            else dialogBuilder().setTitle("Доступ не выдан")
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
    private void permissionHelp(){dialogBuilder().setTitle("Разрешение не выдаётся Android")
        .setMessage("Для APK из файла Android 15 может блокировать SMS и доступ к уведомлениям до отдельного подтверждения.\n\nОткройте Настройки → Приложения → RelayBridge → меню ⋮ → «Разрешить ограниченные настройки». После подтверждения вернитесь и снова запросите разрешение.\n\nЕсли пункта нет или запрет сохраняется, установщик/прошивка или администратор может не разрешать этот доступ. Само приложение не может отменить запрет. Тесты Telegram и email работают без этих разрешений.")
        .setPositiveButton("Открыть карточку",(d,w)->openAppSettings()).setNegativeButton("Закрыть",null).show();}
    private void openAppSettings(){open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
    private void syncRelayToggle(boolean enabled){syncingRelayToggle=true;try{relayToggle.setChecked(enabled);}finally{syncingRelayToggle=false;}}
    private void updateStatus(){
        if(status==null)return;
        try{
            Config stored=Config.load(this);cfg.enabled=stored.enabled;
            boolean smsReady=granted(Manifest.permission.RECEIVE_SMS),phoneReady=granted(Manifest.permission.READ_PHONE_STATE),notificationsReady=listenerGranted();
            String listener=notificationsReady?(RelayListener.connected?"подключена":"доступ разрешён; служба не подключена"):"доступ не выдан / ограничен";
            PowerManager pm=getSystemService(PowerManager.class);
            String state=cfg.enabled?"Активна":"Остановлена";
            android.text.SpannableStringBuilder overview=new android.text.SpannableStringBuilder(state);
            overview.setSpan(new android.text.style.ForegroundColorSpan(cfg.enabled?UiColors.SUCCESS:UiColors.ERROR),0,state.length(),android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            status.setText(overview);status.setTextColor(UiColors.MUTED);status.setTextSize(20);status.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
            String routes=(stored.telegram?"Telegram":"")+(stored.telegram&&stored.email?" · ":"")+(stored.email?"Email":"");int sources=(stored.sms?1:0)+(stored.calls?1:0)+(stored.pushes?1:0);
            overviewDetails.setText((routes.isEmpty()?"Получатели не выбраны":routes)+" — источников: "+sources);syncRelayToggle(cfg.enabled);relayMode.setText(cfg.enabled?"ВКЛ":"ВЫКЛ");relayToggle.setContentDescription(cfg.enabled?"Выключить пересылку":"Включить пересылку");
            recipientSummary.setText(routes.isEmpty()?"Настройте получателей":routes);sourceSummary.setText((stored.sms?"SMS · ":"")+(stored.calls?"Звонки · ":"")+(stored.pushes?stored.apps.size()+" приложений":"Уведомления выключены"));
            List<String> missing=new ArrayList<>();if(stored.sms&&!smsReady)missing.add("SMS");if(stored.calls&&!phoneReady)missing.add("телефон");if(stored.calls&&!granted(Manifest.permission.READ_CALL_LOG))missing.add("номер звонка");if(stored.pushes&&!notificationsReady)missing.add("уведомления");
            boolean channelReady=stored.telegram||stored.email;overviewReadiness.setText(!channelReady?"Настройте отправку и выполните тест.":missing.isEmpty()?"Доступ к выбранным источникам разрешён.":"Нужен доступ: "+String.join(", ",missing));overviewReadiness.setTextColor(UiColors.MUTED);recipientSummary.setTextColor(UiColors.MUTED);sourceSummary.setTextColor(UiColors.MUTED);
            updateAccess(new boolean[]{smsReady,phoneReady,granted(Manifest.permission.READ_CALL_LOG),notificationsReady,granted(Manifest.permission.POST_NOTIFICATIONS),Indicator.allowed(this),pm.isIgnoringBatteryOptimizations(getPackageName()),granted(Manifest.permission.READ_CONTACTS)});
            long last=Config.prefs(this).getLong("last-capture",0);
            String received=last>0?"Последнее событие: "+android.text.format.DateFormat.format("dd.MM HH:mm:ss",last)+"\n\n":"Новых событий в очереди пока нет.\n\n";
            journal.setText(received+Config.prefs(this).getString("fault",""));
            if(selectedTab==3)history.refresh();if(selectedTab==0)refreshLastEvent();
            tgResult.setText(ConnectionTests.telegramStatus);mailResult.setText(ConnectionTests.emailStatus);
            tgTestButton.setEnabled(!ConnectionTests.telegramRunning);mailTestButton.setEnabled(!ConnectionTests.emailRunning);
        }catch(Exception e){status.setText("Ошибка чтения настроек. Закройте и снова откройте приложение.");}
    }
    private void refreshLastEvent(){
        long revision=QueueDb.get(this).revision();if(overviewLoading||revision==overviewRevision||overviewExecutor.isShutdown())return;overviewLoading=true;
        overviewExecutor.execute(()->{try{List<QueueDb.HistoryRow> rows=QueueDb.get(this).history();runOnUiThread(()->{overviewLoading=false;overviewRevision=revision;if(isDestroyed()||lastEvent==null)return;TextView heading=(TextView)((LinearLayout)lastEvent.getParent()).getChildAt(0);if(rows.isEmpty()){heading.setText("Новых событий пока нет");lastEvent.setText("Включите пересылку для захвата событий");}else{QueueDb.HistoryRow r=rows.get(0);heading.setText(HistoryView.displayTitle(r));lastEvent.setText(android.text.format.DateFormat.format("HH:mm · dd.MM",r.created)+" · Telegram: "+QueueDb.label(r.tg)+" · Email: "+QueueDb.label(r.mail));}});}catch(Exception e){runOnUiThread(()->overviewLoading=false);}});
    }
    @Override protected void onDestroy(){overviewExecutor.shutdownNow();super.onDestroy();}
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
    private void templateHelp(){dialogBuilder().setTitle("Переменные сообщений").setMessage("{{time}} — дата и время с часовым поясом\n{{date}} — дата\n{{type}} — тип события\n{{data}} — все данные события, включая отправителя/SIM или приложение\n{{title}} — заголовок уведомления, SMS от номера или Входящий звонок\n{{message}} — текст SMS/уведомления или номер звонка с подписью\n{{number}} — исходный номер отправителя/звонящего\n{{sender}} — имя из контактов или Неизвестный отправитель\n{{sim}} — SIM для SMS/звонка, если доступна\n{{app}} — название приложения\n{{package}} — пакет приложения\n\nНедоступные значения пустые. Переносы строк сохраняются. HTML/Markdown автоматически не включается. Тесты соединения отправляют свой технический текст; для шаблона используйте Предпросмотр. Нажмите Сохранить настройки для применения к новым событиям.").setPositiveButton("Понятно",null).show();}
    private String previewMessage(String template,Map<String,String> values){
        try{return MessageTemplate.render(template,values);}catch(IllegalArgumentException e){return "[Для этого типа событий текст пустой. Добавьте {{data}} или {{message}}.]";}
    }
    private void previewTemplate(){
        try{String template=templateField.getText().toString();MessageTemplate.validate(template);long now=System.currentTimeMillis();String sms=previewMessage(template,MessageTemplate.values("SMS","SMS от Алексей","Пример SMS",ContactNames.smsData("Алексей","+70000000000","Sim 1 MTS","Пример SMS"),"+70000000000","Sim 1 MTS","","","Алексей",now));
            String call=previewMessage(template,MessageTemplate.values("Входящий звонок","Входящий звонок · Мария","От: Мария\nНомер: +70000000000",ContactNames.callData("Мария","+70000000000","Sim 1 MTS"),"+70000000000","Sim 1 MTS","","","Мария",now));
            String push=previewMessage(template,MessageTemplate.values("Уведомление","Новое сообщение","Пример уведомления","Signal (org.thoughtcrime.securesms)\nНовое сообщение\nПример уведомления","","","Signal","org.thoughtcrime.securesms",now));
            TextView content=new TextView(this);content.setTextColor(UiColors.TEXT);content.setTextSize(14);content.setTextIsSelectable(true);content.setPadding(dp(16),dp(12),dp(16),dp(12));content.setText("SMS\n"+sms+"\n\nЗВОНОК\n"+call+"\n\nУВЕДОМЛЕНИЕ\n"+push);ScrollView view=new ScrollView(this);view.addView(content);dialogBuilder().setTitle("Предпросмотр · пример данных").setView(view).setPositiveButton("Закрыть",null).show();
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
        dialogBuilder().setTitle("Приостановка неиспользуемого приложения")
            .setMessage("В карточке RelayBridge отключите «Приостановить работу, если приложение не используется» или «Приостановить в неактивный период». Это также предотвращает автоматический отзыв разрешений при долгом неиспользовании. Изменение доступно в системных настройках.")
            .setPositiveButton("Открыть настройки",(d,w)->openAppSettings()).setNegativeButton("Закрыть",null).show();
    }
    private void showSmtpLog(){
        TextView content=new TextView(this);content.setTextColor(UiColors.TEXT);content.setTypeface(Typeface.MONOSPACE);content.setTextSize(12);content.setTextIsSelectable(true);content.setPadding(dp(14),dp(10),dp(14),dp(10));
        ScrollView view=new ScrollView(this);view.addView(content);
        AlertDialog dialog=dialogBuilder().setTitle("Лог SMTP · этапы и коды").setView(view).setPositiveButton("Закрыть",null).setNegativeButton("Копировать",null).setNeutralButton("Очистить",(d,w)->SmtpLog.clear()).create();
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
            if(!ready){dialogBuilder().setTitle("Нет готового источника")
                .setMessage("Выдайте доступ хотя бы к одному включённому источнику. Для push также выберите приложения. Тесты отправки доступны без разрешений.")
                .setPositiveButton("Разрешения Android",(d,w)->permissionHelp()).setNegativeButton("Закрыть",null).show();return;}
            String missing="";if(next.sms&&!granted(Manifest.permission.RECEIVE_SMS))missing+="\nSMS пока не будут пересылаться.";
            if(next.calls&&!granted(Manifest.permission.READ_PHONE_STATE))missing+="\nСобытия звонков пока недоступны.";
            if(next.pushes&&(!listenerGranted()||next.apps.isEmpty()))missing+="\nУведомления пока не настроены.";
            dialogBuilder().setTitle("Включить пересылку?")
                .setMessage("Новые события разрешённых источников уйдут указанным вами получателям. Подтвердите передачу SMS, номера звонящего и/или текста выбранных уведомлений."+missing)
                .setPositiveButton("Включить",(d,w)->save(true)).setNegativeButton("Отмена",null).show();
        }catch(IllegalArgumentException e){toast(e.getMessage());}
    }
    private void updateSimSummary(){
        updateSimSummary(simList,"SMS",cfg.smsAllSims,cfg.smsSubscriptions);
        updateSimSummary(callSimList,"Звонки",cfg.callAllSims,cfg.callSubscriptions);
    }
    private LinearLayout simStatus(){
        LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        area.addView(list,new LinearLayout.LayoutParams(-1,-2));return list;
    }
    private void updateSimSummary(LinearLayout list,String source,boolean all,Set<Integer> selected){
        if(list==null)return;
        renderSimSelection(list,source,all,selected,SimCards.active(this),SimCards.permitted(this));
    }
    private void renderSimSelection(LinearLayout list,String source,boolean all,Set<Integer> selected,List<SimCards.Card> cards,boolean permitted){
        list.removeAllViews();
        if(!all&&selected.isEmpty()){
            simCard(list,"SIM-карты не выбраны",source.equals("SMS")?"Захват SMS отключён":"Захват звонков отключён");return;
        }
        Set<Integer> shown=new HashSet<>();
        for(SimCards.Card card:cards)if(all||selected.contains(card.id)){
            simCard(list,card.label,all?"Выбрана автоматически":"Выбрана для "+source);shown.add(card.id);
        }
        if(!all){
            List<Integer> missing=new ArrayList<>(selected);missing.removeAll(shown);Collections.sort(missing);
            for(int id:missing)simCard(list,"SIM · ID "+id,permitted?"Сохранённая SIM сейчас недоступна":"Нужен доступ к состоянию телефона");
        }else{
            if(shown.isEmpty())simCard(list,"Все SIM-карты",permitted?"Активных SIM-карт сейчас нет":"Нужен доступ к состоянию телефона");
            TextView hint=new TextView(this);hint.setText("Автовыбор включает новые SIM и eSIM");hint.setTextColor(UiColors.MUTED);hint.setTextSize(11);hint.setIncludeFontPadding(false);hint.setGravity(Gravity.CENTER);hint.setLineSpacing(dp(2),1);hint.setPadding(dp(8),dp(4),dp(8),dp(8));list.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        }
    }
    private void simCard(LinearLayout list,String name,String detail){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setPadding(dp(12),dp(10),dp(12),dp(10));
        GradientDrawable shape=new GradientDrawable();shape.setColor(UiColors.SUCCESS_BG);shape.setCornerRadius(dp(12));shape.setStroke(dp(1),UiColors.BORDER);UiLayout.background(row,shape);
        LinearLayout.LayoutParams card=new LinearLayout.LayoutParams(-1,-2);card.topMargin=dp(6);card.bottomMargin=dp(4);list.addView(row,card);
        ImageView icon=new ImageView(this);icon.setImageDrawable(new UiIcon(8,UiColors.ACCENT,getResources().getDisplayMetrics().density));icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(dp(20),dp(24));image.rightMargin=dp(10);row.addView(icon,image);
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        TextView title=new TextView(this);title.setText(name);title.setTextSize(12);title.setTextColor(UiColors.TEXT);title.setIncludeFontPadding(false);title.setGravity(Gravity.CENTER);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setLineSpacing(dp(2),1);words.addView(title,new LinearLayout.LayoutParams(-1,-2));
        TextView caption=new TextView(this);caption.setText(detail);caption.setTextSize(11);caption.setTextColor(UiColors.MUTED);caption.setIncludeFontPadding(false);caption.setGravity(Gravity.CENTER);caption.setLineSpacing(dp(2),1);LinearLayout.LayoutParams note=new LinearLayout.LayoutParams(-1,-2);note.topMargin=dp(4);words.addView(caption,note);
        View spacer=new View(this);spacer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(spacer,new LinearLayout.LayoutParams(dp(30),1));
    }
    private void chooseSims(){chooseSims(false);}
    private void chooseSims(boolean forCalls){
        if(!SimCards.permitted(this)){
            dialogBuilder().setTitle("Доступ к SIM-картам").setMessage("Для списка SIM и названия оператора разрешите доступ к состоянию телефона. После разрешения откроется выбор SIM.")
                .setPositiveButton("Разрешить",(d,w)->requestAccess(forCalls?PermissionRequests.CALL_SIM_PHONE:PermissionRequests.SIM_PHONE)).setNegativeButton("Отмена",null).show();return;
        }
        boolean allSelected=forCalls?cfg.callAllSims:cfg.smsAllSims;
        Set<Integer> selectedIds=forCalls?cfg.callSubscriptions:cfg.smsSubscriptions;
        List<SimCards.Card> cards=SimCards.active(this);LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(12));
        CheckBox all=new CheckBox(this);all.setText("Все SIM-карты, включая новые");all.setTextColor(UiColors.TEXT);all.setButtonTintList(choiceTint());all.setChecked(allSelected);content.addView(all);
        List<CheckBox> choices=new ArrayList<>();for(SimCards.Card card:cards){CheckBox choice=new CheckBox(this);choice.setText(card.label);choice.setTextColor(UiColors.TEXT);choice.setButtonTintList(choiceTint());choice.setChecked(allSelected||selectedIds.contains(card.id));choice.setEnabled(!all.isChecked());content.addView(choice);choices.add(choice);}
        all.setOnCheckedChangeListener((v,on)->{for(CheckBox choice:choices)choice.setEnabled(!on);});
        TextView hint=new TextView(this);hint.setTextColor(UiColors.MUTED);hint.setTextSize(13);hint.setPadding(0,dp(12),0,0);
        hint.setText("Отключите «Все SIM-карты» и отметьте нужные. Без выбора захват этого источника отключён. После замены SIM или eSIM проверьте выбор снова."+(forCalls?" Если Android не сообщает SIM звонка и её нельзя однозначно определить, при выборе отдельных SIM звонок будет пропущен.":"")+(cards.isEmpty()?" Активные SIM не найдены.":""));content.addView(hint);
        ScrollView view=new ScrollView(this);view.addView(content);
        dialogBuilder().setTitle(forCalls?"SIM для захвата звонков":"SIM для захвата SMS").setView(view).setPositiveButton("Сохранить выбор",(d,w)->{
            try{Set<Integer> selected=new HashSet<>();if(!all.isChecked())for(int n=0;n<cards.size();n++)if(choices.get(n).isChecked())selected.add(cards.get(n).id);
                Config stored=Config.load(this);
                if(forCalls){stored.callAllSims=all.isChecked();stored.callSubscriptions.clear();stored.callSubscriptions.addAll(selected);cfg.callAllSims=stored.callAllSims;cfg.callSubscriptions.clear();cfg.callSubscriptions.addAll(selected);}
                else{stored.smsAllSims=all.isChecked();stored.smsSubscriptions.clear();stored.smsSubscriptions.addAll(selected);cfg.smsAllSims=stored.smsAllSims;cfg.smsSubscriptions.clear();cfg.smsSubscriptions.addAll(selected);}
                stored.save(this);updateSimSummary();toast("Выбор SIM сохранён");
            }catch(Exception e){toast("Не удалось сохранить выбор SIM");}
        }).setNegativeButton("Отмена",null).show();
    }
    private void updateSelectedApps(){
        if(selectedAppsList==null||overviewExecutor.isShutdown())return;
        Set<String> packages=new HashSet<>(cfg.apps);int generation=++selectedAppsGeneration;
        selectedAppsCount.setText("Выбрано приложений: "+packages.size());
        if(packages.isEmpty()){renderSelectedApps(Collections.emptyList());return;}
        overviewExecutor.execute(()->{
            List<AppRow> rows=new ArrayList<>();
            for(String pkg:packages){ApplicationInfo info;String label;
                try{info=getPackageManager().getApplicationInfo(pkg,0);label=info.loadLabel(getPackageManager()).toString();}
                catch(Exception unavailable){info=new ApplicationInfo();info.packageName=pkg;label="Приложение недоступно";}
                rows.add(new AppRow(info,label));
            }
            rows.sort(Comparator.comparing((AppRow app)->app.label.toLowerCase(Locale.ROOT)).thenComparing(app->app.info.packageName));
            runOnUiThread(()->{if(!isDestroyed()&&generation==selectedAppsGeneration)renderSelectedApps(rows);});
        });
    }
    private void renderSelectedApps(List<AppRow> apps){
        selectedAppsList.removeAllViews();
        if(apps.isEmpty()){
            TextView hint=new TextView(this);hint.setText("Приложения для пересылки уведомлений ещё не выбраны.");hint.setTextColor(UiColors.MUTED);hint.setTextSize(12);hint.setIncludeFontPadding(false);hint.setGravity(Gravity.CENTER);hint.setPadding(dp(8),dp(4),dp(8),dp(8));selectedAppsList.addView(hint,new LinearLayout.LayoutParams(-1,-2));return;
        }
        for(AppRow app:apps){
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(12),dp(12),dp(12));UiLayout.background(row,border(UiColors.SUCCESS_BG));LinearLayout.LayoutParams card=new LinearLayout.LayoutParams(-1,-2);card.topMargin=dp(6);card.bottomMargin=dp(4);selectedAppsList.addView(row,card);
            ImageView icon=new ImageView(this);Drawable drawable;try{drawable=app.info.loadIcon(getPackageManager());}catch(Exception ignored){drawable=getPackageManager().getDefaultActivityIcon();}icon.setImageDrawable(drawable);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams image=new LinearLayout.LayoutParams(dp(32),dp(32));image.rightMargin=dp(12);row.addView(icon,image);
            LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);row.addView(words,new LinearLayout.LayoutParams(0,-2,1));
            TextView name=new TextView(this);name.setText(app.label);name.setTextColor(UiColors.TEXT);name.setTextSize(14);name.setIncludeFontPadding(false);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));words.addView(name,new LinearLayout.LayoutParams(-1,-2));
            TextView pkg=new TextView(this);pkg.setText(app.info.packageName);pkg.setTextColor(UiColors.MUTED);pkg.setTextSize(11);pkg.setTypeface(Typeface.MONOSPACE);pkg.setIncludeFontPadding(false);pkg.setTextDirection(View.TEXT_DIRECTION_LTR);pkg.setPadding(0,dp(4),0,0);words.addView(pkg,new LinearLayout.LayoutParams(-1,-2));
        }
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
        appDialog=dialogBuilder().setTitle("Приложения для пересылки").setView(layout).setPositiveButton("Сохранить выбор",(d,w)->{
            try{Config stored=Config.load(this);stored.apps.clear();stored.apps.addAll(selected);stored.save(this);cfg.apps.clear();cfg.apps.addAll(selected);updateSelectedApps();updateStatus();toast("Выбор приложений сохранён");}
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
                h.box=new CheckBox(MainActivity.this);h.box.setButtonTintList(choiceTint());row.addView(h.box);row.setTag(h);
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
    private void title(String text,int size){TextView label=note(text);label.setIncludeFontPadding(false);label.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);label.setTextSize(size);label.setTextColor(UiColors.TEXT);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));label.setPadding(0,0,0,dp(4));}
    private void section(String caption){
        area=page;TextView heading=note(caption.toUpperCase(Locale.ROOT));heading.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);heading.setTextSize(11);heading.setTextColor(UiColors.ACCENT);heading.setLetterSpacing(0.12f);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));heading.setPadding(0,dp(22),0,dp(10));if(caption.isBlank())heading.setVisibility(View.GONE);
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(14),dp(16),dp(14));UiLayout.background(card,border(UiColors.SURFACE));page.addView(card,new LinearLayout.LayoutParams(-1,-2));sections.add(heading);sectionTabs.add(sectionTab);sections.add(card);sectionTabs.add(sectionTab);area=card;
    }
    private void selectTab(int tab){
        selectedTab=tab;if(brandTitle!=null)brandTitle.setText(new String[]{"RelayBridge","Отправка","Доступ","Журнал"}[tab]);for(int i=0;i<sections.size();i++)sections.get(i).setVisibility(sectionTabs.get(i)==tab&&!(sections.get(i) instanceof TextView&&((TextView)sections.get(i)).getText().toString().isBlank())?View.VISIBLE:View.GONE);
        for(int i=0;i<4;i++){int color=i==tab?UiColors.ACCENT:UiColors.MUTED;navLabels[i].setTextColor(color);navIcons[i].setImageDrawable(new UiIcon(i,color,getResources().getDisplayMetrics().density));navigation.getChildAt(i).setSelected(i==tab);}

        scroll.smoothScrollTo(0,0);if(tab==3&&history!=null)history.refresh();
    }
    private GradientDrawable border(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));d.setStroke(dp(1),UiColors.BORDER);return d;}
    private AlertDialog.Builder dialogBuilder(){
        return new AlertDialog.Builder(this){
            @Override public AlertDialog create(){
                AlertDialog dialog=super.create();
                dialog.setOnShowListener(ignored->{UiLayout.styleDialogButtons(dialog);TextView message=dialog.findViewById(android.R.id.message);if(message!=null){if(selectedTab==1)message.setGravity(Gravity.CENTER);}});
                return dialog;
            }
        };
    }
    private TextView formLabel(String text){TextView label=note(text);label.setTextColor(UiColors.TEXT);label.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);label.setTextSize(12);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));label.setPadding(0,dp(10),0,dp(6));return label;}
    private TextView formHelp(String text){TextView hint=note(text);hint.setTextColor(UiColors.TEXT);hint.setTextSize(11);hint.setLineSpacing(dp(2),1);hint.setPadding(0,dp(4),0,dp(10));return hint;}
    private TextView note(String text){TextView t=new TextView(this);t.setIncludeFontPadding(false);t.setGravity(sectionTab==1?Gravity.CENTER:Gravity.START|Gravity.CENTER_VERTICAL);t.setText(text);t.setTextSize(13);t.setTextColor(UiColors.MUTED);t.setLineSpacing(dp(3),1);t.setPadding(0,dp(4),0,dp(6));area.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private CompoundButton check(String text,boolean value){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setBaselineAligned(false);row.setMinimumHeight(dp(56));row.setPadding(0,dp(8),0,dp(8));area.addView(row,new LinearLayout.LayoutParams(-1,-2));
        int kind=text.contains("SMS")?4:text.contains("звон")?5:text.equals("Уведомления")?6:text.equals("Telegram")?1:text.equals("Email / SMTP")?7:-1;
        if(kind>=0){ImageView icon=new ImageView(this);icon.setImageDrawable(new UiIcon(kind,UiColors.TEXT,getResources().getDisplayMetrics().density));icon.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(dp(24),dp(24));ip.rightMargin=dp(12);row.addView(icon,ip);}
        TextView label=new TextView(this);label.setText(text);label.setTextColor(UiColors.TEXT);label.setTextSize(kind>=0?15:13);label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER_VERTICAL);if(kind>=0)label.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams words=new LinearLayout.LayoutParams(0,-2,1);words.rightMargin=dp(12);row.addView(label,words);
        Switch toggle=new UiSwitch(this);toggle.setChecked(value);toggle.setContentDescription(text);row.addView(toggle,new LinearLayout.LayoutParams(-2,-2));row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));label.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));return toggle;
    }
    private void divider(){View line=new View(this);line.setBackgroundColor(UiColors.BORDER);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.topMargin=dp(8);lp.bottomMargin=dp(8);area.addView(line,lp);}
    private TextView linkRow(String heading,String detail,Runnable action,int icon){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));area.addView(row,new LinearLayout.LayoutParams(-1,-2));
        ImageView image=new ImageView(this);image.setImageDrawable(new UiIcon(icon,UiColors.TEXT,getResources().getDisplayMetrics().density));image.setPadding(dp(10),dp(10),dp(10),dp(10));UiLayout.background(image,border(UiColors.SUCCESS_BG));row.addView(image,new LinearLayout.LayoutParams(dp(44),dp(44)));
        LinearLayout words=new LinearLayout(this);words.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.leftMargin=dp(12);row.addView(words,lp);LinearLayout old=area;area=words;title(heading,16);TextView subtitle=note(detail);subtitle.setPadding(0,dp(4),0,0);if(detail.isEmpty())subtitle.setVisibility(View.GONE);area=old;
        TextView arrow=new TextView(this);arrow.setText("›");arrow.setIncludeFontPadding(false);arrow.setGravity(Gravity.CENTER);arrow.setTextSize(26);arrow.setTextColor(UiColors.MUTED);row.addView(arrow,new LinearLayout.LayoutParams(dp(24),dp(32)));row.setFocusable(true);row.setOnClickListener(v->action.run());return subtitle;
    }
    private ColorStateList choiceTint(){return new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{android.R.attr.state_checked},new int[]{}},new int[]{UiColors.BORDER,UiColors.ACCENT,UiColors.MUTED});}
    private Spinner segments(String[] values,String selected){
        Spinner spinner=new Spinner(this);spinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));int pos=Arrays.asList(values).indexOf(selected);spinner.setSelection(Math.max(0,pos));spinner.setVisibility(View.GONE);area.addView(spinner);
        LinearLayout row=new LinearLayout(this);row.setPadding(dp(3),dp(3),dp(3),dp(3));UiLayout.background(row,border(UiColors.FIELD));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(10);area.addView(row,lp);
        Runnable paint=()->{for(int k=0;k<row.getChildCount();k++){Button button=(Button)row.getChildAt(k);boolean on=k==spinner.getSelectedItemPosition();button.setTextColor(UiColors.TEXT);UiLayout.background(button,border(on?UiColors.ACCENT:UiColors.FIELD));}};
        for(int k=0;k<values.length;k++){final int index=k;Button button=new Button(this);button.setText(values[k]);button.setTextSize(11);button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER);button.setAllCaps(false);button.setPadding(0,0,0,0);button.setMinWidth(0);button.setMinimumWidth(0);row.addView(button,new LinearLayout.LayoutParams(0,-1,1));button.setOnClickListener(v->{spinner.setSelection(index);paint.run();});}paint.run();return spinner;
    }
    private void styleSpinner(Spinner spinner){UiLayout.background(spinner,border(UiColors.FIELD));spinner.setPadding(dp(10),0,dp(10),0);spinner.setMinimumHeight(dp(48));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(8);spinner.setLayoutParams(lp);}
    private EditText field(String label,String value,boolean secret){
        formLabel(label);EditText input=new EditText(this);input.setId(viewId++);input.setSingleLine();input.setIncludeFontPadding(false);input.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);input.setText(value);input.setTextSize(15);input.setTextColor(UiColors.TEXT);input.setHintTextColor(UiColors.MUTED);input.setTextDirection(View.TEXT_DIRECTION_LTR);UiLayout.background(input,border(UiColors.FIELD));input.setPadding(dp(14),0,dp(14),0);input.setInputType(InputType.TYPE_CLASS_TEXT|(secret?InputType.TYPE_TEXT_VARIATION_PASSWORD:InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS));
        if(secret){input.setTransformationMethod(PasswordTransformationMethod.getInstance());input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);input.setSaveEnabled(false);}
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(48));lp.bottomMargin=dp(8);area.addView(input,lp);return input;
    }
    private Button button(String caption,Runnable action){return makeButton(caption,action,sectionTab!=2);}
    private Button secondary(String caption,Runnable action){return makeButton(caption,action,false);}
    private Button makeButton(String caption,Runnable action,boolean primary){Button button=new Button(this);button.setText(caption);button.setAllCaps(false);button.setTextSize(14);button.setIncludeFontPadding(false);button.setGravity(Gravity.CENTER);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));button.setTextColor(UiColors.TEXT);UiLayout.background(button,new RippleDrawable(ColorStateList.valueOf(0x22777777),border(primary?UiColors.ACCENT:UiColors.SURFACE),border(UiColors.TEXT)));button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(dp(48));button.setPadding(dp(10),dp(8),dp(10),dp(8));button.setOnClickListener(v->action.run());LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(3);lp.bottomMargin=dp(3);area.addView(button,lp);return button;}
}
