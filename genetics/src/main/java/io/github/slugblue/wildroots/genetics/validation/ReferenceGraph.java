package io.github.slugblue.wildroots.genetics.validation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Resolution of references between definitions (SEC-29): cultivar →
 * species, cultivar → parent cultivar, village template → landrace, role →
 * tag. One iterative pass with an explicit stack (no recursion, so no
 * {@code StackOverflowError}):
 * <ul>
 *   <li>a cycle → ERROR for every record of the cycle;</li>
 *   <li>a chain deeper than 16 → ERROR for the records beyond the depth;</li>
 *   <li>a dangling reference → ERROR for the record and every record that depends on it.</li>
 * </ul>
 */
public final class ReferenceGraph {

    private ReferenceGraph() {
    }

    /**
     * @param edges node id → referenced node ids; every node that exists is a key
     * @return problems with the node id as path; the set of rejected nodes is
     *         {@link #rejected(List)}
     */
    public static List<Problem> check(Map<String, List<String>> edges) {
        Map<String, List<String>> g = new TreeMap<>(edges);
        Map<String, String> bad = new TreeMap<>();
        // dangling
        for (Map.Entry<String, List<String>> e : g.entrySet()) {
            for (String ref : e.getValue()) {
                if (!g.containsKey(ref)) {
                    bad.putIfAbsent(e.getKey(), "dangling reference to " + ref);
                }
            }
        }
        // cycles: iterative Tarjan
        for (Set<String> scc : stronglyConnected(g)) {
            boolean cyclic = scc.size() > 1;
            if (!cyclic) {
                String only = scc.iterator().next();
                cyclic = g.get(only).contains(only);
            }
            if (cyclic) {
                String msg = "reference cycle of " + scc.size() + " record(s) through " + scc.iterator().next();
                for (String n : scc) {
                    bad.put(n, msg);
                }
            }
        }
        // depth: longest chain from each node over the acyclic remainder (memoised, iterative)
        Map<String, Integer> depth = new HashMap<>();
        for (String start : g.keySet()) {
            if (bad.containsKey(start) || depth.containsKey(start)) {
                continue;
            }
            Deque<String> stack = new ArrayDeque<>();
            stack.push(start);
            while (!stack.isEmpty()) {
                String n = stack.peek();
                boolean ready = true;
                for (String ref : g.get(n)) {
                    if (g.containsKey(ref) && !bad.containsKey(ref) && !depth.containsKey(ref)) {
                        stack.push(ref);
                        ready = false;
                    }
                }
                if (ready) {
                    stack.pop();
                    int d = 1;
                    for (String ref : g.get(n)) {
                        if (depth.containsKey(ref)) {
                            d = Math.max(d, depth.get(ref) + 1);
                        }
                    }
                    depth.put(n, d);
                }
            }
        }
        for (Map.Entry<String, Integer> e : depth.entrySet()) {
            if (e.getValue() > DataLimits.MAX_REFERENCE_DEPTH && !bad.containsKey(e.getKey())) {
                bad.put(e.getKey(), "reference chain of depth " + e.getValue() + " > "
                        + DataLimits.MAX_REFERENCE_DEPTH);
            }
        }
        // dependents of rejected nodes are rejected too
        Map<String, List<String>> reverse = new HashMap<>();
        for (Map.Entry<String, List<String>> e : g.entrySet()) {
            for (String ref : e.getValue()) {
                reverse.computeIfAbsent(ref, k -> new ArrayList<>()).add(e.getKey());
            }
        }
        Deque<String> work = new ArrayDeque<>(bad.keySet());
        Set<String> seen = new HashSet<>(bad.keySet());
        while (!work.isEmpty()) {
            String n = work.pop();
            for (String dep : reverse.getOrDefault(n, List.of())) {
                if (seen.add(dep)) {
                    bad.put(dep, "depends on rejected " + n);
                    work.push(dep);
                }
            }
        }
        List<Problem> out = new ArrayList<>();
        for (Map.Entry<String, String> e : bad.entrySet()) {
            out.add(Problem.error(e.getKey(), e.getValue()));
        }
        return out;
    }

    /** Node ids rejected by {@link #check}. */
    public static Set<String> rejected(List<Problem> problems) {
        Set<String> out = new TreeSet<>();
        for (Problem p : problems) {
            if (p.isError()) {
                out.add(p.path());
            }
        }
        return out;
    }

    /** Iterative Tarjan SCC. */
    static List<Set<String>> stronglyConnected(Map<String, List<String>> g) {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> low = new HashMap<>();
        Set<String> onStack = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>();
        List<Set<String>> out = new ArrayList<>();
        int counter = 0;
        for (String root : g.keySet()) {
            if (index.containsKey(root)) {
                continue;
            }
            Deque<Object[]> call = new ArrayDeque<>(); // {node, next edge index}
            call.push(new Object[] {root, 0});
            index.put(root, counter);
            low.put(root, counter);
            counter++;
            stack.push(root);
            onStack.add(root);
            while (!call.isEmpty()) {
                Object[] frame = call.peek();
                String v = (String) frame[0];
                int i = (Integer) frame[1];
                List<String> refs = g.get(v);
                if (i < refs.size()) {
                    frame[1] = i + 1;
                    String w = refs.get(i);
                    if (!g.containsKey(w)) {
                        continue;
                    }
                    if (!index.containsKey(w)) {
                        index.put(w, counter);
                        low.put(w, counter);
                        counter++;
                        stack.push(w);
                        onStack.add(w);
                        call.push(new Object[] {w, 0});
                    } else if (onStack.contains(w)) {
                        low.put(v, Math.min(low.get(v), index.get(w)));
                    }
                } else {
                    call.pop();
                    if (!call.isEmpty()) {
                        String parent = (String) call.peek()[0];
                        low.put(parent, Math.min(low.get(parent), low.get(v)));
                    }
                    if (low.get(v).equals(index.get(v))) {
                        Set<String> scc = new TreeSet<>();
                        String w;
                        do {
                            w = stack.pop();
                            onStack.remove(w);
                            scc.add(w);
                        } while (!w.equals(v));
                        out.add(scc);
                    }
                }
            }
        }
        return out;
    }
}
