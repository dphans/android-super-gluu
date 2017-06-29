/*
 *  oxPush2 is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 *  Copyright (c) 2014, Gluu
 */

package org.gluu.super_gluu.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Vibrator;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

import com.android.vending.billing.IInAppBillingService;
import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.PurchaseState;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.gson.Gson;
import org.gluu.super_gluu.app.activities.GluuApplication;
import org.gluu.super_gluu.app.activities.MainActivity;
import org.gluu.super_gluu.app.customGluuAlertView.CustomGluuAlert;
import org.gluu.super_gluu.app.fragments.PinCodeFragment.PinCodeFragment;
import org.gluu.super_gluu.app.listener.OxPush2RequestListener;
import org.gluu.super_gluu.app.settings.Settings;
import org.gluu.super_gluu.model.OxPush2Request;
import org.gluu.super_gluu.net.CommunicationService;
import org.gluu.super_gluu.store.AndroidKeyDataStore;
import org.gluu.super_gluu.u2f.v2.SoftwareDevice;
import org.gluu.super_gluu.u2f.v2.exception.U2FException;
import org.gluu.super_gluu.u2f.v2.model.TokenResponse;
import org.gluu.super_gluu.u2f.v2.store.DataStore;
import org.gluu.super_gluu.device.DeviceUuidManager;
import org.gluu.super_gluu.util.Utils;
import org.json.JSONException;

import java.io.IOException;
import SuperGluu.app.BuildConfig;
import SuperGluu.app.R;

/**
 * Main activity
 *
 * Created by Yuriy Movchan on 12/28/2015.
 */
public class GluuMainActivity extends AppCompatActivity implements OxPush2RequestListener, KeyHandleInfoFragment.OnDeleteKeyHandleListener, PinCodeFragment.PinCodeViewListener {

    private static final String TAG = "main-activity";

    /**
     * Id to identify a camera permission request.
     */
    private static final int REQUEST_CAMERA = 0;

    public static final String QR_CODE_PUSH_NOTIFICATION_MESSAGE = GluuMainActivity.class.getPackage().getName() + ".QR_CODE_PUSH_NOTIFICATION_MESSAGE";
    public static final String QR_CODE_PUSH_NOTIFICATION = "QR_CODE_PUSH_NOTIFICATION";
    public static final int MESSAGE_NOTIFICATION_ID = 444555;

    private SoftwareDevice u2f;
    private AndroidKeyDataStore dataStore;
    private static Context context;

    private Boolean isShowClearMenu = false;

    Bundle querySkus;
    IInAppBillingService inAppBillingService;

    ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            inAppBillingService = IInAppBillingService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            inAppBillingService = null;
        }
    };

    private BroadcastReceiver mPushMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
            tabLayout.getTabAt(0).select();

            // Get extra data included in the Intent
            String message = intent.getStringExtra(GluuMainActivity.QR_CODE_PUSH_NOTIFICATION_MESSAGE);
            final OxPush2Request oxPush2Request = new Gson().fromJson(message, OxPush2Request.class);
            onQrRequest(oxPush2Request);
            //play sound and vibrate
            try {
                Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                Ringtone r = RingtoneManager.getRingtone(context, notification);
                r.play();
            } catch (Exception e) {
                e.printStackTrace();
            }
            ((Vibrator)getApplication().getSystemService(Context.VIBRATOR_SERVICE)).vibrate(800);
        }
    };

    //For purchases
    // PRODUCT & SUBSCRIPTION IDS
    private static final String SUBSCRIPTION_ID = "org.gluu.monthly.ad.free";
    private static final String SUBSCRIPTION_ID_TEST = "android.test.purchased";
    private static final String LICENSE_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyYw9xTiyhyjQ6mnWOwEWduDkOM84BkqHfN+jrAu82M0xBwg3RAorPwT/38sMcOZMAwcWudN0vjQo7uXAl2j4+N7BiMI2qlO2x33wY8fDvlN4ue54BBdZExZhTpkVEAmIm9cLCI3i+nOlUZgiwX6+sQOb5K+7q9WiNuSBDWRR2WDNOY7QmQdI1VzbHBPQoM00N9/0UDSFCw4LCRngm7ZeuW8AQMyYo6r5K3dy8m+Ys0JWGKA+xuQY4ZutSb47IYX4m7lzxbN0mqH9TLeA3V6audrhs5i0OYYKwbCd68NikB7Wco6L/HOzh1y6LoxIFXZ6M+vnZ6OLfTJuVmEfTOOhIwIDAQAB";//"Your public key, don't forget abput encryption"; // PUT YOUR MERCHANT KEY HERE;
    // put your Google merchant id here (as stated in public profile of your Payments Merchant Center)
    // if filled library will provide protection against Freedom alike Play Market simulators
    private static final String MERCHANT_ID=null;
    private BillingProcessor bp;
    private boolean readyToPurchase = false;
    private boolean isSubscribed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Get the view from gluu_activity_main_main.xml
        setContentView(R.layout.gluu_activity_main);
        context = getApplicationContext();

        //Init main tab vie and pager
        initMainTabView();

        LocalBroadcastManager.getInstance(this).registerReceiver(mPushMessageReceiver,
                new IntentFilter(GluuMainActivity.QR_CODE_PUSH_NOTIFICATION));

        // Init network layer
        CommunicationService.init();

        // Init device UUID service
        DeviceUuidManager deviceUuidFactory = new DeviceUuidManager();
        deviceUuidFactory.init(this);

        this.dataStore = new AndroidKeyDataStore(context);
        this.u2f = new SoftwareDevice(this, dataStore);
        setIsButtonVisible(dataStore.getLogs().size() != 0);

        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setIcon(R.drawable.ic_title_icon);

        // Check if we get push notification
        checkIsPush();

        checkUserCameraPermission();

        //temporary turn off rotation
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

        //Init GoogleMobile AD
        initGoogleADS(isSubscribed);

        //Init InAPP-Purchase service
        initIAPurchaseService();
    }

    private void initGoogleADS(Boolean isShow){
        AdView mAdView = (AdView) findViewById(R.id.adView);
        if (!isShow) {
            MobileAds.initialize(getApplicationContext(), "ca-app-pub-3932761366188106~2301594871");
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
        } else {
            ViewGroup.LayoutParams params = mAdView.getLayoutParams();
            params.height = 0;
            mAdView.setLayoutParams(params);
        }
        Intent intent = new Intent("on-ad-free-event");
        // You can also include some extra data.
        intent.putExtra("isAdFree", isShow);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void initIAPurchaseService(){
        if(!BillingProcessor.isIabServiceAvailable(this)) {
            Log.e(TAG, "In-app billing service is unavailable, please upgrade Android Market/Play to version >= 3.9.16");
        }

        bp = new BillingProcessor(this, LICENSE_KEY, MERCHANT_ID, new BillingProcessor.IBillingHandler() {
            @Override
            public void onProductPurchased(String productId, TransactionDetails details) {
                Log.e(TAG, "onProductPurchased: " + productId);
                isSubscribed = details.purchaseInfo.purchaseData.autoRenewing;
                //Init GoogleMobile AD
                //isSubscribed &&
                initGoogleADS(productId.equalsIgnoreCase(SUBSCRIPTION_ID_TEST));
            }
            @Override
            public void onBillingError(int errorCode, Throwable error) {
                Log.e(TAG, "onBillingError: " + Integer.toString(errorCode));
            }
            @Override
            public void onBillingInitialized() {
                Log.e(TAG, "onBillingInitialized");
                readyToPurchase = true;
                TransactionDetails transactionDetails = bp.getSubscriptionTransactionDetails(SUBSCRIPTION_ID_TEST);
                if (transactionDetails != null) {
                    isSubscribed = transactionDetails.purchaseInfo.purchaseData.autoRenewing;
                }
                TransactionDetails transactionDetails2 = bp.getPurchaseTransactionDetails(SUBSCRIPTION_ID_TEST);
                if (transactionDetails2 != null) {
                    isSubscribed = transactionDetails2.purchaseInfo.purchaseData.purchaseState == PurchaseState.PurchasedSuccessfully;
                }
                //Init GoogleMobile AD
                initGoogleADS(isSubscribed);
            }
            @Override
            public void onPurchaseHistoryRestored() {
                Log.e(TAG, "onPurchaseHistoryRestored");
                for(String sku : bp.listOwnedProducts())
                    Log.e(TAG, "Owned Managed Product: " + sku);
                for(String sku : bp.listOwnedSubscriptions())
                    Log.e(TAG, "Owned Subscription: " + sku);
            }
        });
    }

    private void initMainTabView(){
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText("Home").setIcon(R.drawable.home_action));
        tabLayout.addTab(tabLayout.newTab().setText("Logs").setIcon(R.drawable.logs_action));
        tabLayout.addTab(tabLayout.newTab().setText("Keys").setIcon(R.drawable.keys_action));
        tabLayout.addTab(tabLayout.newTab().setText("Settings").setIcon(R.drawable.settings_action));
        tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);

        // Locate the viewpager in gluu_activity_main.xmln.xml
        final ViewPager viewPager = (ViewPager) findViewById(R.id.pager);//GluuPagerView
//        viewPager.setSwipeLocked(true);

        // Set the ViewPagerAdapter into ViewPager
        viewPager.setAdapter(new ViewPagerAdapter(getSupportFragmentManager()));//, tabLayout.getTabCount()
        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
        final int tabIconColor = ContextCompat.getColor(context, R.color.greenColor);
        final int tabIconColorBlack = ContextCompat.getColor(context, R.color.blackColor);
        tabLayout.getTabAt(0).getIcon().setColorFilter(tabIconColor, PorterDuff.Mode.SRC_IN);
        tabLayout.getTabAt(1).getIcon().setColorFilter(tabIconColorBlack, PorterDuff.Mode.SRC_IN);
        tabLayout.getTabAt(2).getIcon().setColorFilter(tabIconColorBlack, PorterDuff.Mode.SRC_IN);
        tabLayout.getTabAt(3).getIcon().setColorFilter(tabIconColorBlack, PorterDuff.Mode.SRC_IN);
        tabLayout.setOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                isShowClearMenu = position == 1 ? true : false;
                reloadLogs();
                invalidateOptionsMenu();
                viewPager.setCurrentItem(position);
                tab.getIcon().setColorFilter(tabIconColor, PorterDuff.Mode.SRC_IN);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                tab.getIcon().setColorFilter(tabIconColorBlack, PorterDuff.Mode.SRC_IN);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    @Override
    public void onSaveInstanceState( Bundle outState ) {
        //skip it at all
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isShowClearMenu && getIsButtonVisible()) {//&& dataStore.getLogs().size() > 0
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.clear_logs_menu, menu);
        }
        return true;
    }

    private void reloadLogs(){
        Intent intent = new Intent("reload-logs");
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        setIsButtonVisible(dataStore.getLogs().size() != 0);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        GluuAlertCallback listener = new GluuAlertCallback(){
            @Override
            public void onPositiveButton() {
                dataStore.deleteLogs();
                reloadLogs();
                invalidateOptionsMenu();
            }
        };
        CustomGluuAlert gluuAlert = new CustomGluuAlert(GluuMainActivity.this);
        gluuAlert.setMessage(getApplicationContext().getString(R.string.clear_logs));
        gluuAlert.setYesTitle(getApplicationContext().getString(R.string.yes));
        gluuAlert.setNoTitle(getApplicationContext().getString(R.string.no));
        gluuAlert.setmListener(listener);
        gluuAlert.show();
        return super.onOptionsItemSelected(item);
    }

    public void doQrRequest(OxPush2Request oxPush2Request) {
        if (!validateOxPush2Request(oxPush2Request)) {
            return;
        }
        Settings.setPushDataEmpty(getApplicationContext());
        NotificationManager nMgr = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nMgr.cancel(GluuMainActivity.MESSAGE_NOTIFICATION_ID);
        final ProcessManager processManager = createProcessManager(oxPush2Request);
        ApproveDenyFragment approveDenyFragment = new ApproveDenyFragment();
        approveDenyFragment.setIsUserInfo(false);
        approveDenyFragment.setPush2Request(oxPush2Request);
        approveDenyFragment.setListener(new RequestProcessListener() {
            @Override
            public void onApprove() {
                processManager.onOxPushRequest(false);
            }

            @Override
            public void onDeny() {
                processManager.onOxPushRequest(true);
            }
        });
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.replace(R.id.main_root_frame, approveDenyFragment);
        transaction.addToBackStack(null);
        transaction.commitAllowingStateLoss();
    }

    @Override
    public void onQrRequest(final OxPush2Request oxPush2Request) {
        if (!this.isDestroyed()) {
            doQrRequest(oxPush2Request);
        }
    }

    @Override
    public void onAdFreeButtonClick(){
        if (readyToPurchase) {
            TransactionDetails transactionDetails = bp.getSubscriptionTransactionDetails(SUBSCRIPTION_ID_TEST);
            if (transactionDetails != null) {
                isSubscribed = transactionDetails.purchaseInfo.purchaseData.autoRenewing;
            }

            //Automatically try make subscription - only for test
            if (!isSubscribed) {
                //only for testing
                    bp.purchase(GluuMainActivity.this, SUBSCRIPTION_ID_TEST);
                //For production
//                bp.subscribe(GluuMainActivity.this, SUBSCRIPTION_ID);
            } else {
                //Init GoogleMobile AD
                initGoogleADS(true);
            }
        }
    }

    private ProcessManager createProcessManager(OxPush2Request oxPush2Request){
        ProcessManager processManager = new ProcessManager();
        processManager.setOxPush2Request(oxPush2Request);
        processManager.setDataStore(dataStore);
        processManager.setActivity(this);
        processManager.setOxPush2RequestListener(new OxPush2RequestListener() {
            @Override
            public void onQrRequest(OxPush2Request oxPush2Request) {
                //skip code there
            }

            @Override
            public TokenResponse onSign(String jsonRequest, String origin, Boolean isDeny) throws JSONException, IOException, U2FException {
                return u2f.sign(jsonRequest, origin, isDeny);
            }

            @Override
            public TokenResponse onEnroll(String jsonRequest, OxPush2Request oxPush2Request, Boolean isDeny) throws JSONException, IOException, U2FException {
                return u2f.enroll(jsonRequest, oxPush2Request, isDeny);
            }

            @Override
            public DataStore onGetDataStore() {
                return dataStore;
            }

            @Override
            public void onAdFreeButtonClick(){}
        });

        return processManager;
    }

    @Override
    public TokenResponse onSign(String jsonRequest, String origin, Boolean isDeny) throws JSONException, IOException, U2FException {
        return u2f.sign(jsonRequest, origin, isDeny);
    }

    @Override
    public TokenResponse onEnroll(String jsonRequest, OxPush2Request oxPush2Request, Boolean isDeny) throws JSONException, IOException, U2FException {
        return u2f.enroll(jsonRequest, oxPush2Request, isDeny);
    }

    @Override
    public DataStore onGetDataStore() {
        return dataStore;
    }

    private boolean validateOxPush2Request(OxPush2Request oxPush2Request) {
        boolean result = true;
        try {
            boolean isOneStep = Utils.isEmpty(oxPush2Request.getUserName());
            boolean isTwoStep = Utils.areAllNotEmpty(oxPush2Request.getUserName(), oxPush2Request.getIssuer(), oxPush2Request.getApp(),
                    oxPush2Request.getState(), oxPush2Request.getMethod());

            if (BuildConfig.DEBUG) Log.d(TAG, "isOneStep: " + isOneStep + " isTwoStep: " + isTwoStep);

            if (isOneStep || isTwoStep) {
                // Valid authentication method should be used
                if (isTwoStep && !(Utils.equals(oxPush2Request.getMethod(), "authenticate") || Utils.equals(oxPush2Request.getMethod(), "enroll"))) {
                    result = false;
                }
            } else {
                // All fields must be not empty
                result = false;
            }
        } catch (Exception ex) {
            Log.e(TAG, "Failed to parse QR code");
            result = false;
        }

        if (!result) {
            Toast.makeText(getApplicationContext(), R.string.invalid_qr_code, Toast.LENGTH_LONG).show();
        }

        return result;
    }

    @Override
    public void onDeleteKeyHandle(byte[] keyHandle) {
        dataStore.deleteTokenEntry(keyHandle);
    }

    public static String getResourceString(int resourceID){
        return context.getString(resourceID);
    }

    @Override
    public void onNewPinCode(String pinCode) {

    }

    @Override
    public void onCorrectPinCode(boolean isPinCodeCorrect) {
        if (isPinCodeCorrect){
            showAlertView("New Pin Added!");
        } else {
            //to change pin code, first need check if user knows current one
            Intent intent = new Intent(GluuMainActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private void showAlertView(String message){
        CustomGluuAlert gluuAlert = new CustomGluuAlert(this);
        gluuAlert.setMessage(message);
        gluuAlert.show();
    }

    public Boolean getIsButtonVisible(){
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("CleanLogsSettings", Context.MODE_PRIVATE);
        Boolean isVisible = preferences.getBoolean("isCleanButtonVisible", true);
        return isVisible;
    }

    public void setIsButtonVisible(Boolean isVisible){
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("CleanLogsSettings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("isCleanButtonVisible", isVisible);
        editor.commit();
    }

    public interface GluuAlertCallback{
        void onPositiveButton();
    }

    public interface RequestProcessListener{
        void onApprove();
        void onDeny();
    }

    @Override
    protected void onPause() {
        GluuApplication.applicationPaused();
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (bp != null){
            bp.loadOwnedPurchasesFromGoogle();
        }
        GluuApplication.applicationResumed();
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("PinCodeSettings", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean("isMainActivityDestroyed", true);
        editor.commit();
        Log.d(String.valueOf(GluuApplication.class), "APP DESTROYED");
        if (bp != null)
            bp.release();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!bp.handleActivityResult(requestCode, resultCode, data))
            super.onActivityResult(requestCode, resultCode, data);
    }

    public void checkIsPush(){
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("oxPushSettings", Context.MODE_PRIVATE);
        if (preferences.getString("userChoose", "null").equalsIgnoreCase("deny")){
            String requestString = preferences.getString("oxRequest", "null");
            doOxRequest(requestString, true);
            return;
        }
        if (preferences.getString("userChoose", "null").equalsIgnoreCase("approve")){
            String requestString = preferences.getString("oxRequest", "null");
            doOxRequest(requestString, false);
            return;
        }
        SharedPreferences pushPreferences = getApplicationContext().getSharedPreferences("PushNotification", Context.MODE_PRIVATE);
        String message = pushPreferences.getString("PushData", null);
        if (message != null){
            Settings.setPushDataEmpty(getApplicationContext());
            final OxPush2Request oxPush2Request = new Gson().fromJson(message, OxPush2Request.class);
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doQrRequest(oxPush2Request);
                }
            }, 1000);
        }
    }

    private void doOxRequest(String oxRequest, Boolean isDeny){
        SharedPreferences preferences = getApplicationContext().getSharedPreferences("oxPushSettings", Context.MODE_PRIVATE);
        OxPush2Request oxPush2Request = new Gson().fromJson(oxRequest, OxPush2Request.class);
        final ProcessManager processManager = createProcessManager(oxPush2Request);
        processManager.onOxPushRequest(isDeny);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("userChoose", "null");
        editor.putString("oxRequest", "null");
        Settings.setPushDataEmpty(getApplicationContext());
        editor.commit();
        String message = "";
        if (isDeny){
            message = this.getApplicationContext().getString(R.string.process_deny_start);
        } else {
            message = this.getApplicationContext().getString(R.string.process_authentication_start);
        }
        Intent intent = new Intent("ox_request-precess-event");
        // You can also include some extra data.
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void checkUserCameraPermission(){
        Log.i(TAG, "Show camera button pressed. Checking permission.");
        // BEGIN_INCLUDE(camera_permission)
        // Check if the Camera permission is already available.
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Camera permission has not been granted.

            requestCameraPermission();

        } else {

            // Camera permissions is already available, show the camera preview.
            Log.i(TAG,
                    "CAMERA permission has already been granted. Displaying camera preview.");
//            showCameraPreview();
        }
        // END_INCLUDE(camera_permission)
    }

    private void requestCameraPermission() {
        Log.i(TAG, "CAMERA permission has NOT been granted. Requesting permission.");

        // BEGIN_INCLUDE(camera_permission_request)
        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                android.Manifest.permission.CAMERA)) {
            // Provide an additional rationale to the user if the permission was not granted
            // and the user would benefit from additional context for the use of the permission.
            // For example if the user has previously denied the permission.
            Log.i(TAG,
                    "Displaying camera permission rationale to provide additional context.");
            ActivityCompat.requestPermissions(GluuMainActivity.this,
                    new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        } else {

            // Camera permission has not been granted yet. Request it directly.
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA},
                    REQUEST_CAMERA);
        }
        // END_INCLUDE(camera_permission_request)
    }

}
