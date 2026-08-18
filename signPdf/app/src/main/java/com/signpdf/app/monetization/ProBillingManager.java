package com.signpdf.app.monetization;

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
import com.signpdf.app.BuildConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SignPDF Pro 1회성 인앱 상품 상태와 구매/복원을 관리합니다.
 */
public class ProBillingManager implements PurchasesUpdatedListener {

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

    private State state = new State(false, false, null, "Google Play 연결 중");
    private ProductDetails productDetails;

    public ProBillingManager(Activity activity, StateListener stateListener) {
        this.activity = activity;
        this.stateListener = stateListener;
        billingClient = BillingClient.newBuilder(activity)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build();
    }

    public void start() {
        if (billingClient.isReady()) {
            queryProductAndPurchases();
            return;
        }

        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    updateState(new State(true, state.pro, state.priceText, "Google Play 연결됨"));
                    queryProductAndPurchases();
                } else {
                    updateState(new State(false, state.pro, state.priceText,
                        "Google Play 결제를 사용할 수 없습니다"));
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                updateState(new State(false, state.pro, state.priceText,
                    "Google Play 연결을 다시 확인하는 중"));
            }
        });
    }

    public void stop() {
        if (billingClient.isReady()) {
            billingClient.endConnection();
        }
    }

    public void refreshPurchases() {
        if (!billingClient.isReady()) {
            start();
            return;
        }
        queryPurchases();
    }

    public boolean isPro() {
        return state.pro;
    }

    public void launchPurchase() {
        if (!billingClient.isReady() || productDetails == null) {
            updateState(new State(state.ready, state.pro, state.priceText,
                "Play Console에서 Pro 상품을 활성화하면 구매할 수 있습니다"));
            return;
        }

        BillingFlowParams.ProductDetailsParams.Builder productBuilder =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails);

        if (productDetails.getOneTimePurchaseOfferDetailsList() != null
            && !productDetails.getOneTimePurchaseOfferDetailsList().isEmpty()) {
            String offerToken = productDetails.getOneTimePurchaseOfferDetailsList()
                .get(0).getOfferToken();
            if (offerToken != null && !offerToken.isEmpty()) {
                productBuilder.setOfferToken(offerToken);
            }
        }

        BillingFlowParams params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(Collections.singletonList(productBuilder.build()))
            .build();
        billingClient.launchBillingFlow(activity, params);
    }

    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.OK:
                processPurchases(purchases != null ? purchases : Collections.emptyList());
                break;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                updateState(new State(state.ready, state.pro, state.priceText, "구매가 취소되었습니다"));
                break;
            default:
                updateState(new State(state.ready, state.pro, state.priceText,
                    "구매를 완료하지 못했습니다"));
                break;
        }
    }

    private void queryProductAndPurchases() {
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(BuildConfig.PRO_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build();

        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(product))
            .build();

        billingClient.queryProductDetailsAsync(params, (billingResult, detailsResult) -> {
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

                updateState(new State(
                    state.ready,
                    state.pro,
                    price,
                    productDetails != null ? "Pro 구매 가능" : "Pro 상품 설정 대기 중"));
            }
            queryPurchases();
        });
    }

    private void queryPurchases() {
        QueryPurchasesParams params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build();

        billingClient.queryPurchasesAsync(params, (billingResult, purchases) -> {
            if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                processPurchases(purchases);
            }
        });
    }

    private void processPurchases(List<Purchase> purchases) {
        List<Purchase> proPurchases = new ArrayList<>();
        for (Purchase purchase : purchases) {
            if (purchase.getProducts().contains(BuildConfig.PRO_PRODUCT_ID)
                && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                proPurchases.add(purchase);
            }
        }

        for (Purchase purchase : proPurchases) {
            if (!purchase.isAcknowledged()) {
                AcknowledgePurchaseParams params = AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.getPurchaseToken())
                    .build();
                billingClient.acknowledgePurchase(params, result -> { });
            }
        }

        boolean owned = !proPurchases.isEmpty();
        updateState(new State(
            state.ready,
            owned,
            state.priceText,
            owned ? "Pro 활성화됨 · 광고 없음" : state.message));
    }

    private void updateState(State newState) {
        state = newState;
        activity.runOnUiThread(() -> stateListener.onChanged(newState));
    }
}
