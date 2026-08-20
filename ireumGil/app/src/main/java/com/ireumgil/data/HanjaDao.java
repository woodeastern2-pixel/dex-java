package com.ireumgil.data;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HanjaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<HanjaEntity> entities);

    @Query("DELETE FROM hanja")
    void deleteAll();

    @Query("SELECT COUNT(*) FROM hanja")
    int countAll();

    @Query("SELECT * FROM hanja WHERE character = :character LIMIT 1")
    HanjaEntity findByCharacter(String character);

    @Query("SELECT * FROM hanja WHERE IFNULL(allowedForName, 0) = 1")
    List<HanjaEntity> getAllAllowed();

    @Query("SELECT DISTINCT koreanReading FROM hanja WHERE IFNULL(allowedForName, 0) = 1 AND koreanReading IS NOT NULL AND koreanReading != '' ORDER BY koreanReading")
    List<String> getAllAllowedReadings();

    @Query("SELECT * FROM hanja WHERE IFNULL(isCommonSurname, 0) = 1")
    List<HanjaEntity> getCommonSurnameCharacters();

    @Query("SELECT * FROM hanja WHERE IFNULL(allowedForName, 0) = 1 AND koreanReading = :reading")
    List<HanjaEntity> searchByReadingExact(String reading);

    @Query("SELECT * FROM hanja WHERE IFNULL(allowedForName, 0) = 1 AND koreanReading LIKE :prefix || '%' ORDER BY koreanReading, character")
    List<HanjaEntity> searchByReadingPrefix(String prefix);

    @Query("SELECT * FROM hanja WHERE IFNULL(isCommonSurname, 0) = 1 AND koreanReading LIKE :prefix || '%' ORDER BY koreanReading, character")
    List<HanjaEntity> searchSurnameByReadingPrefix(String prefix);

    @Query("SELECT * FROM hanja " +
            "WHERE IFNULL(allowedForName, 0) = 1 " +
            "AND (:surnameOnly = 0 OR IFNULL(isCommonSurname, 0) = 1) " +
            "AND (koreanReading LIKE '%' || :keyword || '%' " +
            "OR character LIKE '%' || :keyword || '%' " +
            "OR meaning LIKE '%' || :keyword || '%') " +
            "ORDER BY CASE " +
            "WHEN character = :keyword THEN 0 " +
            "WHEN koreanReading = :keyword THEN 1 " +
            "WHEN koreanReading LIKE :keyword || '%' THEN 2 " +
            "WHEN meaning LIKE :keyword || '%' THEN 3 " +
            "ELSE 4 END, koreanReading, strokeCount, character LIMIT :limit")
    List<HanjaEntity> searchByKeyword(String keyword, boolean surnameOnly, int limit);

    @Query("SELECT * FROM hanja " +
            "WHERE (:reading IS NULL OR :reading = '' OR koreanReading LIKE '%' || :reading || '%') " +
            "AND (:characterKeyword IS NULL OR :characterKeyword = '' OR character LIKE '%' || :characterKeyword || '%') " +
            "AND (:meaningKeyword IS NULL OR :meaningKeyword = '' OR meaning LIKE '%' || :meaningKeyword || '%') " +
            "AND (:strokeCount IS NULL OR strokeCount = :strokeCount) " +
            "AND (:allowedForName IS NULL OR allowedForName = :allowedForName) " +
            "AND (:surnameOnly IS NULL OR isCommonSurname = :surnameOnly) " +
            "ORDER BY koreanReading, character LIMIT :limit")
    List<HanjaEntity> search(
            String reading,
            String characterKeyword,
            String meaningKeyword,
            Integer strokeCount,
            Boolean allowedForName,
            Boolean surnameOnly,
            int limit
    );
}
