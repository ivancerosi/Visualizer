package algebra.diplomski.ivan.controller.visitors;

import algebra.diplomski.ivan.controller.service.GraphService;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CallGraphMethodVisitor extends MethodVisitor {
    final String className;
    final String parentName;
    final String methodName;
    final String methodDescriptor;
    final GraphService graphService;

    int cyclomaticComplexity=1;
    int instructionCount=0;
    protected CallGraphMethodVisitor(final String className, final String parentName, final String methodName, final String methodDescriptor, GraphService gs) {
        super(Opcodes.ASM7);
        this.className=className;
        this.parentName=parentName;
        this.methodName = methodName;
        this.methodDescriptor = methodDescriptor;
        this.graphService = gs;
    }

    @Override
    public void visitInsn(int opcode) {
        super.visitInsn(opcode);
        instructionCount++;
    }

    @Override
    public void visitJumpInsn(int opcode, Label label) {
        super.visitJumpInsn(opcode, label);
        if (opcode != Opcodes.GOTO && opcode != Opcodes.JSR)
            cyclomaticComplexity++;
    }

    @Override
    public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
        super.visitLookupSwitchInsn(dflt, keys, labels);
        cyclomaticComplexity+=labels.length;
    }

    @Override
    public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
        super.visitTableSwitchInsn(min, max, dflt, labels);
        cyclomaticComplexity+=labels.length;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        graphService.recordInstructionComplexity(className,methodName,methodDescriptor,instructionCount);
        graphService.recordCyclomaticComplexity(className,methodName,methodDescriptor,cyclomaticComplexity);
    }

    @Override
    public void visitMethodInsn(final int opcode,
                                final String owner,
                                final String name,
                                final String descriptor,
                                final boolean isInterface) {
        super.visitMethodInsn(opcode,owner,name,descriptor,isInterface);
        if ("java/lang/Object".equals(owner)) return;

        boolean polymorphic = opcode==Opcodes.INVOKEINTERFACE || opcode==Opcodes.INVOKEVIRTUAL || opcode==Opcodes.INVOKEDYNAMIC;

        // descriptor denotes arrays with single open bracket, needs to be fixed in order to prevent parsing errors
        String descFixedBrackets = descriptor.replace("[","[]");

        graphService.recordMethodCall(className, methodName, methodDescriptor, owner, name, descFixedBrackets, polymorphic);
        if ("<init>".equals(name) && !("<init>".equals(methodName) && owner.equals(this.parentName))) {
            graphService.recordClassInitialized(owner);
        }
    }

    @Override
    public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);

        int i=bootstrapMethodArguments.length;
        for (i=i-1; i>=0; i--) {
            if (bootstrapMethodArguments[i] instanceof Handle) {
                Handle handle = (Handle) bootstrapMethodArguments[i];
                graphService.recordMethodCall(className, methodName, methodDescriptor, handle.getOwner(), handle.getName(), handle.getDesc(), true);
                break;
            }
        }
    }
}
