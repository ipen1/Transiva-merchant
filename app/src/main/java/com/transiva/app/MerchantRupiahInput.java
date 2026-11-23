package com.transiva.app;

import android.text.*;
import android.text.method.DigitsKeyListener;
import android.widget.EditText;

final class MerchantRupiahInput {
    private MerchantRupiahInput(){}
    static long parse(String text) {
        String raw=text.trim();
        if(!raw.matches("[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+"))throw new NumberFormatException("Nominal Rupiah tidak valid");
        return Long.parseLong(raw.replace(".",""));
    }
    static String group(String digits) {
        StringBuilder result=new StringBuilder();
        for(int i=0;i<digits.length();i++){
            if(i>0&&(digits.length()-i)%3==0)result.append('.');
            result.append(digits.charAt(i));
        }
        return result.toString();
    }
    static void attach(EditText input){
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789."));
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER);
        TextWatcher watcher=new TextWatcher(){
            private boolean formatting;
            public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            public void onTextChanged(CharSequence s,int start,int before,int count){}
            public void afterTextChanged(Editable text){
                if(formatting)return;
                String original=text.toString();int cursor=Math.max(0,input.getSelectionStart());
                int digitsBefore=0;
                for(int i=0;i<Math.min(cursor,original.length());i++)if(original.charAt(i)>='0'&&original.charAt(i)<='9')digitsBefore++;
                String raw=original.replaceAll("[^0-9]","");
                String formatted=group(raw);
                if(!original.equals(formatted)){
                    formatting=true;
                    try{
                        text.replace(0,text.length(),formatted);
                        int position=0,seen=0;
                        while(position<text.length()&&seen<digitsBefore){if(text.charAt(position)!='.')seen++;position++;}
                        input.setSelection(position);
                    }finally{formatting=false;}
                }
            }
        };
        input.addTextChangedListener(watcher);watcher.afterTextChanged(input.getText());
    }
}
