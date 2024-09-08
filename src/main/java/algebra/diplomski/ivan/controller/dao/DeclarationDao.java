package algebra.diplomski.ivan.controller.dao;

import algebra.diplomski.ivan.controller.domain.MethodNode;

import java.util.Set;

public interface DeclarationDao {
    void recordClassDeclaration(String className, String parent, String[] interfaces, boolean isAbstract, boolean isSynthetic);
    void recordInterfaceDeclaration(String interfaceName, String[] interfaces, boolean isSynthetic);
    void recordMethodDeclaration(String className, String methodName, String descriptor, boolean isAbstract, boolean isSynthetic, boolean isPolymorphic);

    Set<MethodNode> getImplementorsForInterface(String interfacename);


    String getClassList(String substring);
    String getMethodList(String substring);
    String getMethodsByClass(String clazz);
}
