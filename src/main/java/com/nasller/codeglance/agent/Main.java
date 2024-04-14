package com.nasller.codeglance.agent;

import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.Label;
import org.jetbrains.org.objectweb.asm.tree.*;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;

import static org.jetbrains.org.objectweb.asm.Opcodes.*;

public class Main {
    public static void premain(String args, Instrumentation inst) throws UnmodifiableClassException {
        agentmain(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) throws UnmodifiableClassException {
        String hookClassName = "com/intellij/openapi/editor/impl/EditorImpl";
        inst.addTransformer(new ClassFileTransformer() {
            @Override
            public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {
                if(!hookClassName.equals(className)){
                    return classfileBuffer;
                }
                ClassReader reader = new ClassReader(classfileBuffer);
                ClassNode node = new ClassNode(ASM5);
                reader.accept(node, 0);
                for (MethodNode mn : node.methods) {
                    if ("getStickyLinesPanelWidth".equals(mn.name) && mn.desc.startsWith("()I")) {
                        mn.instructions.clear();
                        InsnList list = new InsnList();
                        list.add(new LabelNode());
                        list.add(new VarInsnNode(ALOAD,0));
                        list.add(new FieldInsnNode(GETFIELD, hookClassName,
                                "myPanel", "Ljavax/swing/JPanel;"));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "javax/swing/JPanel", "getLayout",
                                "()Ljava/awt/LayoutManager;", false));
                        list.add(new VarInsnNode(ASTORE,1));
                        list.add(new LabelNode());
                        list.add(new VarInsnNode(ALOAD,1));
                        list.add(new TypeInsnNode(INSTANCEOF, "java/awt/BorderLayout"));
                        Label label2 = new Label();
                        list.add(new JumpInsnNode(IFEQ, new LabelNode(label2)));
                        list.add(new LabelNode());
                        list.add(new VarInsnNode(ALOAD,1));
                        list.add(new TypeInsnNode(CHECKCAST, "java/awt/BorderLayout"));
                        list.add(new LdcInsnNode("After"));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/BorderLayout", "getLayoutComponent",
                                "(Ljava/lang/Object;)Ljava/awt/Component;", false));
                        list.add(new VarInsnNode(ASTORE, 2));
                        list.add(new LabelNode());
                        list.add(new VarInsnNode(ALOAD,2));
                        list.add(new JumpInsnNode(IFNULL, new LabelNode(label2)));
                        list.add(new LabelNode());
                        list.add(new VarInsnNode(ALOAD,0));
                        list.add(new FieldInsnNode(GETFIELD, hookClassName,
                                "myPanel", "Ljavax/swing/JPanel;"));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "javax/swing/JPanel", "getWidth",
                                "()I", false));
                        list.add(new VarInsnNode(ALOAD,2));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "java/awt/Component", "getWidth",
                                "()I", false));
                        list.add(new InsnNode(ISUB));
                        list.add(new InsnNode(IRETURN));
                        list.add(new LabelNode(label2));
                        list.add(new VarInsnNode(ALOAD,0));
                        list.add(new FieldInsnNode(GETFIELD, hookClassName,
                                "myPanel", "Ljavax/swing/JPanel;"));
                        list.add(new MethodInsnNode(INVOKEVIRTUAL, "javax/swing/JPanel", "getWidth",
                                "()I", false));
                        list.add(new InsnNode(IRETURN));
                        mn.instructions.add(list);
                    }
                }
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            }
        }, true);
        List<Class<?>> classesToRetransform = new ArrayList<>();
        for (Class<?> clazz : inst.getAllLoadedClasses()) {
            String className = clazz.getName();
            if (hookClassName.equals(className)) {
                classesToRetransform.add(clazz);
            }
        }
        inst.retransformClasses(classesToRetransform.toArray(new Class[0]));
    }
}