import 'package:sqflite/sqflite.dart';
import '../../../core/constants/app_constants.dart';
import '../../../core/database/database_helper.dart';

class SettingsLocalDatasource {
  final DatabaseHelper _dbHelper;

  SettingsLocalDatasource(this._dbHelper);

  Future<String?> getValue(String key) async {
    final db = await _dbHelper.database;
    final maps = await db.query(
      AppConstants.tableSettings,
      columns: ['value'],
      where: 'key = ?',
      whereArgs: [key],
      limit: 1,
    );
    if (maps.isEmpty) return null;
    return maps.first['value'] as String?;
  }

  Future<void> setValue(String key, String value) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().toIso8601String();
    await db.insert(
      AppConstants.tableSettings,
      {'key': key, 'value': value, 'updated_at': now},
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<Map<String, String>> getAllSettings() async {
    final db = await _dbHelper.database;
    final maps = await db.query(AppConstants.tableSettings);
    return {
      for (final row in maps) row['key'] as String: row['value'] as String,
    };
  }

  Future<void> setMultiple(Map<String, String> settings) async {
    final db = await _dbHelper.database;
    final now = DateTime.now().toIso8601String();
    final batch = db.batch();
    for (final entry in settings.entries) {
      batch.insert(
        AppConstants.tableSettings,
        {'key': entry.key, 'value': entry.value, 'updated_at': now},
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
    }
    await batch.commit(noResult: true);
  }
}

