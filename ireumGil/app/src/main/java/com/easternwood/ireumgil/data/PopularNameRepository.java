package com.easternwood.ireumgil.data;

import android.content.Context;

import com.easternwood.ireumgil.model.PopularName;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class PopularNameRepository {

    private static final String ASSET = "names/popular_names_current.csv";
    private final List<PopularName> names = new ArrayList<>();

    public PopularNameRepository(Context context) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(ASSET), StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] values = line.split(",", -1);
                if (values.length < 7) {
                    continue;
                }
                names.add(new PopularName(
                        values[0], parseInt(values[1]), values[2], parseInt(values[3]),
                        values[5], values[6]
                ));
            }
        } catch (Exception ignored) {
            // The app can still open if a refresh asset is accidentally omitted.
        }
    }

    public List<PopularName> topTen(String gender) {
        List<PopularName> result = new ArrayList<>();
        for (PopularName item : names) {
            if (gender.equals(item.gender) && item.rank <= 10) {
                result.add(item);
            }
        }
        return result;
    }

    public String latestPeriod() {
        return names.isEmpty() ? "" : names.get(0).periodEnd;
    }

    public String updatedAt() {
        return names.isEmpty() ? "" : names.get(0).updatedAt;
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            return 0;
        }
    }
}
