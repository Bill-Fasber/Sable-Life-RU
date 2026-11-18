package dev.sablelife.module;

/** Unit-independent scalar calculations; no world, module registry or physics backend state. */
public final class MotionForceMath {
    public static double impulse(double speed,double mass,double strength,boolean continuous,boolean limited,double maximum,boolean brake){
        if(!Double.isFinite(speed)||!Double.isFinite(mass)||mass<=0||!Double.isFinite(strength)||strength<0)return 0;
        if(limited&&(!Double.isFinite(maximum)||maximum<0))return 0;
        double drive=strength*(continuous?.05:1);if(!Double.isFinite(drive))return 0;
        if(!limited)return drive;
        if(speed>maximum)return brake?-Math.min((speed-maximum)*mass,drive):0;
        return Math.min(drive,Math.max(0,maximum-speed)*mass);
    }
    public static double brake(double speed,double threshold,double strength){
        if(!Double.isFinite(speed)||!Double.isFinite(threshold)||threshold<0||!Double.isFinite(strength)||strength<=0||speed<=threshold)return 0;
        return (speed-threshold)*-Math.expm1(-strength/10*.05);
    }
    private MotionForceMath(){}
}
