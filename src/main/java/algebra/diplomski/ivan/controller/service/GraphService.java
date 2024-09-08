package algebra.diplomski.ivan.controller.service;


import algebra.diplomski.ivan.controller.dao.*;
import algebra.diplomski.ivan.controller.domain.ClassNode;
import algebra.diplomski.ivan.controller.domain.MethodNode;
import algebra.diplomski.ivan.controller.domain.Node;
import algebra.diplomski.ivan.controller.exceptions.VertexNotFoundException;
import com.tinkerpop.blueprints.Direction;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class GraphService {
    DeclarationDao declarationDao;
    ComplexityDao complexityDao;
    ClassInitializationDao initializationDao;
    ClassInitializationDao persistentInitilizationDao;
    MethodCallDao methodCallDao;
    GraphQueryDao graphQueryDao;

    public<T extends DeclarationDao & ComplexityDao & ClassInitializationDao & MethodCallDao & GraphQueryDao> GraphService(T dao) {
        this.declarationDao = dao;
        this.complexityDao = dao;
        this.initializationDao = new InMemoryInitDao();
        this.persistentInitilizationDao = dao;
        this.methodCallDao = dao;
        this.graphQueryDao = dao;
    }
    public GraphService(DeclarationDao declarationDao, ComplexityDao complexityDao,
                        ClassInitializationDao initializationDao,
                        MethodCallDao methodCallDao,
                        GraphQueryDao graphQueryDao) {
        this.declarationDao = declarationDao;
        this.complexityDao = complexityDao;
        this.initializationDao = initializationDao;
        this.methodCallDao = methodCallDao;
        this.graphQueryDao = graphQueryDao;
    }

    public void rollUpMethodRelations() {
        graphQueryDao.rollUpMethodRelations();
    }

    public void recordClassDeclaration(String className, String parent, String[] interfaces, boolean isAbstract,
                                       boolean isSynthetic) {
        if ("java/lang/Object".equals(parent)) parent=null;
        declarationDao.recordClassDeclaration(className,parent,interfaces,isAbstract, isSynthetic);
    }

    public void recordInterfaceDeclaration(String interfaceName, String[] interfaces, boolean isSynthetic) {
        if (interfaces!=null && interfaces.length>0) {
            declarationDao.recordInterfaceDeclaration(interfaceName, interfaces, isSynthetic);
        } else declarationDao.recordInterfaceDeclaration(interfaceName, null, isSynthetic);
    }

    public void recordMethodDeclaration(String className, String methodName, String descriptor, boolean isAbstract, boolean isSynthetic, boolean isPolymorphic) {
        declarationDao.recordMethodDeclaration(className, methodName, descriptor, isAbstract, isSynthetic, isPolymorphic);
    }

    public void recordCyclomaticComplexity(String className, String methodName, String descriptor, int complexity) {
        complexityDao.recordCyclomaticComplexity(className,methodName,descriptor,complexity);
    }

    public void recordInstructionComplexity(String className, String methodName, String descriptor, int complexity) {
        complexityDao.recordInstructionComplexity(className,methodName,descriptor,complexity);
    }

    public void recordClassInitialized(String className) {
        initializationDao.recordClassInitialized(className);
    }
    public Set<String> getInitializedClasses() {
        return initializationDao.getInitializedClasses();
    }

    public void persistInitializedClasses(Set<String> inits) {
        inits.forEach(it->this.persistentInitilizationDao.recordClassInitialized(it));
    }

    public void recordMethodCall(String callerClass, String callerMethod, String callerDescriptor, String calleeClass,
                          String calleeMethod, String calleeDescriptor, boolean polymorphic) {
        methodCallDao.recordMethodCall(callerClass,callerMethod,callerDescriptor,calleeClass, calleeMethod,
                calleeDescriptor, polymorphic);
    }

    public Map<String, MethodNode> getSyntheticNodes() {
        return graphQueryDao.getSyntheticNodes();
    }
    public Map<String, MethodNode> getCallers(MethodNode methodNode) {
        return graphQueryDao.getCallers(methodNode);
    }
    public Map<String, MethodNode> getCallees(MethodNode methodNode) {
        return graphQueryDao.getCallees(methodNode);
    }
    public boolean addCaller(MethodNode target, MethodNode caller) {
        return graphQueryDao.addCaller(target, caller);
    }
    public boolean addCallee(MethodNode target, MethodNode callee) {
        return graphQueryDao.addCallee(target, callee);
    }

    public boolean removeMethodNode(MethodNode target) {
        return graphQueryDao.removeMethodNode(target);
    }

    public boolean removeInvokesEdge(MethodNode source, MethodNode target) { return graphQueryDao.removeInvokesEdge(source,target);}

    public Map<String,MethodNode> getPolymorphicNodes() {
        return graphQueryDao.getPolymorphicNodes();
    }

    public MethodNode addIntermediateNode(MethodNode forNode) {
        return graphQueryDao.addIntermediateNode(forNode);
    }

    public ClassNode getClassByName(String name) {
        return graphQueryDao.getClassByName(name);
    }

    public Map<String, MethodNode> getMethodsDeclaredOnlyInParent() {
        return graphQueryDao.getMethodsDeclaredOnlyInParent();
    }

    public MethodNode getRealMethodLocationInInterface(MethodNode method) {
        return graphQueryDao.getRealMethodLocationInInterface(method);
    }

    public MethodNode getRealMethodLocationInParent(MethodNode method) {
        return graphQueryDao.getRealMethodLocationInParent(method);
    }

    public void createIntermediateCall(MethodNode callee, Collection<MethodNode> candidates) {
        graphQueryDao.createIntermediateCall(callee, candidates);
    }

    public Map<String, MethodNode> getPolymorphicCandidatesForCall(MethodNode callee) {
        return graphQueryDao.getPolymorphicCandidatesForCall(callee);
    }

    public void transferCallsToOtherMethod(MethodNode source, MethodNode realTarget) {
        graphQueryDao.transferCallsToOtherMethod(source, realTarget);
    }

    public Node getVertexById(String id) {
        return graphQueryDao.getVertexById(id);
    }

    public String traverseFromNode(String id, String resolution, Direction dir, int limit) throws VertexNotFoundException {
        return graphQueryDao.traverseFromNode(id, resolution, dir, limit);
    }

    public String getClassList(String substring) {
        return declarationDao.getClassList(substring);
    }

    public String getMethodList(String substring) {
        return declarationDao.getMethodList(substring);
    }

    public String getMethodsByClass(String classId) {
        return declarationDao.getMethodsByClass(classId);
    }
}
