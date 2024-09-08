package algebra.diplomski.ivan.controller.dao;


import algebra.diplomski.ivan.controller.domain.MethodNode;
import algebra.diplomski.ivan.controller.domain.ClassNode;
import algebra.diplomski.ivan.controller.domain.Node;
import algebra.diplomski.ivan.controller.exceptions.VertexNotFoundException;
import com.tinkerpop.blueprints.Direction;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface GraphQueryDao {
    Map<String, MethodNode> getPolymorphicNodes();
    MethodNode addIntermediateNode(MethodNode forActualNode);
    boolean removeInvokesEdge(MethodNode source, MethodNode target);

    Map<String, MethodNode> getSyntheticNodes();
    Map<String, MethodNode> getCallers(MethodNode mn);
    Map<String, MethodNode> getCallees(MethodNode mn);
    ClassNode getClassByName(String name);

    void createIntermediateCall(MethodNode callee, Collection<MethodNode> candidates);
    Map<String, MethodNode> getPolymorphicCandidatesForCall(MethodNode callee);
    Map<String, MethodNode> getMethodsDeclaredOnlyInParent();
    MethodNode getRealMethodLocationInParent(MethodNode method);
    MethodNode getRealMethodLocationInInterface(MethodNode method);
    void transferCallsToOtherMethod(MethodNode source, MethodNode realTarget);

    boolean addCallee(MethodNode target, MethodNode callee);
    boolean addCaller(MethodNode target, MethodNode callee);

    boolean removeMethodNode(MethodNode target);

    Node getVertexById(String id);

    void rollUpMethodRelations();

    String traverseFromNode(String id, String resolution, Direction dir, int limit) throws VertexNotFoundException;
}
