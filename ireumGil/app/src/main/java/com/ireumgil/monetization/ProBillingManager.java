package com.ireumgil.monetization;

import android.app.Activity;

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
import com.ireumgil.BuildConfig;

import java.util.Collections;
import java.util.List;

public class ProBillingManager implements PurchasesUpdatedListener {

    public static class State {
        public final boolean ready;
        public final boolean pro;
        public final String price;
        public final String message;

        State(boolean ready, boolean pro, String price, String message) {
            this.ready = ready;
            this.pro = pro;
            this.price = price;
            this.message = message;
        }
    }

    public interface Listener { void onChanged(State state); }

    private final Activity activity;
    private final Listener listener;
    private final BillingClient client;
    private State state = new State(false, false, null, "Google Play 연결 중");
    private ProductDetails product;
    private boolean connecting;
    private boolean stopped;

    public ProBillingManager(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        client = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    public void start() {
        if (stopped || connecting) return;
        if (client.isReady()) { queryProduct(); return; }
        connecting = true;
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                connecting = false;
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) queryProduct();
                else publish(new State(false, false, state.price, "Google Play 결제를 사용할 수 없습니다"));
            }
            @Override public void onBillingServiceDisconnected() { connecting = false; }
        });
    }

    public void launchPurchase() {
        if (!client.isReady() || product == null) {
            publish(new State(state.ready, state.pro, state.price, "Play Console에서 Pro 상품을 활성화해 주세요"));
            return;
        }
        BillingFlowParams.ProductDetailsParams.Builder details = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product);
        if (product.getOneTimePurchaseOfferDetailsList() != null
                && !product.getOneTimePurchaseOfferDetailsList().isEmpty()) {
            String token = product.getOneTimePurchaseOfferDetailsList().get(0).getOfferToken();
            if (token != null && !token.isEmpty()) details.setOfferToken(token);
        }
        client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(details.build())).build());
    }

    public void refresh() {
        if (!client.isReady()) start(); else queryPurchases();
    }

    public void stop() {
        stopped = true;
        if (client.isReady()) client.endConnection();
    }

    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
            process(purchases == null ? Collections.emptyList() : purchases);
        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) {
            publish(new State(true, state.pro, state.price, "구매가 취소되었습니다"));
        }
    }

    private void queryProduct() {
        QueryProductDetailsParams.Product requested = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(BuildConfig.PRO_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP).build();
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(requested)).build(), (result, details) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK
                    && !details.getProductDetailsList().isEmpty()) {
                product = details.getProductDetailsList().get(0);
                String price = product.getOneTimePurchaseOfferDetailsList() == null
                        || product.getOneTimePurchaseOfferDetailsList().isEmpty() ? null
                        : product.getOneTimePurchaseOfferDetailsList().get(0).getFormattedPrice();
                publish(new State(false, state.pro, price, "Pro 상품 확인됨"));
            }
            queryPurchases();
        });
    }

    private void queryPurchases() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build(), (result, purchases) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) process(purchases);
        });
    }

    private void process(List<Purchase> purchases) {
        boolean owned = false;
        for (Purchase purchase : purchases) {
            if (purchase.getProducts().contains(BuildConfig.PRO_PRODUCT_ID)
                    && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                owned = true;
                if (!purchase.isAcknowledged()) {
                    client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.getPurchaseToken()).build(), result -> { });
                }
            }
        }
        publish(new State(true, owned, state.price, owned ? "Pro 활성화됨 · 광고 없음" : "Pro 구매 가능"));
    }

    private void publish(State next) {
        if (stopped) return;
        state = next;
        activity.runOnUiThread(() -> listener.onChanged(next));
    }
}
