package algebra.diplomski.ivan.controller.domain;

public class ClassNode extends Node {

    ClassNode parent;
    public ClassNode getParent() {
        return this.parent;
    }


    boolean isInterface;
    public boolean isInterface() {return isInterface;}
    public void setInterface(boolean isInterface) {
        this.isInterface=isInterface;
    }

    boolean instantiated=false;
    public boolean isInstantiated() {
        return this.instantiated;
    }
    public void setIsInstantiated(boolean isInstantiated) {
        this.instantiated = isInstantiated;
    }

    boolean synthetic=false;
    public void setIsSynthetic(boolean synth) {
        this.synthetic = synth;
    }
    public boolean isSynthetic() {
        return this.synthetic;
    }

    boolean isParsed=true;
    public void setIsParsed(boolean parsed) {
        this.isParsed=parsed;
    }
    public boolean isParsed() {return this.isParsed;}

    public ClassNode(String id, String name, boolean isInterface, boolean isAbstract, boolean synthetic) {
        super(id,name,"Class");
        this.isInterface=isInterface;
        this.isAbstract=isAbstract;
        this.synthetic = synthetic;
    }


    public void setAbstract(boolean isAbstract) {
        this.isAbstract=isAbstract;
    }

}
