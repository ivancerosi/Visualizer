package algebra.diplomski.ivan.controller;

import algebra.diplomski.ivan.controller.dao.DaoOrientDB;
import algebra.diplomski.ivan.controller.domain.ClassNode;
import algebra.diplomski.ivan.controller.domain.MethodNode;
import algebra.diplomski.ivan.controller.exceptions.VertexNotFoundException;
import algebra.diplomski.ivan.controller.service.GraphService;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientEdge;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;


@SpringBootTest
class ControllerApplicationTests {
	static OrientGraphNoTx client=null;

	@Autowired
	GraphService gs;

	@BeforeEach
	public void beforeEach() throws Exception {
		if (client!=null) return;

		Field dao = gs.getClass().getDeclaredField("graphQueryDao");
		dao.setAccessible(true);
		DaoOrientDB obj = (DaoOrientDB) dao.get(gs);

		Field clientField = obj.getClass().getDeclaredField("client");
		clientField.setAccessible(true);
		client = (OrientGraphNoTx) clientField.get(obj);
	}

	@Test
	void contextLoads() {
		String className = "TestClass";
		gs.recordClassDeclaration(className,null,null,false,false);
		ClassNode cn = gs.getClassByName(className);
		assertEquals(cn.getName(), className);
	}

	@Test
	void polymorphicTest() {
		createPolyTestScenario();

		Map<String,MethodNode> polyNodes = gs.getPolymorphicNodes();
		polyNodes.forEach((key,value)->{
			Map<String, MethodNode> candidates = gs.getPolymorphicCandidatesForCall(value);
			if (candidates.size()<2) return;
			gs.createIntermediateCall(value,candidates.values());
		});

		OrientVertex vertex=null;
		Iterator<OrientVertex> iter = findVertex("SELECT FROM Intermediate");
		while (iter.hasNext()) {
			vertex = iter.next();
		}

		Iterator<Edge> iterator = vertex.getEdges(Direction.OUT,"Invokes").iterator();
		int callees=0;
		while (iterator.hasNext()) {
			Edge edge = iterator.next();
			Vertex destination = edge.getVertex(Direction.IN);
			callees++;
		}
		assertEquals(2,callees);
	}

	@Test
	void rerouteToParentTest() {
		final String[] NULL = new String[] {};

		gs.recordClassDeclaration("C", null, new String[] {"IC1"}, false, false);
		gs.recordInterfaceDeclaration("IC1",new String[] {"IC2"}, false);
		gs.recordInterfaceDeclaration("IC2",NULL, false);
		gs.recordMethodDeclaration("IC2", "calleeMethod", "()V", false, false, true);

		gs.recordClassDeclaration("B", "C", new String[] {"IB1"}, false, false);
		gs.recordInterfaceDeclaration("IB1",new String[] {"IB2"}, false);
		gs.recordInterfaceDeclaration("IB2",NULL, false);

		gs.recordClassDeclaration("A", "B", new String[] {"IA1"}, false, false);
		gs.recordInterfaceDeclaration("IA1",new String[] {"IA2"}, false);
		gs.recordInterfaceDeclaration("IA2",NULL, false);

		gs.recordClassDeclaration("caller",null,NULL, false, false);
		gs.recordMethodDeclaration("caller", "callerMethod", "()V", false, false, false);
		gs.recordMethodCall("caller","callerMethod","()V","A","calleeMethod","()V",true);

		Iterator<OrientVertex> iterator2 = findVertex("SELECT FROM Method WHERE name='callerMethod' and class='caller'");
		assertTrue(iterator2.hasNext());
		MethodNode mn2 = DaoOrientDB.resultToMethodNode(iterator2.next());
		gs.getCallees(mn2).values().forEach(value->{
			MethodNode x=value;
		});
		try {
			Method m = GraphCreationManager.class.getDeclaredMethod("rerouteEdgesToParent");
			m.setAccessible(true);
			m.invoke(null);
		} catch (Exception e) {
			e.printStackTrace();
		}

		Iterator<OrientVertex> iterator = findVertex("SELECT FROM Method WHERE name='callerMethod' and class='caller'");
		assertTrue(iterator.hasNext());
		MethodNode mn = DaoOrientDB.resultToMethodNode(iterator.next());
		assertEquals(1,gs.getCallees(mn).size());

		gs.getCallees(mn).values().forEach(value->{
			assertEquals("IC2",value.getClassName());
			assertEquals("calleeMethod",value.getMethodName());
			assertEquals("()V",value.getDescriptor());
		});
	}

	@Test
	void controllerTraverseQueryTest() throws VertexNotFoundException {
		createPolyTestScenario();

		String id = gs.getClassByName("RootInterface").getId();

		String x=gs.traverseFromNode(id,"class",Direction.IN,3);
		int a=5;
	}

	private void createPolyTestScenario() {
		final String[] NULL = new String[] {};
		gs.recordInterfaceDeclaration("RootInterface",NULL,false);
		gs.recordMethodDeclaration("RootInterface", "calleeMethod", "(I)V",true, false, true);
		//
		gs.recordClassDeclaration("ParentClass", "null", new String[]{"RootInterface"}, false, false);
		gs.recordClassDeclaration("ChildClass", "ParentClass", NULL, false, false);
		gs.recordMethodDeclaration("ChildClass","calleeMethod","(I)V",false,false, true);
		gs.recordMethodDeclaration("ChildClass","<init>","()V",false,false, true);
		gs.recordClassInitialized("ChildClass");
		//

		//
		gs.recordInterfaceDeclaration("ParentInterface", new String[]{"RootInterface"},false);
		gs.recordMethodDeclaration("ParentInterface", "calleeMethod", "(I)V",false, false, true);

		gs.recordClassDeclaration("ImplementorClass", null, new String[]{"ParentInterface"}, false, false);
		gs.recordMethodDeclaration("ImplementorClass","<init>","()V", false, false, true);

		gs.recordClassDeclaration("ImplementorClassChild", "ImplementorClass", NULL, false, false);

		gs.recordClassDeclaration("CallerClass", null, NULL, false, false);
		gs.recordMethodDeclaration("CallerClass", "callerMethod","()V", false, false, true);
		gs.recordMethodCall("CallerClass", "callerMethod", "()V",
				"ImplementorClass", "<init>", "()V", false);
		gs.recordClassInitialized("ImplementorClass");
		gs.recordMethodCall("CallerClass", "callerMethod", "()V",
				"RootInterface", "calleeMethod", "(I)V", true);
		//

		gs.persistInitializedClasses(gs.getInitializedClasses());
	}

	private Iterator<OrientVertex> findVertex(String query) {
		return ((Iterable <OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();
	}
}
