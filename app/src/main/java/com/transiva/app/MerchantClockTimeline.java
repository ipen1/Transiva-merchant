package com.transiva.app;

/** Pure monotonic clock model. Corrections are slewed at 5%, never stepped backwards. */
final class MerchantClockTimeline {
    private long anchorEpoch, anchorElapsed, correction;
    private boolean synced;
    MerchantClockTimeline(long epoch, long elapsed) { anchorEpoch=epoch;anchorElapsed=elapsed; }
    synchronized long now(long elapsed) {
        long age=Math.max(0,elapsed-anchorElapsed);
        long budget=age/20;
        long applied=Math.max(-budget,Math.min(budget,correction));
        return anchorEpoch+age+applied;
    }
    synchronized void sync(long serverEpoch,long sent,long received) {
        if(serverEpoch<=0||received<sent)return;
        long rtt=received-sent;
        if(synced&&rtt>10000)return;
        long estimate=serverEpoch+Math.min(2000,rtt/2);
        if(!synced){anchorEpoch=estimate;anchorElapsed=received;correction=0;synced=true;return;}
        long current=now(received);anchorEpoch=current;anchorElapsed=received;correction=estimate-current;
    }
    synchronized boolean isSynced(){return synced;}
}
