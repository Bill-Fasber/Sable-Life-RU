package dev.sablelife.module;

import dev.sablelife.heart.*;
import net.minecraft.nbt.*;
import java.util.*;

/** Move set owns conditions, weighted/priority selection and direct action timing. */
public final class ModuleMoveSet {
    private static final class State {
        UUID current, previous, target;
        long started, restUntil;
        int revision;
        final Map<UUID, Long> cooldown = new HashMap<>();
    }
    private record Activation(long started, UUID target) {}

    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Activation> activations = new HashMap<>();
    public long activation(UUID id) { var action = activations.get(id); return action == null ? Long.MIN_VALUE : action.started(); }
    public UUID target(UUID id) { var action = activations.get(id); return action == null ? null : action.target(); }
    public boolean powered(UUID id) { return activations.containsKey(id); }
    public void clear() { states.clear(); activations.clear(); }
    public static String status(MoveSet.Move move, HeartBlockEntity heart) {
        if (!move.config().enabled()) return "disabled";
        if (move.actions().isEmpty()) return "empty";
        for (var a : move.actions()) {
            var target = heart.modules().find(a.module());
            if (target == null || !MoveSet.supportsModule(target.type())) return "missing";
            if (!HeartModules.status(target, heart).equals("valid")) return "unavailable";
            if (target.value("trigger") != 4) return "trigger";
        }
        return "ready";
    }
    public void tick(HeartBlockEntity heart) {
        activations.clear();
        if (!heart.operational() || !heart.wireless().master()) { clear(); return; }
        long now = heart.getLevel().getGameTime(); var keep = new HashSet<UUID>();
        for (var table : heart.modules().all()) {
            if (!table.type().equals("move_set") || !HeartModules.triggered(table, heart)) continue;
            keep.add(table.id()); var state = states.computeIfAbsent(table.id(), ignored -> new State());
            var moves = table.moveSet().moves();
            state.cooldown.keySet().removeIf(id -> moves.stream().noneMatch(m -> m.config().id().equals(id)));
            var running = moves.stream().filter(m -> m.config().id().equals(state.current)).findFirst().orElse(null);
            if (state.current != null && (running == null || !status(running, heart).equals("ready") || state.revision != heart.modules().revision()
                    || now - state.started >= Math.max(running.length(), (int)running.config().value("dispatchBusy")))) {
                state.target = null;
                if(ModuleHurt.once(table)) { heart.modules().hurtTriggers.finish(table.id()); state.current=null; continue; }
                state.current = null; state.restUntil = now + (running == null ? 1 : (int)running.config().value("dispatchRest")); running = null;
            }
            if (state.current == null && now >= state.restUntil) {
                boolean ignore = ignoresConditions(table, heart.isControlCore());
                var eligible = new ArrayList<MoveSet.Move>();
                var selectedTargets = new HashMap<UUID, UUID>();
                for (var move : moves) {
                    var rules = move.config();
                    if (now < state.cooldown.getOrDefault(rules.id(), 0L) || !status(move, heart).equals("ready")) continue;
                    if (!ignore) {
                        if (rules.value("inheritMoveTarget") == 1) {
                            UUID target = rules.selectTarget(heart);
                            if (target == null) continue;
                            selectedTargets.put(rules.id(), target);
                        } else if (!rules.targetPresent(heart)) continue;
                    }
                    eligible.add(move);
                }
                var selected = choose(eligible, state.previous, heart.getLevel().random.nextDouble());
                if (selected != null) {
                    running = eligible.stream().filter(m -> m.config().id().equals(selected)).findFirst().orElseThrow();
                    state.current = state.previous = selected; state.started = now; state.revision = heart.modules().revision();
                    state.target = selectedTargets.get(selected);
                    state.cooldown.put(selected, now + (int)running.config().value("dispatchCooldown"));
                }
            }
            if(running==null && ModuleHurt.once(table)) heart.modules().hurtTriggers.finish(table.id());
            if (running != null) for (var a : running.actions()) {
                if (!a.active(now - state.started)) continue;
                long started = state.started + a.start();
                var previous = activations.get(a.module());
                // Equal starts keep the first action in the core's module order.
                if (previous == null || started > previous.started()) activations.put(a.module(), new Activation(started, state.target));
            }
        }
        states.keySet().retainAll(keep);
    }
    /** Selection override only; action validity and timing remain independent. */
    public static boolean ignoresConditions(VirtualModule table){return table!=null&&table.type().equals("move_set")&&table.value("ignoreConditions")==1;}
    public static boolean ignoresConditions(VirtualModule table, boolean controlCore){return table!=null&&table.type().equals("move_set")&&(controlCore||ignoresConditions(table));}
    public static boolean ruleLocked(VirtualModule table,String key,boolean controlCore){return ignoresConditions(table, controlCore)&&!List.of("dispatchWeight","dispatchCooldown","dispatchBusy","dispatchRest","dispatchRepeat").contains(key)&&ModuleFields.moveRules().stream().anyMatch(f->f.key().equals(key));}
    /** Eligible priority moves follow list order; weighted draws only run when none are ready. */
    public static UUID choose(List<MoveSet.Move> eligible, UUID previous, double random) {
        var priority = eligible.stream().filter(MoveSet.Move::priority).findFirst();
        if (priority.isPresent()) return priority.get().config().id();
        return MoveChoice.choose(eligible.stream().map(m -> new MoveChoice.Candidate(m.config().id(),
                (int)m.config().value("dispatchWeight"), m.config().value("dispatchRepeat") == 0)).toList(), previous, random);
    }
    public CompoundTag snapshot(VirtualModule table, HeartBlockEntity heart) {
        var tag = new CompoundTag(); var state = states.get(table.id()); long now = heart.getLevel().getGameTime();
        if (state != null && state.current != null) { tag.putUUID("Current", state.current); tag.putInt("Elapsed", (int)(now - state.started)); }
        var rows = new ListTag();
        for (var move : table.moveSet().moves()) {
            var row = new CompoundTag(); var id = move.config().id(); row.putUUID("Id", id);
            String status = status(move, heart);
            if (status.equals("ready") && !ignoresConditions(table, heart.isControlCore()) && !move.config().targetPresent(heart)) status = "conditions";
            row.putString("Status", status); row.putInt("Cooldown", state == null ? 0 : (int)Math.max(0, state.cooldown.getOrDefault(id, 0L) - now));
            var problems = new ListTag(); var seen = new HashSet<UUID>();
            for (int i=0;i<move.actions().size();i++) {
                var action=move.actions().get(i); if(!seen.add(action.module()))continue;
                var target=heart.modules().find(action.module());
                String reason=target==null?"missing":!MoveSet.supportsModule(target.type())?"unsupported":!target.enabled()?"disabled":HeartModules.status(target,heart);
                if(reason.equals("valid"))reason=target.value("trigger")!=4?"trigger":"";
                if(!reason.isEmpty()) {var issue=new CompoundTag();issue.putUUID("Module",action.module());issue.putInt("Action",i+1);issue.putString("Reason",reason);problems.add(issue);}
            }
            row.put("Problems",problems); rows.add(row);
        }
        tag.put("Rows", rows); return tag;
    }
}
