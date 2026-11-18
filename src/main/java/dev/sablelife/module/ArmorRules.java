package dev.sablelife.module;
import net.minecraft.nbt.*;
import java.util.*;
/** Bounded per-part rules. Channel zero is unbound, never an unconditional shield. */
public final class ArmorRules {
 public static final int LIMIT=64;
 public enum HitType { OTHER, MELEE, PROJECTILE }
 public static final class Row {
  private final UUID id;private boolean enabled=true;private int source,when,channel=1;private double modifier;
  Row(UUID id){this.id=id;}public UUID id(){return id;}public boolean enabled(){return enabled;}public int source(){return source;}public int when(){return when;}public int channel(){return channel;}public double modifier(){return modifier;}
  private Double meleeModifier, projectileModifier;
  public boolean overrides(HitType type) {
   return switch(type){case MELEE -> meleeModifier!=null;case PROJECTILE -> projectileModifier!=null;case OTHER -> false;};
  }
  public double modifier(HitType type) {
   return switch(type){case MELEE -> meleeModifier==null?modifier:meleeModifier;case PROJECTILE -> projectileModifier==null?modifier:projectileModifier;case OTHER -> modifier;};
  }
  double lowestModifier(boolean projectileOnly) {
   double projectile = modifier(HitType.PROJECTILE);
   return projectileOnly ? projectile : Math.min(projectile, Math.min(modifier, modifier(HitType.MELEE)));
  }
  public void clampMinimum(double minimum){
   modifier=Math.max(minimum,modifier);
   if(meleeModifier!=null)meleeModifier=Math.max(minimum,meleeModifier);
   if(projectileModifier!=null)projectileModifier=Math.max(minimum,projectileModifier);
  }
  public boolean edit(String key,double value){
   if(!Double.isFinite(value))return false;
   switch(key){
    case "enabled"->{if(value!=0&&value!=1)return false;enabled=value==1;}
    case "when"->{if(value!=0&&value!=1)return false;when=(int)value;}
    case "source"->{if(value!=0&&value!=1)return false;source=(int)value;}
    case "channel"->{if(value!=Math.rint(value)||value<0||value>65535)return false;channel=(int)value;}
    case "modifier","meleeModifier","projectileModifier"->{
     if(value< -100||value>1000)return false;
     switch(key){
      case "modifier" -> modifier=value;
      case "meleeModifier" -> {if(meleeModifier==null)return false;meleeModifier=value;}
      case "projectileModifier" -> {if(projectileModifier==null)return false;projectileModifier=value;}
     }
    }
    case "meleeOverride"->{if(value!=0&&value!=1)return false;meleeModifier=value==0?null:modifier(HitType.MELEE);}
    case "projectileOverride"->{if(value!=0&&value!=1)return false;projectileModifier=value==0?null:modifier(HitType.PROJECTILE);}
    default->{return false;}
   }
   return true;
  }
 }
 private final List<Row> rows=new ArrayList<>();public List<Row> rows(){return List.copyOf(rows);}public Row find(UUID id){return rows.stream().filter(r->r.id.equals(id)).findFirst().orElse(null);}
 public Row add(){if(rows.size()>=LIMIT)return null;var r=new Row(UUID.randomUUID());rows.add(r);return r;}public boolean remove(UUID id){return rows.removeIf(r->r.id.equals(id));}
 public boolean move(UUID id,int delta){var r=find(id);int i=rows.indexOf(r),n=i+delta;if(i<0||n<0||n>=rows.size())return false;Collections.swap(rows,i,n);return true;}
 public boolean channelDeleted(int index){boolean changed=false;for(var r:rows)if(r.channel>index){r.channel=r.channel==index+1?0:r.channel-1;changed=true;}return changed;}
 public void unbind(){rows.forEach(r->r.channel=0);}
 public ListTag save(){var list=new ListTag();for(var r:rows){var t=new CompoundTag();t.putUUID("Id",r.id);t.putBoolean("Enabled",r.enabled);t.putInt("Source",r.source);t.putInt("When",r.when);t.putInt("Channel",r.channel);t.putDouble("Modifier",r.modifier);if(r.meleeModifier!=null)t.putDouble("MeleeModifier",r.meleeModifier);if(r.projectileModifier!=null)t.putDouble("ProjectileModifier",r.projectileModifier);list.add(t);}return list;}
 public void load(ListTag list){rows.clear();var ids=new HashSet<UUID>();for(var item:list){if(rows.size()>=LIMIT)break;var t=(CompoundTag)item;if(!t.hasUUID("Id")||!ids.add(t.getUUID("Id")))continue;var r=new Row(t.getUUID("Id"));if(!r.edit("when",t.getInt("When"))||!r.edit("source",t.getInt("Source"))||!r.edit("channel",t.getInt("Channel"))||!r.edit("modifier",t.getDouble("Modifier")))continue;
   r.meleeModifier=readOverride(t,"MeleeModifier");r.projectileModifier=readOverride(t,"ProjectileModifier");r.enabled=t.getBoolean("Enabled");rows.add(r);}}
 private static Double readOverride(CompoundTag tag,String key){
  if(!tag.contains(key,Tag.TAG_ANY_NUMERIC))return null;
  double value=tag.getDouble(key);
  return Double.isFinite(value)&&value>=-100&&value<=1000?value:null;
 }
}
