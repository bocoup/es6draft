/**
 * Copyright (c) 2012-2015 André Bargull
 * Alle Rechte vorbehalten / All Rights Reserved.  Use is subject to license terms.
 *
 * <https://github.com/anba/es6draft>
 */
package com.github.anba.es6draft.compiler.assembler;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.Printer;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

/**
 * Class encapsulating generated bytecode.
 */
public final class Code {
    private static final int METHOD_LIMIT = 1 << 12;
    private static final int JAVA_VERSION = Opcodes.V1_7;

    private final ArrayList<ClassCode> classes = new ArrayList<>();
    private final SourceInfo sourceInfo;
    private final ClassCode mainClass;
    private ExternConstantPool sharedConstantPool = null;
    private ClassCode currentClass;

    /**
     * Constructs a new code object.
     * 
     * @param access
     *            the access flags
     * @param className
     *            the internal class name
     * @param signature
     *            the class signature
     * @param superClass
     *            the super-class
     * @param interfaces
     *            the list of interfaces
     * @param sourceInfo
     *            the source information object
     */
    public Code(int access, String className, ClassSignature signature, Type superClass,
            List<Type> interfaces, SourceInfo sourceInfo) {
        this.sourceInfo = sourceInfo;
        this.mainClass = newMainClass(this, access, className, signature, superClass, interfaces);
        classes.add(mainClass);
        setCurrentClass(mainClass);
    }

    private void setCurrentClass(ClassCode currentClass) {
        this.currentClass = currentClass;
    }

    private ClassCode requestClassForMethod() {
        if (currentClass.methodCount() >= METHOD_LIMIT) {
            setCurrentClass(newClass(new InlineConstantPool(this)));
        }
        return currentClass;
    }

    private ClassCode newMainClass(Code code, int access, String className,
            ClassSignature signature, Type superClass, List<Type> interfaces) {
        ConstantPool constantPool = new InlineConstantPool(code);
        return newClass(constantPool, access, className, signature, superClass, interfaces,
                sourceInfo);
    }

    private ClassCode newClass(ConstantPool constantPool, int access, String className) {
        return newClass(constantPool, access, className, ClassSignature.NONE, Types.Object,
                Collections.<Type> emptyList(), sourceInfo);
    }

    private static ClassCode newClass(ConstantPool constantPool, int access, String className,
            ClassSignature signature, Type superClass, List<Type> interfaces, SourceInfo sourceInfo) {
        if ((access & ~Modifier.classModifiers()) != 0) {
            throw new IllegalArgumentException();
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(JAVA_VERSION, access | Opcodes.ACC_SUPER, className, signature.toString(),
                superClass.internalName(), toInternalNames(interfaces));
        cw.visitSource(sourceInfo.getFileName(), sourceInfo.getSourceMap());

        return new ClassCode(constantPool, className, cw);
    }

    private static String[] toInternalNames(List<Type> types) {
        if (types.isEmpty()) {
            return null;
        }
        int i = 0;
        String[] names = new String[types.size()];
        for (Type type : types) {
            names[i++] = type.internalName();
        }
        return names;
    }

    /**
     * Returns the list of generated {@link ClassCode} objects.
     * 
     * @return the list of generated class code objects
     */
    public List<ClassCode> getClasses() {
        return classes;
    }

    /**
     * Returns the shared constant pool instance.
     * 
     * @return the shared constant pool
     */
    ConstantPool getSharedConstantPool() {
        if (sharedConstantPool == null) {
            sharedConstantPool = new ExternConstantPool(this);
        }
        return sharedConstantPool;
    }

    /**
     * Adds a new class.
     * 
     * @param constantPool
     *            the constant pool instance to use
     * @return the class code instance which represents the new class
     */
    ClassCode newClass(ConstantPool constantPool) {
        int access = Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL;
        String className = mainClass.className + '~' + classes.size();
        ClassCode classCode = newClass(constantPool, access, className);
        classes.add(classCode);
        return classCode;
    }

    /**
     * Adds a new method to main class module.
     * 
     * @param access
     *            the access flag
     * @param methodDescriptor
     *            the method descriptor
     * @return the method code instance which represents the new method
     */
    public MethodCode newConstructor(int access, MethodTypeDescriptor methodDescriptor) {
        return mainClass.newConstructor(access, methodDescriptor, null, null);
    }

    /**
     * Adds a new method to a class module.
     * 
     * @param access
     *            the access flag
     * @param methodName
     *            the name of the new method
     * @param methodDescriptor
     *            the method descriptor
     * @return the method code instance which represents the new method
     */
    public MethodCode newMethod(int access, String methodName, MethodTypeDescriptor methodDescriptor) {
        return requestClassForMethod().newMethod(access, methodName, methodDescriptor, null, null);
    }

    /**
     * Returns the decompiled byte code.
     * 
     * @param bytes
     *            the class byte code
     * @param simpleTypes
     *            {@code true} to use simple type representation
     * @return the decompiled byte code
     */
    public static String toByteCode(byte[] bytes, boolean simpleTypes) {
        Printer p = simpleTypes ? new SimpleTypeTextifier() : new Textifier();
        return toByteCode(new TraceClassVisitor(null, p, null), p, bytes);
    }

    /**
     * Returns the decompiled method byte code.
     * 
     * @param bytes
     *            the class byte code
     * @param methodName
     *            the method name
     * @return the decompiled byte code
     */
    public static String methodToByteCode(byte[] bytes, String methodName) {
        Printer p = new SimpleTypeTextifier();
        return toByteCode(new FilterMethodVisitor(p, methodName), p, bytes);
    }

    private static String toByteCode(ClassVisitor cv, Printer p, byte[] bytes) {
        new ClassReader(bytes).accept(cv, ClassReader.EXPAND_FRAMES);

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        p.print(pw);
        pw.flush();
        return sw.toString();
    }

    private static final class FilterMethodVisitor extends ClassVisitor {
        private final Printer printer;
        private final String methodName;

        public FilterMethodVisitor(Printer p, String methodName) {
            super(Opcodes.ASM5);
            this.printer = p;
            this.methodName = methodName;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature,
                String[] exceptions) {
            if (methodName.equals(name)) {
                Printer p = printer.visitMethod(access, name, desc, signature, exceptions);
                return new TraceMethodVisitor(null, p);
            }
            return null;
        }
    }

    /**
     * Class representing method code.
     */
    public static final class MethodCode {
        public final ClassCode classCode;
        public final int access;
        public final String methodName;
        public final MethodTypeDescriptor methodDescriptor;
        public final MethodVisitor methodVisitor;

        MethodCode(ClassCode classCode, int access, String methodName,
                MethodTypeDescriptor methodDescriptor, MethodVisitor methodVisitor) {
            this.classCode = classCode;
            this.access = access;
            this.methodName = methodName;
            this.methodDescriptor = methodDescriptor;
            this.methodVisitor = methodVisitor;
        }
    }

    /**
     * Class representing class code.
     */
    public static final class ClassCode {
        private int methodCount = 0;
        public final ConstantPool constantPool;
        public final String className;
        public final Type classType;
        public final ClassWriter classWriter;

        ClassCode(ConstantPool constantPool, String className, ClassWriter classWriter) {
            this.constantPool = constantPool;
            this.className = className;
            this.classType = Type.forName(className);
            this.classWriter = classWriter;
        }

        int methodCount() {
            return methodCount;
        }

        public byte[] toByteArray() {
            constantPool.close();
            classWriter.visitEnd();
            return classWriter.toByteArray();
        }

        public MethodCode newConstructor(int access, MethodTypeDescriptor methodDescriptor,
                String signature, String[] exceptions) {
            if ((access & ~Modifier.constructorModifiers()) != 0) {
                throw new IllegalArgumentException();
            }
            methodCount += 1;
            return new MethodCode(this, access, "<init>", methodDescriptor,
                    classWriter.visitMethod(access, "<init>", methodDescriptor.descriptor(),
                            signature, exceptions));
        }

        public MethodCode newMethod(int access, String methodName,
                MethodTypeDescriptor methodDescriptor, String signature, String[] exceptions) {
            if ((access & ~Modifier.methodModifiers()) != 0) {
                throw new IllegalArgumentException();
            }
            methodCount += 1;
            return new MethodCode(this, access, methodName, methodDescriptor,
                    classWriter.visitMethod(access, methodName, methodDescriptor.descriptor(),
                            signature, exceptions));
        }

        public void addField(int access, String fieldName, Type fieldDescriptor, String signature) {
            if ((access & ~Modifier.fieldModifiers()) != 0) {
                throw new IllegalArgumentException();
            }
            classWriter
                    .visitField(access, fieldName, fieldDescriptor.descriptor(), signature, null)
                    .visitEnd();
        }
    }
}
