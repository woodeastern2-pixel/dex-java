package com.ireumgil.data;

import com.ireumgil.model.HanjaCharacter;

import java.util.List;

public class HanjaSearchService {

    public static class Query {
        public String reading;
        public String character;
        public String meaningKeyword;
        public Integer strokeCount;
        public Boolean allowedForName;
        public Boolean surnameOnly;
        public int limit = 300;
    }

    private final HanjaRepository repository;

    public HanjaSearchService(HanjaRepository repository) {
        this.repository = repository;
    }

    public List<HanjaCharacter> search(Query query) {
        Query q = query == null ? new Query() : query;
        return repository.search(
                q.reading,
                q.character,
                q.meaningKeyword,
                q.strokeCount,
                q.allowedForName,
                q.surnameOnly,
                q.limit
        );
    }
}
