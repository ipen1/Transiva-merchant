package com.transiva.app;

import org.json.JSONObject;

/**
 * Regional/cluster model sourced from the Transiva server/database.
 * No regional or cluster definitions are hardcoded in the Merchant APK.
 */
public final class TransivaCluster {
    public static final class Region {
        public final int id;
        public final String code;
        public final String name;
        public final boolean inside;

        Region(int id, String code, String name, boolean inside) {
            this.id = id; this.code = safe(code); this.name = safe(name); this.inside = inside;
        }
    }

    public static final class Item {
        public final int id;
        public final int regionId;
        public final String code;
        public final String name;
        public final boolean insideLaunchArea;
        public final Region region;

        Item(int id, int regionId, String code, String name, boolean insideLaunchArea, Region region) {
            this.id = id; this.regionId = regionId; this.code = safe(code); this.name = safe(name);
            this.insideLaunchArea = insideLaunchArea; this.region = region;
        }
    }

    private TransivaCluster() {}

    public static Item fromServer(JSONObject cluster) {
        if (cluster == null) return null;
        JSONObject r = cluster.optJSONObject("region");
        Region region = r == null ? null : new Region(
                r.optInt("id", cluster.optInt("region_id", 0)),
                r.optString("code", cluster.optString("region_code", "")),
                r.optString("name", cluster.optString("region_name", "")),
                r.optBoolean("inside_region", true)
        );
        return new Item(
                cluster.optInt("id", 0),
                cluster.optInt("region_id", region == null ? 0 : region.id),
                cluster.optString("code", ""),
                cluster.optString("name", ""),
                cluster.optBoolean("inside_launch_area", false),
                region
        );
    }

    public static String label(Item item) {
        if (item == null || item.id <= 0) return "📍 Wilayah merchant belum terdeteksi";
        String regionName = item.region == null ? "Regional belum terdeteksi" : nonEmpty(item.region.name, "Regional belum terdeteksi");
        return "🌐 Regional " + regionName + "  •  📍 Cluster " + nonEmpty(item.name, "Belum terdeteksi");
    }

    private static String safe(String v) { return v == null ? "" : v.trim(); }
    private static String nonEmpty(String v, String fallback) { String s = safe(v); return s.isEmpty() ? fallback : s; }
}
