package com.saphir.benji.saphir;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Message;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.AppCompatCheckBox;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Time;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;



public class MainActivity extends AppCompatActivity {

    private TextView mStartTime, mEndTime;
    private Button mB_StartTimer, mB_EndTimer, mB_Mail,mB_Quit;

    private TimerService mTimerService;
    private ComputeHoursService mComputeHours;

    //Handler to update the UI while timer is running
    private final Handler mUpdateTimeHandler= new UIUpdateHandler(this);

    //Message type for the handler
    private final static int  MSG_UPDATE_TIME=0;
    //To know if the timer is bound
    private boolean mServiceBound;

    private String TAG_Start ="StartTimerButton : ";
    private String TAG_End ="EndTimerButton : ";
    private String TAG_Mail ="MailButton : ";
    private String TAG="MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mStartTime = (TextView) findViewById(R.id.StartTime);
        mEndTime = (TextView) findViewById(R.id.EndTime);
        mB_StartTimer = (Button) findViewById(R.id.StartTimer);
        mB_EndTimer = (Button) findViewById(R.id.EndTimer);
        mB_Mail = (Button) findViewById(R.id.Mail);
        mB_Quit = (Button) findViewById(R.id.QuitButton);

        ifHuaweiAlert();
        /*
         *  StartTimer button listener
         */
        mB_StartTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mServiceBound && !mTimerService.isTimerRunning()) {
                    Log.v(TAG_Start,"StartingTimer");
                    mTimerService.startTimer();
                    mComputeHours.getDate();
                    updateUIStartRun();
                }
            }
        });

        /*
         *  EndTimer button listener
         */
        mB_EndTimer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mServiceBound && mTimerService.isTimerRunning()) {
                    long elapsedTimeInSeconds= mTimerService.elapsedTime();
                    Log.v(TAG_End,"Stopping Timer");
                    mComputeHours.getAgentStatus(elapsedTimeInSeconds);
                    mTimerService.stopTimer();
                    updateUIStopRun();
                    mEndTime.setText(mComputeHours.printWorkTime());
                }
            mComputeHours.foreground();
            }
        });

        /*
         * Mail button listener
         */
        mB_Mail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i(TAG_Mail,"Mail button pressed");
                sendMail();
            }
        });

        mB_Quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopService(new Intent(MainActivity.this, TimerService.class));
                stopService(new Intent(MainActivity.this,ComputeHoursService.class));
                unbindService(mTimerConnection);
                unbindService(mComputeConnection);
                File file = getFile();
                file.delete();
                finishAffinity();
                System.exit(0);
            }
        });
    }

    @Override
    protected void onStart(){
        super.onStart();
        Log.v(TAG,"Starting and binding service");
        //Binding TimerService
        Intent intent = new Intent(MainActivity.this,TimerService.class);
        startService(intent);
        bindService(intent,mTimerConnection,0);
        //Binding ComputeHoursService
        Intent ComputeIntent = new Intent(MainActivity.this,ComputeHoursService.class);
        startService(ComputeIntent);
        bindService(ComputeIntent,mComputeConnection,0);
    }

    @Override
    protected void onStop(){
        super.onStop();
        updateUIStopRun();
       if(mServiceBound){
           //if a timer is active, foreground the service, else kill it
           if(mTimerService.isTimerRunning() ){
               mTimerService.foreground();
           }
           else{
               stopService(new Intent(this,TimerService.class));
           }
           unbindService(mTimerConnection);
           mServiceBound=false;
       }
    }

    /**
     * Update the UI when a run starts
     */
    private void updateUIStartRun(){
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
        mB_StartTimer.setEnabled(false);
        mB_EndTimer.setEnabled(true);
    }

    /**
     * Update the UI when a run stops
     */
    private void updateUIStopRun(){
        mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
        mB_EndTimer.setEnabled(false);
        mB_StartTimer.setEnabled(true);
    }

    /**
     * Update the timer ; Service must be bound
     */
    private void updateUITimer(){
        if(mServiceBound && mTimerService.isTimerRunning()){
            //mElapsedTime.setText(mTimerService.elapsedTime()+"");
            Calendar calendar = Calendar.getInstance();
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy--HH:mm:ss");
            String formatDate= sdf.format(calendar.getTime());
            mStartTime.setText(formatDate);
        }
    }

    /**
     * Callback for service binding, passed down to bindService()
     */
    private ServiceConnection mTimerConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG,"Service bound");
            TimerService.RunServiceBinder binder = (TimerService.RunServiceBinder) service;
            mTimerService = binder.getService();
            mServiceBound=true;

            //Making sure service isn't in background when bounding
            mTimerService.background();

            //Update the UI if the service is already running the timer
            if(mTimerService.isTimerRunning()){
                updateUIStartRun();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG,"Service unbound");
            mServiceBound = false;
        }
    };

    private ServiceConnection mComputeConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.v(TAG,"Service bound");
            ComputeHoursService.RunServiceBinder binder = (ComputeHoursService.RunServiceBinder) service;
            mComputeHours = binder.getService();
            //Making sure service isn't in background when bounding
            mComputeHours.background();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.v(TAG,"Service unbound");
        }
    };

    /**
     * When the timer is running using this handler to update
     * the UI every second
     */
    static class UIUpdateHandler extends Handler{

        private final static int UPDATE_RATE_MS=1000;
        private String TAG="UIUpdateHandler";
        private final WeakReference<MainActivity> activityWeakReference;

        UIUpdateHandler(MainActivity activity){
            this.activityWeakReference = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message message){
            if(MSG_UPDATE_TIME == message.what) {
                Log.v(TAG, "updating time");
                activityWeakReference.get().updateUITimer();
                sendEmptyMessageDelayed(MSG_UPDATE_TIME, UPDATE_RATE_MS);
            }
        }
    }

    private File getFile(){
        String pathToFile=getExternalCacheDir()+"/Saphir/agents.txt";
        File file = new File(pathToFile);
        if(!file.exists() || !file.canRead()){
            Log.e(TAG,"Error reading/accessing the file");
        }
        return file;
    }

    private void sendMail(){
        String [] To={"benjamin.leguen974@gmail.com"};
        String [] Subject={"Rapport Agent Astreinte"};
        String [] Body = {"Voir pièce jointe"};

        //Attaching file to mail
        Uri fileUri= Uri.fromFile(getFile());

        //Creating mail
        Intent emailIntent = new Intent(Intent.ACTION_SEND);
        emailIntent.setType("text/plain");
        emailIntent.putExtra(Intent.EXTRA_EMAIL,To);
        emailIntent.putExtra(Intent.EXTRA_SUBJECT,Subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT,"Voir pièce jointe");
        emailIntent.putExtra(Intent.EXTRA_STREAM,fileUri);

        //Sending mail
        try{
            startActivity(Intent.createChooser(emailIntent,"Choisissez votre application de messagerie (GMail,Outlook...)"));
            finish();
        }catch (android.content.ActivityNotFoundException ex){
            Log.e(TAG,"There is no mail client installed "+ ex.toString());
        }
    }

    /**
     * Specific code to Huawei protected app feature
     */
    private void ifHuaweiAlert() {
        final SharedPreferences settings = getSharedPreferences("ProtectedApps", MODE_PRIVATE);
        final String saveIfSkip = "skipProtectedAppsMessage";
        boolean skipMessage = settings.getBoolean(saveIfSkip, false);
        if (!skipMessage) {
            final SharedPreferences.Editor editor = settings.edit();
            Intent intent = new Intent();
            intent.setClassName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity");
            if (isCallable(intent)) {
                final AppCompatCheckBox dontShowAgain = new AppCompatCheckBox(this);
                dontShowAgain.setText("Do not show again");
                dontShowAgain.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        editor.putBoolean(saveIfSkip, isChecked);
                        editor.apply();
                    }
                });

                new AlertDialog.Builder(this)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("Huawei Protected Apps")
                        .setMessage(String.format("%s requires to be enabled in 'Protected Apps' to function properly.%n", getString(R.string.STR_AppName)))
                        .setView(dontShowAgain)
                        .setPositiveButton("Protected Apps", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                huaweiProtectedApps();
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            } else {
                editor.putBoolean(saveIfSkip, true);
                editor.apply();
            }
        }
    }
    private boolean isCallable(Intent intent) {
        List<ResolveInfo> list = getPackageManager().queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return list.size() > 0;
    }
    private void huaweiProtectedApps() {
        try {
            String cmd = "am start -n com.huawei.systemmanager/.optimize.process.ProtectActivity";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                cmd += " --user " + getUserSerial();
            }
            Runtime.getRuntime().exec(cmd);
        } catch (IOException ignored) {
        }
    }
    private String getUserSerial() {
        //noinspection ResourceType
        Object userManager = getSystemService("user");
        if (null == userManager) return "";

        try {
            Method myUserHandleMethod = android.os.Process.class.getMethod("myUserHandle", (Class<?>[]) null);
            Object myUserHandle = myUserHandleMethod.invoke(android.os.Process.class, (Object[]) null);
            Method getSerialNumberForUser = userManager.getClass().getMethod("getSerialNumberForUser", myUserHandle.getClass());
            Long userSerial = (Long) getSerialNumberForUser.invoke(userManager, myUserHandle);
            if (userSerial != null) {
                return String.valueOf(userSerial);
            } else {
                return "";
            }
        } catch (NoSuchMethodException | IllegalArgumentException | InvocationTargetException | IllegalAccessException ignored) {
        }
        return "";
    }
}
