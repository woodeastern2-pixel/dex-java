package com.signpdf.app.monetization;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.signpdf.app.BuildConfig;
import com.signpdf.app.R;
import com.signpdf.app.util.UsageQuotaManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** SignPDF Pro one-time purchase state, purchase, acknowledgement, and restore flow. */
public class ProBillingManager implements PurchasesUpdatedListener {

    private static final int MAX_ACK_ATTEMPTS = 2;
    private static final long ACK_RETRY_DELAY_MS = 1500L;

    public static class State {
        public final boolean ready;
        public final boolean pro;
        public final String priceText;
        public final String message;

        public State(boolean ready, boolean pro, String priceText, String message) {
            this.ready = ready;
            this.pro = pro;
            this.priceText = priceText;
            this.message = message;
        }
    }

    public interface StateListener {
        void onChanged(State state);
    }

    private final Activity activity;
    private final StateListener stateListener;
    private final BillingClient billingClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private State state;
    private ProductDetails productDetails;
    private boolean connectionInProgress = false;
    private boolean stopped = false;

    public ProBillingManager(Activity activity, StateListener stateListener) {
        this.activity = activity;
        this.stateListener = stateListener;

        // Debug APKs unlock Pro locally so every paid feature can be validated before
        // the Play product is activated. Release builds ignore this override completely.
        if (BuildConfig.DEBUG) {
            UsageQuotaManager.setDebugProOverride(true);
        }

        state = new State(false, UsageQuotaManager.isPro(), null,
            activity.getString(R.string.billing_connecting));
        billingClient = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    public void start() {
        if (stopped) return;
        if (billingClient.isReady()) {
            queryProductAndPurchases();
            return;
        }
        if (connectionInProgress) return;
        connectionInProgress = true;

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                connectionInProgress = false;
                if (stopped) return;
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    updateState(new State(false, UsageQuotaManager.isPro(), state.priceText,
                        activity.getString(R.string.billing_checking)));
                    queryProductAndPurchases();
                } else {
                    updateState(new State(false, UsageQuotaManager.isPro(), state.priceText,
                        activity.getString(R.string.billing_unavailable)));
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connectionInProgress = false;
                if (stopped) return;
                updateState(new State(false, UsageQuotaManager.isPro(), state.priceText,
                    activity.getString(R.string.billing_reconnecting)));
            }
        });
    }

    public void stop() {
        stopped = true;
        connectionInProgress = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (billingClient.isReady()) billingClient.endConnection();
    }

    public void refreshPurchases() {
        if (stopped) return;
        if (!billingClient.isReady()) {
            start();
            return;
        }
        updateState(new State(false, UsageQuotaManager.isPro(), state.priceText,
            activity.getString(R.string.billing_checking)));
        queryPurchases();
    }

    public boolean isPro() {
        return UsageQuotaManager.isPro();
    }

    public void launchPurchase() {
        if (stopped) return;
        if (!billingClient.isReady() || productDetails == null) {
            updateState(new State(state.ready, UsageQuotaManager.isPro(), state.priceText,
                activity.getString(R.string.billing_product_not_ready)));
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder productBuilder =
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(productDetails);

        if (productDetails.getOneTimePurchaseOfferDetailsList() != null
            && !productDetails.getOneTimePurchaseOfferDetailsList().isEmpty()) {
            String offerToken = productDetails.getOneTimePurchaseOfferDetailsList()
                .get(0).getOfferToken();
            if (offerToken != null && !offerToken.isEmpty()) productBuilder.setOfferToken(offerToken);
        }

        BillingFlowParams params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(productBuilder.build()))
            .build();
        BillingResult launchResult = billingClient.launchBillingFlow(activity, params);
        if (launchResult.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            updateState(new State(true, UsageQuotaManager.isPro(), state.priceText,
                activity.getString(R.string.billing_failed)));
        }
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (stopped) return;
        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.OK:
                processPurchases(purchases != null ? purchases : Collections.emptyList());
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                updateState(new State(true, UsageQuotaManager.isPro(), state.priceText,
                    activity.getString(R.string.billing_cancelled)));
                break;
            default:
                updateState(new State(true, UsageQuotaManager.isPro(), state.priceText,
                    activity.getString(R.string.billing_failed)));
                break;
        }
    }

    private void queryProductAndPurchases() {
        if (stopped || !billingClient.isReady()) return;

        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build();
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(product))
            .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, detailsResult) -> {
            if (stopped) return;
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                List<ProductDetails> details = detailsResult.getProductDetailsList();
                productDetails = details.isEmpty() ? null : details.get(0);

                String price = null;
                if (productDetails != null
                    && productDetails.getOneTimePurchaseOfferDetailsList() != null
                    && !productDetails.getOneTimePurchaseOfferDetailsList().isEmpty()) {
                    price = productDetails.getOneTimePurchaseOfferDetailsList()
                        .get(0).getFormattedPrice();
                }

                updateState(new State(false, UsageQuotaManager.isPro(), price,
                    activity.getString(productDetails != null
                        ? R.string.billing_product_ready
                        : R.string.billing_product_waiting)));
            }
            queryPurchases();
        });
    }

    private void queryPurchases() {
        if (stopped || !billingClient.isReady()) return;
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (stopped) return;
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases);
            } else {
                updateState(new State(false, UsageQuotaManager.isPro(), state.priceText,
                    activity.getString(R.string.billing_query_failed)));
            }
        });
    }

    private void processPurchases(List<Purchase> purchases) {
        if (stopped) return;
        List<Purchase> proPurchases = new ArrayList<>();
        boolean hasPendingProPurchase = false;

        for (Purchase purchase : purchases) {
            if (!purchase.getProducts().contains(BuildConfig.PRO_PRODUCT_ID)) continue;

            if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                proPurchases.add(purchase);
            } else if (purchase.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                hasPendingProPurchase = true;
            }
        }

        boolean acknowledgementPending = false;
        for (Purchase purchase : proPurchases) {
            if (!purchase.isAcknowledged()) {
                acknowledgementPending = true;
                acknowledgePurchaseWithRetry(purchase, 1);
            }
        }

        boolean owned = !proPurchases.isEmpty();
        UsageQuotaManager.setPro(owned);
        boolean effectivePro = UsageQuotaManager.isPro();

        final String message;
        if (effectivePro) {
            message = activity.getString(acknowledgementPending
                ? R.string.billing_ack_retry
                : R.string.billing_pro_enabled);
        } else if (hasPendingProPurchase) {
            message = activity.getString(R.string.billing_pending);
        } else {
            message = activity.getString(productDetails != null
                ? R.string.billing_pro_available
                : R.string.billing_product_waiting);
        }
        updateState(new State(true, effectivePro, state.priceText, message));
    }

    private void acknowledgePurchaseWithRetry(Purchase purchase, int attempt) {
        if (stopped || purchase == null || purchase.isAcknowledged()) return;

        AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.getPurchaseToken())
            .build();
        billingClient.acknowledgePurchase(params, result -> {
            if (stopped) return;
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                updateState(new State(true, UsageQuotaManager.isPro(), state.priceText,
                    activity.getString(R.string.billing_pro_enabled)));
                return;
            }

            if (attempt < MAX_ACK_ATTEMPTS && billingClient.isReady()) {
                mainHandler.postDelayed(
                    () -> acknowledgePurchaseWithRetry(purchase, attempt + 1),
                    ACK_RETRY_DELAY_MS);
            } else {
                updateState(new State(true, UsageQuotaManager.isPro(), state.priceText,
                    activity.getString(R.string.billing_ack_retry)));
            }
        });
    }

    private void updateState(State newState) {
        if (stopped) return;
        state = newState;
        activity.runOnUiThread(() -> {
            if (!stopped && !activity.isFinishing() && !activity.isDestroyed()) {
                stateListener.onChanged(newState);
            }
        });
    }
}
