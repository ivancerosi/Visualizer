package algebra.diplomski.ivan.controller.visitors;

import algebra.diplomski.ivan.controller.service.GraphService;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

public class CallGraphClassVisitor extends ClassVisitor {
    private String className;
    private String superName;
    private boolean validVersion;
    private final ClassReader reader;
    private final GraphService graphService;

    private boolean syntheticClass=false;

    public CallGraphClassVisitor(InputStream classData, GraphService graphService) throws IOException {
        super(Opcodes.ASM7);
        this.graphService = graphService;
        this.reader = new ClassReader(classData);
    }

    public void visit() {
        reader.accept(this,0);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.className = name;
        this.superName = superName;
        if (version<=52) validVersion=true;
        else validVersion=false;
        if (!validVersion) return;


        boolean isInterface = (access & Opcodes.ACC_INTERFACE)>0;
        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT)>0;
        syntheticClass = (access & Opcodes.ACC_SYNTHETIC)>0;


        if (isInterface) graphService.recordInterfaceDeclaration(name, interfaces, syntheticClass);
        else graphService.recordClassDeclaration(name, superName, interfaces, isAbstract, syntheticClass);
    }

    @Override
    public MethodVisitor visitMethod(final int access,
                                     final String name,
                                     final String descriptor,
                                     final String signature,
                                     final String[] exceptions) {
        final MethodVisitor methodVisitor = super.visitMethod(access,name,descriptor,signature,exceptions);
        if (!validVersion) return methodVisitor;


        boolean isAbstract = (access & Opcodes.ACC_ABSTRACT)>0;
        boolean isSynthetic= (access & Opcodes.ACC_SYNTHETIC)>0;
        boolean isFinal= (access & Opcodes.ACC_FINAL)>0;
        isSynthetic |= syntheticClass;

        boolean isNotPolymorphic = false;
        isNotPolymorphic |= (access & Opcodes.ACC_PRIVATE)>0;
        isNotPolymorphic |= (access & Opcodes.ACC_STATIC)>0;
        isNotPolymorphic |= name.equals("<init>");
        isNotPolymorphic |= name.equals("<clinit>");

        // descriptor denotes arrays with single open bracket, needs to be fixed in order to prevent parsing errors
        String descFixedBrackets = descriptor.replace("[","[]");

        graphService.recordMethodDeclaration(className, name,descFixedBrackets, isAbstract, isSynthetic, !isNotPolymorphic);

        return new CallGraphMethodVisitor(className, superName, name, descFixedBrackets, graphService);
    }
}
