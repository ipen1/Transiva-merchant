package com.transiva.app;

final class MerchantPosAccess {
    private MerchantPosAccess(){}
    interface Grantee {String posGrant();}
    static boolean valid(String grant){
        if(grant==null||!grant.matches("[0-9]{10}\\.[a-f0-9]{64}"))return false;
        return Long.parseLong(grant.substring(0,10))*1000L>System.currentTimeMillis();
    }
    static String category(String screen){
        switch(screen){
            case "report":case "history":case "cash":case "sales":return "finance";
            case "products":case "materials":return "products";
            case "staff":case "attendance":return "team";
            case "discounts":case "types":return "pricing";
            default:return "";
        }
    }
}
