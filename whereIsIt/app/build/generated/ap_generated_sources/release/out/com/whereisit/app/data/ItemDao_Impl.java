package com.whereisit.app.data;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.whereisit.app.model.ItemEntity;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ItemDao_Impl implements ItemDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ItemEntity> __insertionAdapterOfItemEntity;

  private final EntityDeletionOrUpdateAdapter<ItemEntity> __deletionAdapterOfItemEntity;

  private final EntityDeletionOrUpdateAdapter<ItemEntity> __updateAdapterOfItemEntity;

  public ItemDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfItemEntity = new EntityInsertionAdapter<ItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `items` (`id`,`item_name`,`location_name`,`category`,`photo_uris`,`memo`,`created_date`,`updated_date`,`favorite`,`tags`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ItemEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.itemName == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.itemName);
        }
        if (entity.locationName == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.locationName);
        }
        if (entity.category == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.category);
        }
        final String _tmp = StringListConverter.fromList(entity.photoUris);
        if (_tmp == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, _tmp);
        }
        if (entity.memo == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.memo);
        }
        statement.bindLong(7, entity.createdDate);
        statement.bindLong(8, entity.updatedDate);
        final int _tmp_1 = entity.favorite ? 1 : 0;
        statement.bindLong(9, _tmp_1);
        final String _tmp_2 = StringListConverter.fromList(entity.tags);
        if (_tmp_2 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_2);
        }
      }
    };
    this.__deletionAdapterOfItemEntity = new EntityDeletionOrUpdateAdapter<ItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `items` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ItemEntity entity) {
        statement.bindLong(1, entity.id);
      }
    };
    this.__updateAdapterOfItemEntity = new EntityDeletionOrUpdateAdapter<ItemEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `items` SET `id` = ?,`item_name` = ?,`location_name` = ?,`category` = ?,`photo_uris` = ?,`memo` = ?,`created_date` = ?,`updated_date` = ?,`favorite` = ?,`tags` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          final ItemEntity entity) {
        statement.bindLong(1, entity.id);
        if (entity.itemName == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.itemName);
        }
        if (entity.locationName == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.locationName);
        }
        if (entity.category == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.category);
        }
        final String _tmp = StringListConverter.fromList(entity.photoUris);
        if (_tmp == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, _tmp);
        }
        if (entity.memo == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.memo);
        }
        statement.bindLong(7, entity.createdDate);
        statement.bindLong(8, entity.updatedDate);
        final int _tmp_1 = entity.favorite ? 1 : 0;
        statement.bindLong(9, _tmp_1);
        final String _tmp_2 = StringListConverter.fromList(entity.tags);
        if (_tmp_2 == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, _tmp_2);
        }
        statement.bindLong(11, entity.id);
      }
    };
  }

  @Override
  public long insert(final ItemEntity item) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      final long _result = __insertionAdapterOfItemEntity.insertAndReturnId(item);
      __db.setTransactionSuccessful();
      return _result;
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void delete(final ItemEntity item) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __deletionAdapterOfItemEntity.handle(item);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public void update(final ItemEntity item) {
    __db.assertNotSuspendingTransaction();
    __db.beginTransaction();
    try {
      __updateAdapterOfItemEntity.handle(item);
      __db.setTransactionSuccessful();
    } finally {
      __db.endTransaction();
    }
  }

  @Override
  public ItemEntity getById(final long id) {
    final String _sql = "SELECT * FROM items WHERE id = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfItemName = CursorUtil.getColumnIndexOrThrow(_cursor, "item_name");
      final int _cursorIndexOfLocationName = CursorUtil.getColumnIndexOrThrow(_cursor, "location_name");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfPhotoUris = CursorUtil.getColumnIndexOrThrow(_cursor, "photo_uris");
      final int _cursorIndexOfMemo = CursorUtil.getColumnIndexOrThrow(_cursor, "memo");
      final int _cursorIndexOfCreatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "created_date");
      final int _cursorIndexOfUpdatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_date");
      final int _cursorIndexOfFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "favorite");
      final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
      final ItemEntity _result;
      if (_cursor.moveToFirst()) {
        _result = new ItemEntity();
        _result.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfItemName)) {
          _result.itemName = null;
        } else {
          _result.itemName = _cursor.getString(_cursorIndexOfItemName);
        }
        if (_cursor.isNull(_cursorIndexOfLocationName)) {
          _result.locationName = null;
        } else {
          _result.locationName = _cursor.getString(_cursorIndexOfLocationName);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _result.category = null;
        } else {
          _result.category = _cursor.getString(_cursorIndexOfCategory);
        }
        final String _tmp;
        if (_cursor.isNull(_cursorIndexOfPhotoUris)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getString(_cursorIndexOfPhotoUris);
        }
        _result.photoUris = StringListConverter.toList(_tmp);
        if (_cursor.isNull(_cursorIndexOfMemo)) {
          _result.memo = null;
        } else {
          _result.memo = _cursor.getString(_cursorIndexOfMemo);
        }
        _result.createdDate = _cursor.getLong(_cursorIndexOfCreatedDate);
        _result.updatedDate = _cursor.getLong(_cursorIndexOfUpdatedDate);
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfFavorite);
        _result.favorite = _tmp_1 != 0;
        final String _tmp_2;
        if (_cursor.isNull(_cursorIndexOfTags)) {
          _tmp_2 = null;
        } else {
          _tmp_2 = _cursor.getString(_cursorIndexOfTags);
        }
        _result.tags = StringListConverter.toList(_tmp_2);
      } else {
        _result = null;
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ItemEntity> getAllItems() {
    final String _sql = "SELECT * FROM items ORDER BY updated_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfItemName = CursorUtil.getColumnIndexOrThrow(_cursor, "item_name");
      final int _cursorIndexOfLocationName = CursorUtil.getColumnIndexOrThrow(_cursor, "location_name");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfPhotoUris = CursorUtil.getColumnIndexOrThrow(_cursor, "photo_uris");
      final int _cursorIndexOfMemo = CursorUtil.getColumnIndexOrThrow(_cursor, "memo");
      final int _cursorIndexOfCreatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "created_date");
      final int _cursorIndexOfUpdatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_date");
      final int _cursorIndexOfFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "favorite");
      final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
      final List<ItemEntity> _result = new ArrayList<ItemEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ItemEntity _item;
        _item = new ItemEntity();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfItemName)) {
          _item.itemName = null;
        } else {
          _item.itemName = _cursor.getString(_cursorIndexOfItemName);
        }
        if (_cursor.isNull(_cursorIndexOfLocationName)) {
          _item.locationName = null;
        } else {
          _item.locationName = _cursor.getString(_cursorIndexOfLocationName);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        final String _tmp;
        if (_cursor.isNull(_cursorIndexOfPhotoUris)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getString(_cursorIndexOfPhotoUris);
        }
        _item.photoUris = StringListConverter.toList(_tmp);
        if (_cursor.isNull(_cursorIndexOfMemo)) {
          _item.memo = null;
        } else {
          _item.memo = _cursor.getString(_cursorIndexOfMemo);
        }
        _item.createdDate = _cursor.getLong(_cursorIndexOfCreatedDate);
        _item.updatedDate = _cursor.getLong(_cursorIndexOfUpdatedDate);
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfFavorite);
        _item.favorite = _tmp_1 != 0;
        final String _tmp_2;
        if (_cursor.isNull(_cursorIndexOfTags)) {
          _tmp_2 = null;
        } else {
          _tmp_2 = _cursor.getString(_cursorIndexOfTags);
        }
        _item.tags = StringListConverter.toList(_tmp_2);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ItemEntity> getFavoriteItems() {
    final String _sql = "SELECT * FROM items WHERE favorite = 1 ORDER BY updated_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfItemName = CursorUtil.getColumnIndexOrThrow(_cursor, "item_name");
      final int _cursorIndexOfLocationName = CursorUtil.getColumnIndexOrThrow(_cursor, "location_name");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfPhotoUris = CursorUtil.getColumnIndexOrThrow(_cursor, "photo_uris");
      final int _cursorIndexOfMemo = CursorUtil.getColumnIndexOrThrow(_cursor, "memo");
      final int _cursorIndexOfCreatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "created_date");
      final int _cursorIndexOfUpdatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_date");
      final int _cursorIndexOfFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "favorite");
      final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
      final List<ItemEntity> _result = new ArrayList<ItemEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ItemEntity _item;
        _item = new ItemEntity();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfItemName)) {
          _item.itemName = null;
        } else {
          _item.itemName = _cursor.getString(_cursorIndexOfItemName);
        }
        if (_cursor.isNull(_cursorIndexOfLocationName)) {
          _item.locationName = null;
        } else {
          _item.locationName = _cursor.getString(_cursorIndexOfLocationName);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        final String _tmp;
        if (_cursor.isNull(_cursorIndexOfPhotoUris)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getString(_cursorIndexOfPhotoUris);
        }
        _item.photoUris = StringListConverter.toList(_tmp);
        if (_cursor.isNull(_cursorIndexOfMemo)) {
          _item.memo = null;
        } else {
          _item.memo = _cursor.getString(_cursorIndexOfMemo);
        }
        _item.createdDate = _cursor.getLong(_cursorIndexOfCreatedDate);
        _item.updatedDate = _cursor.getLong(_cursorIndexOfUpdatedDate);
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfFavorite);
        _item.favorite = _tmp_1 != 0;
        final String _tmp_2;
        if (_cursor.isNull(_cursorIndexOfTags)) {
          _tmp_2 = null;
        } else {
          _tmp_2 = _cursor.getString(_cursorIndexOfTags);
        }
        _item.tags = StringListConverter.toList(_tmp_2);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ItemEntity> getRecentItems(final int limit) {
    final String _sql = "SELECT * FROM items ORDER BY created_date DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfItemName = CursorUtil.getColumnIndexOrThrow(_cursor, "item_name");
      final int _cursorIndexOfLocationName = CursorUtil.getColumnIndexOrThrow(_cursor, "location_name");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfPhotoUris = CursorUtil.getColumnIndexOrThrow(_cursor, "photo_uris");
      final int _cursorIndexOfMemo = CursorUtil.getColumnIndexOrThrow(_cursor, "memo");
      final int _cursorIndexOfCreatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "created_date");
      final int _cursorIndexOfUpdatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_date");
      final int _cursorIndexOfFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "favorite");
      final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
      final List<ItemEntity> _result = new ArrayList<ItemEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ItemEntity _item;
        _item = new ItemEntity();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfItemName)) {
          _item.itemName = null;
        } else {
          _item.itemName = _cursor.getString(_cursorIndexOfItemName);
        }
        if (_cursor.isNull(_cursorIndexOfLocationName)) {
          _item.locationName = null;
        } else {
          _item.locationName = _cursor.getString(_cursorIndexOfLocationName);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        final String _tmp;
        if (_cursor.isNull(_cursorIndexOfPhotoUris)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getString(_cursorIndexOfPhotoUris);
        }
        _item.photoUris = StringListConverter.toList(_tmp);
        if (_cursor.isNull(_cursorIndexOfMemo)) {
          _item.memo = null;
        } else {
          _item.memo = _cursor.getString(_cursorIndexOfMemo);
        }
        _item.createdDate = _cursor.getLong(_cursorIndexOfCreatedDate);
        _item.updatedDate = _cursor.getLong(_cursorIndexOfUpdatedDate);
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfFavorite);
        _item.favorite = _tmp_1 != 0;
        final String _tmp_2;
        if (_cursor.isNull(_cursorIndexOfTags)) {
          _tmp_2 = null;
        } else {
          _tmp_2 = _cursor.getString(_cursorIndexOfTags);
        }
        _item.tags = StringListConverter.toList(_tmp_2);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @Override
  public List<ItemEntity> searchItems(final String keyword, final String category) {
    final String _sql = "SELECT * FROM items WHERE (? = '' OR category = ?) AND (item_name LIKE '%' || ? || '%' OR location_name LIKE '%' || ? || '%' OR tags LIKE '%' || ? || '%') ORDER BY updated_date DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 5);
    int _argIndex = 1;
    if (category == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, category);
    }
    _argIndex = 2;
    if (category == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, category);
    }
    _argIndex = 3;
    if (keyword == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, keyword);
    }
    _argIndex = 4;
    if (keyword == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, keyword);
    }
    _argIndex = 5;
    if (keyword == null) {
      _statement.bindNull(_argIndex);
    } else {
      _statement.bindString(_argIndex, keyword);
    }
    __db.assertNotSuspendingTransaction();
    final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
    try {
      final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
      final int _cursorIndexOfItemName = CursorUtil.getColumnIndexOrThrow(_cursor, "item_name");
      final int _cursorIndexOfLocationName = CursorUtil.getColumnIndexOrThrow(_cursor, "location_name");
      final int _cursorIndexOfCategory = CursorUtil.getColumnIndexOrThrow(_cursor, "category");
      final int _cursorIndexOfPhotoUris = CursorUtil.getColumnIndexOrThrow(_cursor, "photo_uris");
      final int _cursorIndexOfMemo = CursorUtil.getColumnIndexOrThrow(_cursor, "memo");
      final int _cursorIndexOfCreatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "created_date");
      final int _cursorIndexOfUpdatedDate = CursorUtil.getColumnIndexOrThrow(_cursor, "updated_date");
      final int _cursorIndexOfFavorite = CursorUtil.getColumnIndexOrThrow(_cursor, "favorite");
      final int _cursorIndexOfTags = CursorUtil.getColumnIndexOrThrow(_cursor, "tags");
      final List<ItemEntity> _result = new ArrayList<ItemEntity>(_cursor.getCount());
      while (_cursor.moveToNext()) {
        final ItemEntity _item;
        _item = new ItemEntity();
        _item.id = _cursor.getLong(_cursorIndexOfId);
        if (_cursor.isNull(_cursorIndexOfItemName)) {
          _item.itemName = null;
        } else {
          _item.itemName = _cursor.getString(_cursorIndexOfItemName);
        }
        if (_cursor.isNull(_cursorIndexOfLocationName)) {
          _item.locationName = null;
        } else {
          _item.locationName = _cursor.getString(_cursorIndexOfLocationName);
        }
        if (_cursor.isNull(_cursorIndexOfCategory)) {
          _item.category = null;
        } else {
          _item.category = _cursor.getString(_cursorIndexOfCategory);
        }
        final String _tmp;
        if (_cursor.isNull(_cursorIndexOfPhotoUris)) {
          _tmp = null;
        } else {
          _tmp = _cursor.getString(_cursorIndexOfPhotoUris);
        }
        _item.photoUris = StringListConverter.toList(_tmp);
        if (_cursor.isNull(_cursorIndexOfMemo)) {
          _item.memo = null;
        } else {
          _item.memo = _cursor.getString(_cursorIndexOfMemo);
        }
        _item.createdDate = _cursor.getLong(_cursorIndexOfCreatedDate);
        _item.updatedDate = _cursor.getLong(_cursorIndexOfUpdatedDate);
        final int _tmp_1;
        _tmp_1 = _cursor.getInt(_cursorIndexOfFavorite);
        _item.favorite = _tmp_1 != 0;
        final String _tmp_2;
        if (_cursor.isNull(_cursorIndexOfTags)) {
          _tmp_2 = null;
        } else {
          _tmp_2 = _cursor.getString(_cursorIndexOfTags);
        }
        _item.tags = StringListConverter.toList(_tmp_2);
        _result.add(_item);
      }
      return _result;
    } finally {
      _cursor.close();
      _statement.release();
    }
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
