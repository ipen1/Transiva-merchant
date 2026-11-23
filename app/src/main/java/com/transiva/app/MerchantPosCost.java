package com.transiva.app;

import org.json.JSONObject;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class MerchantPosCost {
    private MerchantPosCost(){}
    static BigDecimal unit(JSONObject item) {
        if(item==null||item.isNull("unitCost"))return null;
        try {
            String value=item.optString("unitCost");
            if(!value.matches("[0-9]{1,10}(?:\\.[0-9]{1,6})?"))return null;
            BigDecimal cost=new BigDecimal(value);
            return cost.scale()<=6&&cost.signum()>=0&&cost.compareTo(BigDecimal.valueOf(1000000000))<=0?cost:null;
        } catch(NumberFormatException ignored){return null;}
    }
    static BigDecimal unit(long purchaseCost,long packSize) {
        return BigDecimal.valueOf(purchaseCost).divide(BigDecimal.valueOf(packSize),6,RoundingMode.HALF_UP);
    }
    static BigDecimal average(BigDecimal previous,long remaining,BigDecimal incoming,long quantity) {
        if(remaining==0)return incoming;
        if(previous==null||incoming==null)return null;
        return previous.multiply(BigDecimal.valueOf(remaining)).add(incoming.multiply(BigDecimal.valueOf(quantity)))
                .divide(BigDecimal.valueOf(remaining+quantity),6,RoundingMode.HALF_UP);
    }
    static long incomingUnits(long amount,long packSize) {
        if(amount<0||packSize<1||packSize>999999)throw new IllegalArgumentException("Jumlah atau isi pack tidak valid.");
        return Math.multiplyExact(amount,packSize);
    }
    static long valuation(BigDecimal unit,long pcs) {
        return unit.multiply(BigDecimal.valueOf(pcs)).setScale(0,RoundingMode.HALF_UP).longValueExact();
    }
}
