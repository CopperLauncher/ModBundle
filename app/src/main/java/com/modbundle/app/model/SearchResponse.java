package com.modbundle.app.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SearchResponse {
    @SerializedName("hits")        public List<ModResult> hits;
    @SerializedName("total_hits")  public int totalHits;
    @SerializedName("offset")      public int offset;
    @SerializedName("limit")       public int limit;
}
