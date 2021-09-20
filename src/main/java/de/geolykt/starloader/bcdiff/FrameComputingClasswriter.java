package de.geolykt.starloader.bcdiff;

import java.util.Map;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

public class FrameComputingClasswriter extends ClassWriter {

    protected final Map<String, ClassNode> nodes;

    class ClassWrapper {

        final String superName;
        final String[] superInterfaces;
        final String name;
        final boolean itf;

        public ClassWrapper(String name) {
            if (name.equals("java/lang/Object")) {
                this.name = name;
                this.itf = false;
                this.superInterfaces = new String[0];
                this.superName = null;
                return;
            }
            this.name = name;
            ClassNode asmNode = nodes.get(name);
            if (asmNode == null) {
                Class<?> clazz;
                try {
                    clazz = Class.forName(name.replace('/', '.'), false, getClassLoader());
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                    superName = "java/lang/Object";
                    itf = false;
                    superInterfaces = new String[0];
                    return;
                }
                itf = clazz.isInterface();
                if (itf) {
                    superName = "java/lang/Object";
                } else {
                    superName = clazz.getSuperclass().getName().replace('.', '/');
                }
                Class<?>[] interfaces = clazz.getInterfaces();
                superInterfaces = new String[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    superInterfaces[i] = interfaces[i].getName().replace('.', '/');
                }
            } else {
                superName = asmNode.superName;
                itf = (asmNode.access & Opcodes.ACC_INTERFACE) != 0;
                superInterfaces = asmNode.interfaces.toArray(new String[0]);
            }
        }

        ClassWrapper getSuperWrapper() {
            String superName = getSuper();
            if (superName == null) {
                throw new IllegalStateException(name + " does not have a super type.");
            }
            return new ClassWrapper(superName);
        }

        String getSuper() {
            return superName;
        }

        String getName() {
            return name;
        }

        String[] getSuperInterfacesName() {
            return superInterfaces;
        }

        public boolean isInterface() {
            return itf;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ClassWrapper) {
                return ((ClassWrapper) obj).getName().equals(this.getName());
            }
            return false;
        }

        @Override
        public int hashCode() {
            return getName().hashCode();
        }
    }

    private boolean isImplementingInterface(ClassWrapper clazz, String interfaceName) {
        if (clazz.name.equals("java/lang/Object")) {
            return false;
        }
        for (String interfaces : clazz.getSuperInterfacesName()) {
            if (interfaces.equals(interfaceName)) {
                return true;
            } else {
                if (isImplementingInterface(clazz, interfaceName)) {
                    return true;
                }
            }
        }
        if (clazz.itf) {
            return false;
        }
        return isImplementingInterface(clazz.getSuperWrapper(), interfaceName);
    }

    protected boolean canAssign(ClassWrapper superType, ClassWrapper subType) {
        final String name = superType.name;
        if (superType.itf) {
            return isImplementingInterface(subType, name);
        } else {
            while (subType != null) {
                if (name.equals(subType.name) || name.equals(subType.superName)) {
                    return true;
                }
                if (subType.name.equals("java/lang/Object")) {
                    return false;
                }
                subType = subType.getSuperWrapper();
            }
        }
        return false;
    }

    public FrameComputingClasswriter(int flags, Map<String, ClassNode> allNodes) {
        super(flags | COMPUTE_FRAMES);
        this.nodes = allNodes;
    }

    @Override
    protected String getCommonSuperClass(String type1, String type2) {
        if (type1.equals("java/lang/Object")) {
            return type1;
        }
        if (type2.equals("java/lang/Object")) {
            return type2;
        }
        ClassWrapper class1 = new ClassWrapper(type1);
        ClassWrapper class2 = new ClassWrapper(type2);
        // isAssignableFrom = class1 = class2;
        if (canAssign(class1, class2)) {
            return type1;
        }
        if (canAssign(class2, class1)) {
            return type2;
        }
        if (class1.isInterface() || class2.isInterface()) {
            return "java/lang/Object";
        }
        return getCommonSuperClass(type1, class2.getSuper());
    }
}
