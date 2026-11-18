package dev.sablelife.module;

import java.util.*;

/** Stable keys and independent defaults for the two physical motion modules. */
public final class PhysicsModuleFields {
    private static ModuleFields.Field n(String key,double min,double max,double initial){return new ModuleFields.Field(key,min,max,initial,1,List.of());}
    private static ModuleFields.Field t(String key,double min,double max,double initial){return new ModuleFields.Field(key,min,max,initial,20,List.of());}
    private static ModuleFields.Field c(String key,String... choices){return new ModuleFields.Field(key,0,choices.length-1,0,1,List.of(choices));}
    public static List<ModuleFields.Field> propulsion(){return List.of(
            c("propMode","prop_impulse","prop_continuous"),n("propStrength",0,10000,10),
            c("propFrame","prop_local","prop_world"),c("propWorldDirection","north","south","east","west","up","down"),
            n("propHorizontal",-180,180,0),n("propVertical",-90,90,0),t("propDuration",1,12000,20),
            t("interval",0,12000,20),c("propLimit","off","on"),n("propMaxSpeed",.1,128,16),
            c("propOverspeed","prop_coast","prop_brake"));}
    public static List<ModuleFields.Field> damping(){
        var result=new ArrayList<>(List.of(c("dampFrame","damp_world","damp_local"),
                c("dampDirections","damp_edit"),c("dampMode","damp_signal","damp_timed"),t("dampDuration",1,12000,20)));
        for(String direction:ModuleMotionDamping.DIRECTIONS){result.add(n("damp"+direction,0,100,0));result.add(n("damp"+direction+"Speed",0,128,0));}
        return List.copyOf(result);
    }
    public static boolean directionField(String key){return ModuleMotionDamping.DIRECTIONS.stream().anyMatch(d->key.equals("damp"+d)||key.equals("damp"+d+"Speed"));}
    private PhysicsModuleFields(){}
}
