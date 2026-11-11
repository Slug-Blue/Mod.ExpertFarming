package io.github.slugblue.wildroots.arch;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * WR-198: references of a class's bytecode to classes under a package prefix
 * ({@code a/b/client/}) outside the bodies of DistExecutor-guarded lambdas.
 *
 * <p>A synthetic {@code lambda$} method is <em>guarded</em> when the
 * {@code invokedynamic} that creates it is directly followed by a call of
 * {@code net.minecraftforge.fml.DistExecutor} ({@code unsafeRunWhenOn},
 * {@code safeCallWhenOn}, …), or when it is created inside a guarded lambda
 * (the {@code () -> () -> Client.x()} shape). References inside guarded
 * lambdas are allowed; every other reference is reported: super types,
 * fields, method signatures and {@code throws}, annotations (also class
 * values), and the instructions of every other method — calls, field
 * accesses, {@code new}, casts, {@code instanceof}, class literals,
 * {@code catch} types, method references and the method types of
 * {@code invokedynamic} sites. {@code DistExecutor.runForDist} with two
 * suppliers is not recognised (the client supplier is not followed by the
 * call); the mod does not use it.
 */
final class ClientReferences {

    static final String DIST_EXECUTOR = "net/minecraftforge/fml/DistExecutor";
    static final Pattern DIST_CALL = Pattern.compile("(unsafe|safe)?(Run|Call)WhenOn");

    private ClientReferences() {
    }

    static List<String> outsideLambdas(Class<?> type, String prefix) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            if (in == null) {
                return List.of("class file not found: " + resource);
            }
            ClassNode c = new ClassNode();
            new ClassReader(in).accept(c, ClassReader.SKIP_FRAMES);
            return scan(c, prefix);
        } catch (IOException e) {
            return List.of("cannot read " + resource + ": " + e);
        }
    }

    static boolean hits(String text, String prefix) {
        return text != null && text.contains(prefix);
    }

    static AbstractInsnNode nextReal(AbstractInsnNode i) {
        AbstractInsnNode n = i.getNext();
        while (n != null && n.getOpcode() < 0) {
            n = n.getNext();
        }
        return n;
    }

    /** Synthetic lambda methods of {@code c} whose bodies run only on the client. */
    static Set<String> guardedLambdas(ClassNode c) {
        Map<String, String> createdIn = new HashMap<>();
        Set<String> direct = new HashSet<>();
        for (MethodNode m : c.methods) {
            for (AbstractInsnNode i : m.instructions) {
                if (!(i instanceof InvokeDynamicInsnNode indy) || indy.bsmArgs.length < 2
                        || !(indy.bsmArgs[1] instanceof Handle h) || !h.getOwner().equals(c.name)
                        || !h.getName().startsWith("lambda$")) {
                    continue;
                }
                createdIn.put(h.getName(), m.name);
                if (nextReal(i) instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESTATIC
                        && call.owner.equals(DIST_EXECUTOR) && DIST_CALL.matcher(call.name).matches()) {
                    direct.add(h.getName());
                }
            }
        }
        Set<String> guarded = new HashSet<>(direct);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (Map.Entry<String, String> e : createdIn.entrySet()) {
                if (!guarded.contains(e.getKey()) && guarded.contains(e.getValue())) {
                    guarded.add(e.getKey());
                    grew = true;
                }
            }
        }
        return guarded;
    }

    static List<String> scan(ClassNode c, String prefix) {
        List<String> out = new ArrayList<>();
        if (hits(c.superName, prefix) || hits(c.signature, prefix)) {
            out.add("super type " + c.superName);
        }
        for (String i : c.interfaces) {
            if (hits(i, prefix)) {
                out.add("interface " + i);
            }
        }
        annotations("class", c.visibleAnnotations, c.invisibleAnnotations, prefix, out);
        for (FieldNode f : c.fields) {
            if (hits(f.desc, prefix) || hits(f.signature, prefix)) {
                out.add("field " + f.name + " " + f.desc);
            }
            annotations("field " + f.name, f.visibleAnnotations, f.invisibleAnnotations, prefix, out);
        }
        Set<String> guarded = guardedLambdas(c);
        for (MethodNode m : c.methods) {
            if (guarded.contains(m.name) && (m.access & Opcodes.ACC_SYNTHETIC) != 0) {
                continue; // runs, and links its call sites, only on the client
            }
            String where = "method " + m.name + m.desc;
            if (hits(m.desc, prefix) || hits(m.signature, prefix)) {
                out.add(where + ": signature");
            }
            for (String e : m.exceptions) {
                if (hits(e, prefix)) {
                    out.add(where + ": throws " + e);
                }
            }
            annotations(where, m.visibleAnnotations, m.invisibleAnnotations, prefix, out);
            if (m.visibleParameterAnnotations != null) {
                for (List<AnnotationNode> a : m.visibleParameterAnnotations) {
                    annotations(where + " parameter", a, null, prefix, out);
                }
            }
            if (m.invisibleParameterAnnotations != null) {
                for (List<AnnotationNode> a : m.invisibleParameterAnnotations) {
                    annotations(where + " parameter", null, a, prefix, out);
                }
            }
            for (TryCatchBlockNode t : m.tryCatchBlocks) {
                if (hits(t.type, prefix)) {
                    out.add(where + ": catch " + t.type);
                }
            }
            for (AbstractInsnNode i : m.instructions) {
                instruction(where, i, prefix, out);
            }
        }
        return out;
    }

    private static void instruction(String where, AbstractInsnNode i, String prefix, List<String> out) {
        if (i instanceof MethodInsnNode n && (hits(n.owner, prefix) || hits(n.desc, prefix))) {
            out.add(where + ": calls " + n.owner + "." + n.name);
        } else if (i instanceof FieldInsnNode n && (hits(n.owner, prefix) || hits(n.desc, prefix))) {
            out.add(where + ": field " + n.owner + "." + n.name);
        } else if (i instanceof TypeInsnNode n && hits(n.desc, prefix)) {
            out.add(where + ": type " + n.desc);
        } else if (i instanceof LdcInsnNode n && n.cst instanceof Type t && hits(t.getDescriptor(), prefix)) {
            out.add(where + ": class literal " + t.getClassName());
        } else if (i instanceof MultiANewArrayInsnNode n && hits(n.desc, prefix)) {
            out.add(where + ": array " + n.desc);
        } else if (i instanceof InvokeDynamicInsnNode n) {
            if (hits(n.desc, prefix)) {
                out.add(where + ": invokedynamic " + n.name + n.desc);
            }
            for (Object a : n.bsmArgs) {
                if (a instanceof Handle h && (hits(h.getOwner(), prefix) || hits(h.getDesc(), prefix))) {
                    out.add(where + ": method handle " + h.getOwner() + "." + h.getName() + h.getDesc());
                } else if (a instanceof Type t && hits(t.getDescriptor(), prefix)) {
                    out.add(where + ": invokedynamic type " + t.getDescriptor());
                }
            }
        }
    }

    private static void annotations(String where, List<AnnotationNode> visible, List<AnnotationNode> invisible,
                                    String prefix, List<String> out) {
        for (List<AnnotationNode> list : java.util.Arrays.asList(visible, invisible)) {
            if (list == null) {
                continue;
            }
            for (AnnotationNode a : list) {
                if (hits(a.desc, prefix) || annotationValueHits(a.values, prefix)) {
                    out.add(where + ": annotation " + a.desc);
                }
            }
        }
    }

    private static boolean annotationValueHits(List<Object> values, String prefix) {
        if (values == null) {
            return false;
        }
        for (Object v : values) {
            if (v instanceof Type t && hits(t.getDescriptor(), prefix)
                    || v instanceof String[] e && e.length > 0 && hits(e[0], prefix)
                    || v instanceof AnnotationNode n && (hits(n.desc, prefix) || annotationValueHits(n.values, prefix))
                    || v instanceof List<?> l && annotationValueHits(new ArrayList<>(l), prefix)) {
                return true;
            }
        }
        return false;
    }
}
