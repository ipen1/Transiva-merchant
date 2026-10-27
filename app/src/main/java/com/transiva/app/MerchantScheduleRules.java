package com.transiva.app;

/** Canonical HH:mm display values; unchecked days never require an entered time. */
final class MerchantScheduleRules {
 private MerchantScheduleRules(){}
 static boolean valid(String raw){return raw!=null&&raw.matches("(?:[01]\\d|2[0-3]):[0-5]\\d");}
 static String display(String raw,String fallback){
  String value=raw==null?"":raw.trim();
  if(value.matches("\\d{4}"))value=value.substring(0,2)+":"+value.substring(2);
  if(value.matches("\\d{2}:\\d{2}:\\d{2}"))value=value.substring(0,5);
  return valid(value)?value:fallback;
 }
 static boolean enabled(Object raw){return raw==null||Boolean.TRUE.equals(raw)||"1".equals(String.valueOf(raw))||"true".equalsIgnoreCase(String.valueOf(raw));}
}
