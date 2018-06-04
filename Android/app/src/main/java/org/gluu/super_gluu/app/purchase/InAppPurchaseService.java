package org.gluu.super_gluu.app.purchase;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.PurchaseState;
import com.anjlab.android.iab.v3.TransactionDetails;

import org.gluu.super_gluu.app.settings.Settings;

import SuperGluu.app.R;

/**
 * Created by nazaryavornytskyy on 6/30/17.
 */

public class InAppPurchaseService {

    private static final String TAG = "InAppPurchaseService";

    // PRODUCT & SUBSCRIPTION IDS
    private static final String MERCHANT_ID = null;

    public boolean readyToPurchase = false;
    public boolean isSubscribed = false;

    private BillingProcessor bp;

    public interface OnInAppServiceListener {
        public void onSubscribed(Boolean isSubscribed);
    }

    private OnInAppServiceListener inAppListener; //listener

    //setting the listener
    public void setCustomEventListener(OnInAppServiceListener eventListener) {
        this.inAppListener = eventListener;
    }

    public void initInAppService(final Context context){
        if(!BillingProcessor.isIabServiceAvailable(context)) {
            Log.e(TAG, "In-app billing service is unavailable, please upgrade Android Market/Play to version >= 3.9.16");
        }

        String licenseKey = context.getString(R.string.purchase_license_key);
        String subscriptionId = context.getString(R.string.subscription_id);

        bp = new BillingProcessor(context, licenseKey, MERCHANT_ID, new BillingProcessor.IBillingHandler() {
            @Override
            public void onProductPurchased(String productId, TransactionDetails details) {
                Log.e(TAG, "onProductPurchased: " + productId);
                isSubscribed = details.purchaseInfo.purchaseData.autoRenewing;
                //Init GoogleMobile AD
                //isSubscribed &&
                if (inAppListener != null){
                    inAppListener.onSubscribed(isSubscribed);
                }
                Settings.setPurchase(context, isSubscribed);
            }
            @Override
            public void onBillingError(int errorCode, Throwable error) {
                Log.e(TAG, "onBillingError: " + Integer.toString(errorCode));
            }
            @Override
            public void onBillingInitialized() {
                Log.e(TAG, "onBillingInitialized");
                readyToPurchase = true;
                TransactionDetails transactionDetails = bp.getSubscriptionTransactionDetails(subscriptionId);
                if (transactionDetails != null) {
                    isSubscribed = transactionDetails.purchaseInfo.purchaseData.autoRenewing;
                }
                TransactionDetails transactionDetails2 = bp.getPurchaseTransactionDetails(subscriptionId);
                if (transactionDetails2 != null) {
                    isSubscribed = transactionDetails2.purchaseInfo.purchaseData.purchaseState == PurchaseState.PurchasedSuccessfully;
                }
                //Init GoogleMobile AD
                if (inAppListener != null){
                    inAppListener.onSubscribed(isSubscribed);
                }
//                initGoogleADS(isSubscribed);
                Settings.setPurchase(context, isSubscribed);
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

    public void purchase(final Activity activity){
        String subscriptionId = activity.getString(R.string.subscription_id);

        bp.subscribe(activity, subscriptionId);
    }

    public void restorePurchase(){
        bp.loadOwnedPurchasesFromGoogle();
    }


    public void reloadPurchaseService(){
        if (bp != null){
            bp.loadOwnedPurchasesFromGoogle();
        }
    }

    public void deInitPurchaseService(){
        if (bp != null) bp.release();
    }

    public Boolean isHandleResult(int requestCode, int resultCode, Intent data){
        return !bp.handleActivityResult(requestCode, resultCode, data);
    }

}
