package com.transiva.app;
final class MerchantServerClock{
 private static volatile long offsetMs=0L;private static volatile long syncedAt=0L;private MerchantServerClock(){}
 static void sync(long serverEpochMs){if(serverEpochMs<=0)return;long now=System.currentTimeMillis();offsetMs=serverEpochMs-now;syncedAt=now;}
 static long now(){return System.currentTimeMillis()+offsetMs;}
 static boolean isSynced(){return syncedAt>0&&System.currentTimeMillis()-syncedAt<6*60*60*1000L;}
}
