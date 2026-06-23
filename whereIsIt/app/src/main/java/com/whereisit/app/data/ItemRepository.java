package com.whereisit.app.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.whereisit.app.model.ItemEntity;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItemRepository {

    public interface DataCallback<T> {
        void onResult(T result);
    }

    private final ItemDao itemDao;
    private final ExecutorService ioExecutor;
    private final Handler mainHandler;

    public ItemRepository(Context context) {
        this.itemDao = ItemDatabase.getInstance(context).itemDao();
        this.ioExecutor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void insert(ItemEntity item, DataCallback<Long> callback) {
        ioExecutor.execute(() -> {
            long id = itemDao.insert(item);
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(id));
            }
        });
    }

    public void update(ItemEntity item, Runnable callback) {
        ioExecutor.execute(() -> {
            itemDao.update(item);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    public void delete(ItemEntity item, Runnable callback) {
        ioExecutor.execute(() -> {
            itemDao.delete(item);
            if (callback != null) {
                mainHandler.post(callback);
            }
        });
    }

    public void getById(long id, DataCallback<ItemEntity> callback) {
        ioExecutor.execute(() -> {
            ItemEntity item = itemDao.getById(id);
            if (callback != null) {
                mainHandler.post(() -> callback.onResult(item));
            }
        });
    }

    public void getAllItems(DataCallback<List<ItemEntity>> callback) {
        ioExecutor.execute(() -> {
            List<ItemEntity> list = itemDao.getAllItems();
            mainHandler.post(() -> callback.onResult(list));
        });
    }

    public void getFavoriteItems(DataCallback<List<ItemEntity>> callback) {
        ioExecutor.execute(() -> {
            List<ItemEntity> list = itemDao.getFavoriteItems();
            mainHandler.post(() -> callback.onResult(list));
        });
    }

    public void getRecentItems(int limit, DataCallback<List<ItemEntity>> callback) {
        ioExecutor.execute(() -> {
            List<ItemEntity> list = itemDao.getRecentItems(limit);
            mainHandler.post(() -> callback.onResult(list));
        });
    }

    public void searchItems(String keyword, String category, DataCallback<List<ItemEntity>> callback) {
        ioExecutor.execute(() -> {
            List<ItemEntity> list = itemDao.searchItems(keyword, category);
            mainHandler.post(() -> callback.onResult(list));
        });
    }
}
