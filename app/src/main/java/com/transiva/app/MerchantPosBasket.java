package com.transiva.app;

import org.json.JSONObject;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;

final class MerchantPosBasket {
    private final Map<String,Integer> quantities=new LinkedHashMap<>();

    boolean change(String id,int delta,IntPredicate available) {
        int updated=quantity(id)+delta;
        if(delta>0&&!available.test(updated))return false;
        if(updated<=0)quantities.remove(id);else quantities.put(id,updated);
        return true;
    }

    int quantity(String id) {
        return quantities.getOrDefault(id,0);
    }

    void restore(JSONObject snapshot) {
        if(snapshot==null)return;
        clear();
        for(Iterator<String> ids=snapshot.keys();ids.hasNext();){
            String id=ids.next();
            int quantity=snapshot.optInt(id);
            if(quantity>0)quantities.put(id,quantity);
        }
    }

    JSONObject snapshot() {return new JSONObject(quantities);}
    Map<String,Integer> items() {return Collections.unmodifiableMap(quantities);}
    boolean isEmpty() {return quantities.isEmpty();}
    void clear() {quantities.clear();}
}
