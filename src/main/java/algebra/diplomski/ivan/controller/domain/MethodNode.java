package algebra.diplomski.ivan.controller.domain;

public class MethodNode extends algebra.diplomski.ivan.controller.domain.Node {

    public MethodNode(String id, String className, String methodName, String descriptor) {
        super(id,methodName,"Method");
        this.className = className;
        this.methodName = methodName;
        this.descriptor = descriptor;
    }

    String qualifiedName;
    public void setQualifiedName(String qualifiedName) {
        this.qualifiedName = qualifiedName;
    }
    public String getQualifiedName() {
        return this.qualifiedName;
    }

    private boolean isPolymorphic;
    public void setIsPolymorphic(boolean polymorphic) {this.isPolymorphic=polymorphic;}
    public boolean isPolymorphic() {return this.isPolymorphic;}

    @Override
    public String getName() {return this.methodName;}

    private String className;
    public void setClassName(String className) {
        this.className = className;
    }
    public String getClassName() {
        return className;
    }

    private String methodName;
    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }
    public String getMethodName() {
        return this.methodName;
    }

    private String descriptor;
    public void setDescriptor(String descriptor) {
        this.descriptor = descriptor;
    }
    public String getDescriptor() {
        return this.descriptor;
    }

    private boolean isParsed;
    public void setIsParsed(boolean isParsed) {
        this.isParsed = isParsed;
    }
    public boolean isParsed() {
        return this.isParsed;
    }

    private boolean isAbstract;
    public void setIsAbstract(boolean isAbstract) {
        this.isAbstract = isAbstract;
    }
    public boolean isAbstract() {
        return this.isAbstract;
    }

    private boolean isSynthetic;
    public void setIsSynthetic(boolean isSynthetic) {
        this.isSynthetic = isSynthetic;
    }
    public boolean isSynthetic() {
        return this.isSynthetic;
    }

    private int instructionComplexity;
    public void setInstructionComplexity(int instructionComplexity) {
        this.instructionComplexity = instructionComplexity;
    }
    public int getInstructionComplexity() {
        return this.instructionComplexity;
    }

    private int cyclomaticComplexity;
    public void setCyclomaticComplexity(int cyclomaticComplexity) {
        this.cyclomaticComplexity = cyclomaticComplexity;
    }
    public int getCyclomaticComplexity() {
        return this.cyclomaticComplexity;
    }


    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        return className+"."+methodName+descriptor;
    }


}
