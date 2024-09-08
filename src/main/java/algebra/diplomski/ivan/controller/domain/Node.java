package algebra.diplomski.ivan.controller.domain;

public abstract class Node {
    String clazz;
    public String getClazz() {return this.clazz;}

    String id;
    public String getId() {return this.id;}

    String name;
    public String getName() { return name; }

    boolean isAbstract;
    public boolean isAbstract() {return this.isAbstract;}

    public Node(String id, String name, String clazz) {
        this.id=id;
        this.name=name;
    }

    @Override
    public String toString() {
        return name;
    }
}
