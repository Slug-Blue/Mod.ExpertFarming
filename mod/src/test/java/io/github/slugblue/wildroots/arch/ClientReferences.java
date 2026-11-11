package io.github.slugblue.wildroots.arch;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * WR-198: references of a class's bytecode to classes under a package prefix
 * ({@code a/b/client/}) outside synthetic {@code lambda$} methods: super
 * types, field types, method signatures, and the instructions of every other
 * method (calls, field accesses, {@code new}, casts, {@code instanceof},
 * class literals, method references).
 */
final class ClientReferences {

    private ClientReferences() {
    }

    static List<String> outsideLambdas(Class<?> type, String prefix) {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getResourceAsStream(resource)) {
            if (in == null) {
                return List.of("class file not found: " + resource);
            }
            List<String> out = new ArrayList<>();
            new ClassReader(in).accept(new Visitor(prefix, out), ClassReader.SKIP_FRAMES);
            return out;
        } catch (IOException e) {
            return List.of("cannot read " + resource + ": " + e);
        }
    }

    static boolean hits(String text, String prefix) {
        return text != null && text.contains(prefix);
    }

    private static final class Visitor extends ClassVisitor {
        private final String prefix;
        private final List<String> out;

        Visitor(String prefix, List<String> out) {
            super(Opcodes.ASM9);
            this.prefix = prefix;
            this.out = out;
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
            if (hits(superName, prefix) || hits(signature, prefix)) {
                out.add("super type " + superName);
            }
            for (String i : interfaces == null ? new String[0] : interfaces) {
                if (hits(i, prefix)) {
                    out.add("interface " + i);
                }
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            if (hits(descriptor, prefix) || hits(signature, prefix)) {
                out.add("field " + name + " " + descriptor);
            }
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            if (name.startsWith("lambda$") && (access & Opcodes.ACC_SYNTHETIC) != 0) {
                return null; // runs, and links its call sites, only when the lambda is invoked
            }
            String where = "method " + name + descriptor;
            if (hits(descriptor, prefix) || hits(signature, prefix)) {
                out.add(where + ": signature");
            }
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) {
                    if (hits(owner, prefix) || hits(d, prefix)) {
                        out.add(where + ": calls " + owner + "." + n);
                    }
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String n, String d) {
                    if (hits(owner, prefix) || hits(d, prefix)) {
                        out.add(where + ": field " + owner + "." + n);
                    }
                }

                @Override
                public void visitTypeInsn(int opcode, String t) {
                    if (hits(t, prefix)) {
                        out.add(where + ": type " + t);
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type t && hits(t.getDescriptor(), prefix)) {
                        out.add(where + ": class literal " + t.getClassName());
                    }
                }

                @Override
                public void visitInvokeDynamicInsn(String n, String d, Handle bsm, Object... args) {
                    if (hits(d, prefix)) {
                        out.add(where + ": invokedynamic " + n + d);
                    }
                    for (Object a : args) {
                        if (a instanceof Handle h && hits(h.getOwner(), prefix)) {
                            out.add(where + ": method reference " + h.getOwner() + "." + h.getName());
                        }
                    }
                }

                @Override
                public void visitMultiANewArrayInsn(String d, int dims) {
                    if (hits(d, prefix)) {
                        out.add(where + ": array " + d);
                    }
                }
            };
        }
    }
}
