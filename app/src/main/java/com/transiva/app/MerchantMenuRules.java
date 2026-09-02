package com.transiva.app;
import org.json.*;import java.util.*;
final class MerchantMenuRules{private MerchantMenuRules(){}static long gross(long price,List<long[]> rules){for(long[] r:rules)if(price>=r[0]&&(r[1]<=0||price<=r[1]))return Math.max(price,Math.round(price*(100.0+r[2])/100.0));return price;}
static String normalizeCategory(String raw){if(raw==null)return "";String x=raw.trim().toLowerCase(Locale.US);if(x.contains("makan"))return "Makanan";if(x.contains("minum"))return "Minuman";if(x.contains("snack")||x.contains("cemil"))return "Snack";return raw.trim();}}
