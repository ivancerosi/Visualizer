package algebra.diplomski.ivan.controller.dao;

import algebra.diplomski.ivan.controller.dao.dto.LinkDto;
import algebra.diplomski.ivan.controller.dao.dto.NodeDto;
import algebra.diplomski.ivan.controller.domain.MethodNode;
import algebra.diplomski.ivan.controller.domain.ClassNode;
import algebra.diplomski.ivan.controller.domain.Node;
import algebra.diplomski.ivan.controller.exceptions.VertexNotFoundException;
import com.orientechnologies.common.util.OCallable;
import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.exception.OConcurrentModificationException;
import com.orientechnologies.orient.core.metadata.schema.OClass;
import com.orientechnologies.orient.core.metadata.schema.OProperty;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.storage.ORecordDuplicatedException;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Parameter;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;
import com.tinkerpop.blueprints.impls.orient.OrientVertex;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class DaoOrientDB implements DeclarationDao, ComplexityDao, ClassInitializationDao, MethodCallDao, GraphQueryDao {
    static ReentrantLock schemaInitLock = new ReentrantLock();
    private static boolean schemaInitialized=false;

    private final static int MAX_RETRIES=15;

    OrientGraphNoTx client;
    private static final Logger LOGGER =
            LoggerFactory.getLogger("analytics");
    private static final Logger EXCEPTIONS_LOGGER =
            LoggerFactory.getLogger("exceptions");
    private static final Logger DUPLICATES_LOGGER =
            LoggerFactory.getLogger("duplicates");

    public DaoOrientDB(OrientGraphFactory factory) {
        schemaInitLock.lock();
        client = factory.getNoTx();

        if (!schemaInitialized) {
            initializeSchema();
            schemaInitialized = true;
        }
        schemaInitLock.unlock();

    }

    public void close() {
        client.shutdown();
    }


    @Override
    public void recordClassDeclaration(String className, String parent, String[] interfaces, boolean isAbstract,
                                       boolean isSynthetic) {
        LOGGER.info(String.format("recordClassDeclaration:%s, %s",className,parent));
        OrientVertex parentV = null; int attempts=0;
        for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
            try {
                parentV = null;
                if (parent != null && parent.length() > 0 && !parent.equals("null")) {
                    int subattempts=0;
                    for (subattempts=0; subattempts<MAX_RETRIES; subattempts++) {
                        try {
                            OrientVertex parentClass = getClass(parent);
                            if (parentClass!=null) parentV = (OrientVertex) parentClass;
                            else {
                                parentV = client.addVertex("class:Class");
                                parentV.setProperty("name", parent);
                                parentV.setProperty("parsed", false);
                                parentV.setProperty("initialized",false);
                                parentV.save();
                            }
                            break;
                        } catch (ORecordDuplicatedException e) { EXCEPTIONS_LOGGER.error(e.toString());}
                    }
                    if (subattempts==MAX_RETRIES) {
                        System.out.println("Fail");
                    }
                }
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail");
        }

        OrientVertex classV = null; boolean loggedWarning=false;
        for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
            try {
                OrientVertex optionalClass = getClass(className);
                classV = null;
                if (optionalClass!=null) {
                    if (!loggedWarning) LOGGER.warn("recordClassDeclaration({}) found existing class of the same name", className);
                    classV = optionalClass;
                    loggedWarning=true;
                } else classV = client.addVertex("class:Class");
                classV.setProperty("parsed", true);
                classV.setProperty("abstract", isAbstract);
                classV.setProperty("name", className);
                classV.setProperty("synthetic",isSynthetic);
                classV.setProperty("interface",false);
                classV.setProperty("initialized",false);
                classV.save();
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
            catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());}
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail");
        }
        for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
            try {
                if (parentV != null) {
                    String command = String.format("CREATE EDGE Extends FROM %s TO %s", classV.getIdentity().toString(), parentV.getIdentity().toString());
                    client.command(new OCommandSQL(command)).execute();
                }
                break;
            } catch (OConcurrentModificationException e) {
                EXCEPTIONS_LOGGER.error(e.toString());
                classV.reload();
                parentV.reload();
            } catch (ORecordDuplicatedException e) {
                DUPLICATES_LOGGER.error(e.toString());
                DUPLICATES_LOGGER.error(String.format("CLASS/ID FROM: %s/%s",classV.getProperty("name"),classV.getIdentity().toString()));
                DUPLICATES_LOGGER.error(String.format("CLASS/ID TO: %s/%s",parentV.getProperty("name"),parentV.getIdentity().toString()));
            }
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail");
        }

        if (interfaces != null) {
            OrientVertex interfaceV = null;
            for (int x = 0; x < interfaces.length; x++) {
                for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
                    try {
                        int subattempts=0;
                        for (subattempts=0; subattempts<MAX_RETRIES; subattempts++) {
                            try {
                                OrientVertex optionalInterface = getInterface(interfaces[x]);
                                if (optionalInterface==null) {
                                    interfaceV = client.addVertex("class:Class");
                                    interfaceV.setProperty("interface",true);
                                    interfaceV.setProperty("name", interfaces[x]);
                                    interfaceV.setProperty("parsed", false);
                                    interfaceV.setProperty("abstract",true);
                                    interfaceV.setProperty("initialized",false);
                                    interfaceV.setProperty("synthetic",false);
                                    interfaceV.save();
                                } else interfaceV = optionalInterface;
                                break;
                            } catch(ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());}
                        }
                        if (subattempts==MAX_RETRIES) {
                            System.out.println("Fail");
                        }

                        String command = String.format("CREATE EDGE Implements FROM %s TO %s",
                                classV.getIdentity().toString(), interfaceV.getIdentity().toString());
                        client.command(new OCommandSQL(command)).execute();
                        break;
                    } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
                }
                if (attempts==MAX_RETRIES) {
                    System.out.println("Fail");
                }
            }
        }
    }

    @Override
    public void recordInterfaceDeclaration(String childInterface, String[] interfaces, boolean isSynthetic) {
        client.getVertices().forEach(value->{
            if (value.getProperty("name")==null) {
                Object x = value;
                EXCEPTIONS_LOGGER.error("Couldn't find name property for id:%s class:%s",
                        value.getProperty("@rid"),
                        value.getProperty("@class"));
            }
        });
        OrientVertex childInterfaceV = null;
        int attempts=0;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                OrientVertex optionalChild = getInterface(childInterface);
                childInterfaceV = null;

                if (optionalChild==null) {
                    childInterfaceV = client.addVertex("class:Class");
                    childInterfaceV.setProperty("name", childInterface);
                    childInterfaceV.setProperty("parsed", true);
                    childInterfaceV.setProperty("synthetic",isSynthetic);
                    childInterfaceV.setProperty("interface",true);
                    childInterfaceV.setProperty("abstract",true);
                    childInterfaceV.setProperty("initialized",false);

                    childInterfaceV.save();
                } else {
                    childInterfaceV = optionalChild;
                    childInterfaceV.setProperty("parsed",true);
                    childInterfaceV.save();
                }
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
            catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());}
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail");
        }

        if (interfaces == null || interfaces.length == 0) return;
        for (String _interface : interfaces) {
            if (_interface==null || _interface.length()==0) continue;

            OrientVertex parentInterfaceV = null;
            for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
                try {
                    OrientVertex optionalParent = getInterface(_interface);
                    parentInterfaceV = null;

                    if (optionalParent==null) {
                        parentInterfaceV = client.addVertex("class:Class");
                        parentInterfaceV.setProperty("name", _interface);
                        parentInterfaceV.setProperty("parsed", false);
                        parentInterfaceV.setProperty("synthetic", false);
                        parentInterfaceV.setProperty("abstract", true);
                        parentInterfaceV.setProperty("initialized", false);
                        parentInterfaceV.setProperty("interface", true);
                        parentInterfaceV.save();
                    } else parentInterfaceV = optionalParent;
                    break;
                } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());
                } catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());
                }
            }
            if (attempts == MAX_RETRIES) {
                System.out.println("Fail");
            }

            for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
                try {
                    Iterator<Edge> iterator = childInterfaceV.getEdges(Direction.OUT).iterator();

                    while (iterator.hasNext()) {
                        Edge edge = iterator.next();
                        Vertex to = edge.getVertex(Direction.IN);
                        if (to.getProperty("name").toString().equals(_interface)) {
                            return;
                        }
                    }

                    String command = String.format("CREATE EDGE Extends FROM %s TO %s", childInterfaceV.getIdentity().toString(),
                            parentInterfaceV.getIdentity().toString());
                    client.command(new OCommandSQL(command)).execute();
                    break;
                } catch (OConcurrentModificationException e) {
                    EXCEPTIONS_LOGGER.error(e.toString());
                    parentInterfaceV.reload();
                    childInterfaceV.reload();
                } catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());
                }
                if (attempts == MAX_RETRIES) {
                    System.out.println("Fail");
                }
            }
        }
    }

    @Override
    public void recordMethodDeclaration(String className, String methodName, String descriptor, boolean isAbstract, boolean isSynthetic, boolean isPolymorphic) {
        LOGGER.info(String.format("recordMethodDeclaration %s %s %s", className, methodName, descriptor));
        OrientVertex method=null;
        int attempts;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                OrientVertex optionalMethod = getMethod(className, methodName, descriptor);
                method = null;
                if (optionalMethod!=null) {
                    method = optionalMethod;
                    LOGGER.warn(String.format("recordMethodDeclaration [%s, %s, %s] found existing method vertex", className, methodName, descriptor));
                } else {
                    method = client.addVertex("class:Method");
                }
                method.setProperty("poly",isPolymorphic);
                method.setProperty("abstract", isAbstract);
                method.setProperty("synthetic", isSynthetic);
                method.setProperty("descriptor", descriptor);
                method.setProperty("name", methodName);
                method.setProperty("qualifiedName",methodName+descriptor);
                method.setProperty("class", className);
                method.setProperty("parsed",true);
                method.setProperty("cyclomaticComplexity",0);
                method.setProperty("instructionComplexity",0);
                method.save();
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
            catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());
            }
        }
        if (attempts==MAX_RETRIES){
            System.out.println("Fail");
        }
        OrientVertex clazz=null;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                OrientVertex optionalClass = getClass(className);
                clazz = null;
                if (optionalClass==null) {
                    LOGGER.error("recordMethodDeclaration could not find class vertex {}", className);
                    clazz = client.addVertex("class:Class");
                    clazz.setProperty("parsed", false);
                    clazz.setProperty("name", className);
                    clazz.setProperty("initialized",false);
                    clazz.save();
                } else clazz = optionalClass;

                String command = String.format("CREATE EDGE ContainsMethod FROM %s TO %s",
                        clazz.getIdentity().toString(), method.getIdentity().toString());
                client.command(new OCommandSQL(command)).execute();
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail");
        }
    }

    @Override
    public void recordInstructionComplexity(String className, String methodName, String descriptor, int complexity) {
        int attempts=0;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                String query = String.format("SELECT FROM Method WHERE class = \"%s\" AND name = \"%s\" AND descriptor = \"%s\""
                        , className, methodName, descriptor);
                Iterator<OrientVertex> rs = ((Iterable<OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();
                if (rs.hasNext()) {
                    OrientVertex result = rs.next();
                    if (rs.hasNext())
                        LOGGER.error("recordInstructionComplexity found multiple methods with key {}  {}  {}",
                                className, methodName, descriptor);
                    result.setProperty("instructionComplexity", complexity);
                    result.save();

                } else {
                    LOGGER.error(String.format("recordInstructionComplexity failed to find method [%s, %s, %s]", className, methodName, descriptor));
                    return;
                }
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
            catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());}
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail");
        }
    }

    @Override
    public void recordCyclomaticComplexity(String className, String methodName, String descriptor, int complexity) {
        int attempts=0;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                String query = String.format("SELECT FROM Method WHERE class = \"%s\" AND name = \"%s\" AND descriptor = \"%s\"",
                        className, methodName, descriptor);
                Iterator<OrientVertex> rs = ((Iterable<OrientVertex>)client.command(new OCommandSQL(query)).execute()).iterator();
                if (rs.hasNext()) {
                    OrientVertex result = rs.next();
                    if (rs.hasNext())
                        LOGGER.error(String.format("recordCyclomaticComplexity found multiple methods with key [%s, %s, %s]",
                                className, methodName, descriptor));
                    result.setProperty("cyclomaticComplexity", complexity);
                    result.save();
                } else
                    LOGGER.error(String.format("recordCyclomaticComplexity failed to find method %s, %s, %s", className, methodName, descriptor));
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail");
        }
    }

    @Override
    public void recordClassInitialized(String className) {
        int attempts=0;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                String command = String.format("UPDATE Class SET initialized=TRUE where name='%s'", className);
                client.command(new OCommandSQL(command)).execute();
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());}
            break;
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Failed");
        }
    }

    @Override
    public Set<String> getInitializedClasses() {
        String query = "SELECT FROM Class WHERE initialized=TRUE";
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();

        Set<String> results = new HashSet<>();
        while (rs.hasNext()) {
            results.add(rs.next().getProperty("name"));
        }
        return results;
    }

    @Override
    public void recordMethodCall(String callerClass, String callerMethod, String callerDescriptor, String calleeClass, String calleeMethod, String calleeDescriptor, boolean polymorphic) {
        OrientVertex callerOptional = getMethod(callerClass, callerMethod, callerDescriptor);
        if (callerOptional==null) {
            LOGGER.error(String.format("recordMethodCall failed to find caller method %s, %s, %s", callerClass, callerMethod, callerDescriptor));
            return;
        }
        OrientVertex caller = callerOptional;

        OrientVertex calleeOptional;
        OrientVertex callee = null;

        int attempts = 0;
        for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
            try {
                calleeOptional = getMethod(calleeClass, calleeMethod, calleeDescriptor);
                if (calleeOptional!=null) {
                    callee = calleeOptional;
                    if ((boolean)callee.getProperty("poly")!=polymorphic) {
                        callee.setProperty("poly",polymorphic);
                        callee.save();
                    }
                }
                else {
                    callee = client.addVertex("class:Method");
                    callee.setProperty("class", calleeClass);
                    callee.setProperty("name", calleeMethod);
                    callee.setProperty("descriptor", calleeDescriptor);
                    callee.setProperty("qualifiedName",calleeMethod+calleeDescriptor);
                    callee.setProperty("parsed", false);
                    callee.setProperty("poly",polymorphic);
                    callee.setProperty("cyclomaticComplexity",0);
                    callee.setProperty("instructionComplexity",0);
                    callee.save();
                }
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());
            } catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());
            }
        }
        if (attempts == MAX_RETRIES) System.out.println("Fail");

        for (attempts = 0; attempts < MAX_RETRIES; attempts++) {
            try {
                String statement = String.format("CREATE EDGE Invokes FROM %s TO %s SET poly=%s",caller.getIdentity().toString(),callee.getIdentity().toString(), polymorphic);
                client.command(new OCommandSQL(statement)).execute();
                break;
            } catch (ORecordDuplicatedException e) {EXCEPTIONS_LOGGER.error(e.toString());
                break;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());
                callee.reload();
                caller.reload();
            }
        }
        if (attempts==MAX_RETRIES) {
            System.out.println("Fail method call edge");
        }
    }

    @Override
    public void transferCallsToOtherMethod(MethodNode source, MethodNode realTarget) {
        String incoming = String.format(
                "SELECT Invokes WHERE in=%s",realTarget.getId());
        String transfer = String.format(
                "UPDATE EDGE Invokes SET in = %s WHERE in = %s",realTarget.getId(), source.getId()
        );
        try {
            Object response = client.command(new OCommandSQL(transfer)).execute();
            client.commit();
        } catch (ORecordDuplicatedException e) {
            DUPLICATES_LOGGER.error(e.toString());
        }
    }

    @Override
    public void createIntermediateCall(MethodNode callee, Collection<MethodNode> candidates) {
        MethodNode intermediate = addIntermediateNode(callee);
        transferCallsToOtherMethod(callee,intermediate);

        candidates.forEach(candidate->{
           addCaller(intermediate,candidate);
        });
    }

    @Override
    public Map<String, MethodNode> getPolymorphicCandidatesForCall(MethodNode callee) {
        Map<String, MethodNode> result = new HashMap<>();

        // sve subklase metode koja sadrži callee
        String subclasses = String.format("SELECT FROM (TRAVERSE in('ContainsMethod'), in('Extends'), in('Implements') FROM %s) SKIP 1", callee.getId());
        String initCandidates  = String.format("SELECT expand(out('ContainsMethod')[qualifiedName=\"%s\"]) FROM (%s) WHERE initialized=TRUE", callee.getQualifiedName(), subclasses);

        Iterator<OrientVertex> rs2 = ((Iterable<OrientVertex>) client.command(new OCommandSQL(subclasses)).execute()).iterator();
        List<ClassNode> list = new ArrayList<>(10);
        while (rs2.hasNext()) {
            ClassNode mn = resultToClassNode(rs2.next());
            list.add(mn);
        }

        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>) client.command(new OCommandSQL(initCandidates)).execute()).iterator();
        while (rs.hasNext()) {
            MethodNode mn = resultToMethodNode(rs.next());
            if (mn!=null) result.put(mn.getId(),mn);
        }

        // sve subklase koje nadredjuju callee i nisu instancirane
        String uninitCandidates  = String.format("SELECT expand(out('ContainsMethod')) FROM (%s) WHERE out('ContainsMethod').qualifiedName='%s' AND out('ContainsMethod').abstract=FALSE AND initialized=FALSE", subclasses, callee.getQualifiedName());
        rs = ((Iterable<OrientVertex>) client.command(new OCommandSQL(uninitCandidates)).execute()).iterator();
        while (rs.hasNext()) {
            // mn je metoda u neinstanciranoj klasi
            MethodNode mn = resultToMethodNode(rs.next());
            String condition = String.format("@class='Method' OR name='%s' OR NOT (out('ContainsMethod').qualifiedName='%s')",mn.getClassName(),mn.getName()+mn.getDescriptor());
            //
            String subc = String.format("TRAVERSE in('Extends'), in('Implements'), in('ContainsMethod') FROM %s WHILE %s", mn.getId(), condition);
            String checkCandidate = String.format("SELECT $subc[initialized=true].size() as size LET $subc=(%s)",subc);

            OrientVertex size = ((Iterable<OrientVertex>) client.command(new OCommandSQL(checkCandidate)).execute()).iterator().next();

            if (size.getProperty("size") instanceof String) {
                if (Integer.parseInt(size.getProperty("size"))>0) result.put(mn.getId(), mn);
            } else {
                if ((Integer)size.getProperty("size")>0) result.put(mn.getId(),mn);
            }
        }


        return result;
    }

    @Override
    public MethodNode getRealMethodLocationInInterface(MethodNode method) {
        String parents = String.format(
                "TRAVERSE out('Extends') FROM (SELECT FROM Class WHERE name='%s')",
                method.getClassName()
        );
        String interfacesStart = String.format(
                "SELECT expand(out('Implements')) as implements FROM (%s)",
                parents
        );
        String interfacesAll = String.format(
                "TRAVERSE out('Extends') FROM (%s) STRATEGY BREADTH_FIRST",
                interfacesStart
        );
        String location = String.format(
                "SELECT FROM (%s) WHERE out('ContainsMethod').name='%s' AND out('ContainsMethod').descriptor='%s' LIMIT 1",
                interfacesAll, method.getMethodName(), method.getDescriptor()
        );
        String targetMethod = String.format(
                "SELECT expand(out('ContainsMethod')) FROM (%s) WHERE out('ContainsMethod').name='%s' AND out('ContainsMethod').descriptor='%s'",
                location, method.getMethodName(), method.getDescriptor()
        );

        MethodNode realLocation=null;
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>) client.command(new OCommandSQL(targetMethod)).execute()).iterator();
        if (rs.hasNext()) {
            realLocation = resultToMethodNode(rs.next());
        }
        return realLocation;
    }

    @Override
    public MethodNode getRealMethodLocationInParent(MethodNode method) {
        String condition =
                String.format("(@class='Method' AND name='%s' AND descriptor='%s' AND class<>'%s') OR (@class='Class')",method.getName(), method.getDescriptor(),method.getClassName());

        String traverse =
                String.format("TRAVERSE out('Extends'), out('ContainsMethod') FROM (SELECT FROM Class WHERE name='%s') WHILE (%s) STRATEGY BREADTH_FIRST",method.getClassName(),condition);

        String select =
                String.format("SELECT FROM (%s) WHERE @class='Method'", traverse);

        MethodNode realLocation=null;
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>)client.command(new OCommandSQL(select)).execute()).iterator();
        if (rs.hasNext()) {
            realLocation = resultToMethodNode(rs.next());
        }
        return realLocation;
    }

    @Override
    public Map<String,MethodNode> getMethodsDeclaredOnlyInParent() {
        //MATCH {class:Class, as:results, where: (@rid in (select @rid from (traverse outE('Extends'), outE('Implements'), inV('Class') from (select from Class where name like '%TestInstance')) where @class='Class'))}.out('ContainsMethod') {as: metode, where: (name='hello')} return metode.name


        // gets a list of methods which are not parsed, but their class is parsed
        String unparsedMethods = "SELECT class FROM Method WHERE parsed=false";
        String unparsedMethodClasses = String.format("SELECT name FROM Class WHERE name IN (%s) AND parsed=true",unparsedMethods);
        String query = String.format("SELECT FROM Method WHERE class IN (%s) AND parsed=false",
                unparsedMethodClasses);

        Iterator<OrientVertex> resultSet = ((Iterable<OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();
        Map<String,MethodNode> results = new HashMap<>();

        while (resultSet.hasNext()) {
            MethodNode result = resultToMethodNode(resultSet.next());
            if (result != null) results.put(result.getId(),result);
        }

        return results;
    }

    @Override
    public Set<MethodNode> getImplementorsForInterface(String interfaceName) {
        // get children interfaces
        OrientVertex parentOptional = getInterface(interfaceName);
        if (parentOptional==null) {
            LOGGER.error(String.format("getImplementorsForInterface {} called but couldn't find parent vertex", interfaceName));
            return null;
        }
        OrientVertex parent = parentOptional;
        parent.getEdges(Direction.IN,"Extends");

        String query = String.format("SELECT FROM (TRAVERSE OUT(\"Interface\") FROM Interface WHERE name = %s",interfaceName);

        return null;
    }

    @Override
    public String getMethodsByClass(String classId) {
        JSONArray result = new JSONArray();
        String query = String.format("SELECT expand(out('ContainsMethod')) FROM %s",classId);
        Iterator<Vertex> iterator = ((Iterable<Vertex>) client.command(new OCommandSQL(query)).execute()).iterator();
        while (iterator.hasNext()) {
            Vertex vertex = iterator.next();
            if (vertex.getId().toString().contains("#-")) continue;
            JSONObject obj = new JSONObject();

            obj.put("id", vertex.getId().toString().substring(1));
            obj.put("name", vertex.getProperty("name").toString());
            obj.put("descriptor", vertex.getProperty("descriptor").toString());

            result.put(obj);
        }
        return result.toString();
    }

    @Override
    public String getMethodList(String substring) {
        JSONArray result=new JSONArray();
        String query;
        if (substring==null || substring.length()==0) {
            query= String.format("SELECT FROM Method LIMIT 20");
        } else {
            query = "SELECT FROM Method WHERE name LIKE '%"+substring+"%' LIMIT 20";
        }
        Iterator<Vertex> iterator = ((Iterable<Vertex>)client.command(new OCommandSQL(query)).execute()).iterator();
        while (iterator.hasNext()) {
            Vertex vertex = iterator.next();
            JSONObject obj = new JSONObject();
            obj.put("id",vertex.getId().toString().substring(1));
            obj.put("name",vertex.getProperty("name").toString());
            obj.put("descriptor",vertex.getProperty("descriptor").toString());
            result.put(obj);
        }
        return result.toString();
    }

    @Override
    public String getClassList(String substring) {
        JSONArray result=new JSONArray();
        String query;
        if (substring==null || substring.length()==0) {
            query= String.format("SELECT FROM Class LIMIT 20");
        } else {
            query = "SELECT FROM Class WHERE name LIKE '%"+substring+"%' LIMIT 20";
        }
        Iterator<Vertex> iterator = ((Iterable<Vertex>)client.command(new OCommandSQL(query)).execute()).iterator();
        while (iterator.hasNext()) {
            Vertex vertex = iterator.next();
            JSONObject obj = new JSONObject();
            obj.put("id",vertex.getId().toString().substring(1));
            obj.put("name",vertex.getProperty("name").toString());
            result.put(obj);
        }
        return result.toString();
    }

    @Override
    public void rollUpMethodRelations() {
        Iterator<Vertex> iterator = client.getVerticesOfClass("Class").iterator();
        while (iterator.hasNext()) {
            Vertex vertex = iterator.next();

            String command2= String.format(
                    "SELECT expand(out('ContainsMethod').out('Invokes').in('ContainsMethod')) FROM %s"
                    ,vertex.getId().toString());
            Iterator<Vertex> results=((Iterable<Vertex>) client.command(new OCommandSQL(command2)).execute()).iterator();
            HashSet<String> set = new HashSet<>(5);
            while (results.hasNext()) {
                Vertex target = results.next();
                if (vertex.getId().equals(target.getId())) continue;
                if (target.getProperty("name")==null) continue;
                if (set.contains(target.getId().toString())) continue;

                set.add(target.getId().toString());
                vertex.addEdge("DependsOn",target);
            }

/*
            String command2 = String.format(
                    "SELECT EXPAND(*) FROM (SELECT DISTINCT @rid FROM (SELECT expand(out('ContainsMethod').out('Invokes').in('ContainsMethod')) FROM %s)) WHERE @rid<>%s"
            ,vertex.getId(),vertex.getId());
            Iterator<Vertex> results2=((Iterable<Vertex>) client.command(new OCommandSQL(command)).execute()).iterator();
            while (results2.hasNext()) {
                Vertex target = results2.next();
                if (target.getId().toString().contains("#-")) continue;
                vertex.addEdge("DependsOn",target);

            }*/
        }
    }

    @Override
    public String traverseFromNode(String id, String resolution, Direction dir, int limit) throws VertexNotFoundException {
        OrientVertex anchor=null;
        JSONObject object = new JSONObject();

        Set<NodeDto> nodes = new HashSet<>();
        Set<LinkDto> links = new HashSet<>();

        try {
            anchor = client.getVertex(id);
        } catch (Exception e) {
            EXCEPTIONS_LOGGER.error(e.toString());
        }
        if (anchor==null || !anchor.getProperty("@class").toString().toLowerCase().equals(resolution)) {
            throw new VertexNotFoundException("Couldn't find a vertex");
        }
        Direction opposite=dir==Direction.IN?Direction.OUT:Direction.IN;

        int placedCount=1;
        nodes.add(new NodeDto(anchor));

        String[] labels;
        Queue<Edge> output = new ArrayDeque<>(10);
        Queue<Edge> input = new ArrayDeque<>(10);
        if (resolution.equals("class")) {
            labels = new String[] {"Implements","Extends","DependsOn"};
            Iterator<Edge> edgeIterator = anchor.getEdges(dir,labels).iterator();
            while (edgeIterator.hasNext()) {
                output.add(edgeIterator.next());
            }
        } else {
            labels = new String[] {"Invokes"};
            if (anchor.getProperty("@class").toString().equals("Class")) {
                Iterator<Edge> edgeIterator = anchor.getEdges(dir,"ContainsMethod").iterator();
                while (edgeIterator.hasNext()) {
                    input.add(edgeIterator.next());
                }
                while (!input.isEmpty() && placedCount<limit) {
                    Edge edge = input.remove();
                    Vertex vertex = edge.getVertex(opposite);
                    nodes.add(new NodeDto(vertex));
                    links.add(new LinkDto(edge));
                    placedCount++;
                    Iterator<Edge> edgeIterator2 = vertex.getEdges(dir,labels).iterator();
                    while (edgeIterator2.hasNext()) {
                        output.add(edgeIterator.next());
                    }
                }
            } else {
                Iterator<Edge> edgeIterator = anchor.getEdges(dir, labels).iterator();
                while (edgeIterator.hasNext()) {
                    output.add(edgeIterator.next());
                }
            }
        }
        assert(input.isEmpty());
        traverseNextBreadth(resolution,dir,labels,nodes,links,placedCount,limit,output,input);

        JSONArray jsonLinks = new JSONArray();
        JSONArray jsonNodes = new JSONArray();

        Iterator<NodeDto> iteratorN = nodes.iterator();
        while (iteratorN.hasNext()) {
            jsonNodes.put(iteratorN.next().toJson());
        }
        Iterator<LinkDto> iteratorL = links.iterator();
        while (iteratorL.hasNext()) {
            jsonLinks.put(iteratorL.next().toJson());
        }

        object.put("nodes",jsonNodes);
        object.put("links",jsonLinks);

        return object.toString();
    }

    public void traverseNextBreadth(String resolution, Direction dir, String[] labels, Set<NodeDto> nodes, Set<LinkDto> links, int count,int limit,Queue<Edge>input,Queue<Edge> output) {
        Direction opposite=dir==Direction.IN?Direction.OUT:Direction.IN;

        while (!input.isEmpty() && count < limit) {
            Edge edge = input.remove();
            Vertex vertex = edge.getVertex(opposite);

            if ("method".equals(resolution) && !vertex.getEdges(Direction.IN,"ContainsMethod").iterator().hasNext()) {
                continue;
            }

            links.add(new LinkDto(edge));
            if (!nodes.contains(vertex)) {
                nodes.add(new NodeDto(vertex));
                count++;

                Iterator<Edge> edgeIterator = vertex.getEdges(dir, labels).iterator();
                while (edgeIterator.hasNext()) output.add(edgeIterator.next());
            }
        }
        if (count>=limit || output.isEmpty()) return;
        assert (input.isEmpty());
        traverseNextBreadth(resolution, dir, labels, nodes, links, count, limit, output, input);
    }


    private OrientVertex getMethod(String className, String name, String descriptor) {
        String query = String.format("SELECT FROM Method WHERE class = \"%s\" AND name = \"%s\" AND descriptor = \"%s\"",className,name,descriptor);
        Iterator<OrientVertex> rs  = ((Iterable<OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();
        if (rs.hasNext()) {
            OrientVertex res = rs.next();
            return res;
        } else return null;
    }

    private OrientVertex getClass(String name) {
        String query = String.format("SELECT FROM Class WHERE name = '%s'",name);
        Iterator<OrientVertex> rs  = ((Iterable<OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();
        if (rs.hasNext()) {
            OrientVertex res = rs.next();
            return res;
        } else return null;
    }

    private OrientVertex getInterface(String name) {
        String query = String.format("SELECT * FROM Class WHERE name = \"%s\" AND interface=true",name);
        Iterator<OrientVertex> rs  = ((Iterable<OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();

        if (rs.hasNext()) {
            OrientVertex res = rs.next();
            return res;
        } else return null;
    }

    private OProperty createProperty(OClass oclass, String name, OType type) {
        OProperty prop;
        if ((prop=oclass.getProperty(name))==null) {
            return oclass.createProperty(name, type);
        } else return prop;
    }
    private OClass createClass(String name) {
        OClass oclass = client.getVertexType(name);
        if (oclass==null) {
            oclass = client.createVertexType(name);
        }
        return oclass;
    }


    public void initializeSchema() {
        if (client.getVertexType("Class")==null) {
            OClass classNode = createClass("Class");
            createProperty(classNode, "name", OType.STRING);
            client.createKeyIndex("name", Vertex.class,new Parameter("type","UNIQUE"), new Parameter("class","Class"));
            createProperty(classNode, "interface", OType.BOOLEAN);
            createProperty(classNode, "abstract", OType.BOOLEAN);
            createProperty(classNode, "parsed", OType.BOOLEAN);
            createProperty(classNode, "initialized", OType.BOOLEAN);
            createProperty(classNode, "synthetic", OType.BOOLEAN);
        }


        if (client.getVertexType("Method")==null) {
            OClass methodNode = createClass("Method");
            createProperty(methodNode, "name", OType.STRING);
            createProperty(methodNode, "descriptor", OType.STRING);
            createProperty(methodNode, "qualifiedName", OType.STRING);
            createProperty(methodNode, "class", OType.STRING);
            createProperty(methodNode, "abstract", OType.BOOLEAN);
            createProperty(methodNode, "synthetic", OType.BOOLEAN);
            createProperty(methodNode, "poly", OType.BOOLEAN);
            createProperty(methodNode, "parsed", OType.BOOLEAN);
            createProperty(methodNode, "instructionComplexity", OType.INTEGER);
            createProperty(methodNode, "cyclomaticComplexity", OType.INTEGER);
            client.command(new OCommandSQL("CREATE INDEX methodIndex on Method (class, name, descriptor) UNIQUE")).execute();
        }

        if (client.getVertexType("Intermediate")==null) {
            OClass intermediateNode = createClass("Intermediate");
            createProperty(intermediateNode, "name", OType.STRING);
            createProperty(intermediateNode, "descriptor", OType.STRING);
            createProperty(intermediateNode, "class", OType.STRING);

            createProperty(intermediateNode, "synthetic", OType.BOOLEAN);
            createProperty(intermediateNode, "parsed", OType.BOOLEAN);
            createProperty(intermediateNode, "abstract", OType.BOOLEAN);
            createProperty(intermediateNode, "qualifiedName", OType.STRING);

            client.command(new OCommandSQL("CREATE INDEX intermediateIndex on Intermediate (class, name, descriptor) UNIQUE")).execute();
        }
        OClass ccRel;
        if (client.getEdgeType("CCRelation")==null) {
            ccRel = client.createEdgeType("CCRelation");
            ccRel.createProperty("out",OType.LINK);
            ccRel.createProperty("in",OType.LINK);
            ccRel.createIndex("CCRelation.out_in", OClass.INDEX_TYPE.UNIQUE, "out","in");
        } else ccRel = client.getEdgeType("CCRelation");

        if (client.getEdgeType("Extends")==null) {
            OClass edge = client.createEdgeType("Extends","CCRelation");
            edge.createProperty("out",OType.LINK);
            edge.createProperty("in",OType.LINK);
            edge.createIndex("Extends.out_in", OClass.INDEX_TYPE.UNIQUE, "out","in");
        }
        if (client.getEdgeType("Implements")==null) {
            OClass edge = client.createEdgeType("Implements","CCRelation");
            edge.createProperty("out",OType.LINK);
            edge.createProperty("in",OType.LINK);
            edge.createIndex("Implements.out_in", OClass.INDEX_TYPE.UNIQUE, "out","in");
        }
        if (client.getEdgeType("DependsOn")==null) {
            OClass edge = client.createEdgeType("DependsOn");
            edge.createProperty("out",OType.LINK);
            edge.createProperty("in",OType.LINK);
            edge.createIndex("DependsOn.out_in", OClass.INDEX_TYPE.UNIQUE, "out","in");
        }
        if (client.getEdgeType("ContainsMethod")==null) {
            OClass edge = client.createEdgeType("ContainsMethod");
            edge.createProperty("out", OType.LINK);
            edge.createProperty("in", OType.LINK);
            edge.createIndex("ContainsMethod.out_in", OClass.INDEX_TYPE.UNIQUE, "out", "in");
        }

        if (client.getEdgeType("Invokes")==null) {
            OClass edge = client.createEdgeType("Invokes");
            OClass method = client.getVertexType("Method");
            edge.createProperty("out", OType.LINK);
            edge.createProperty("in", OType.LINK);
            edge.createProperty("poly", OType.BOOLEAN);
            edge.createIndex("Invokes.out_in", OClass.INDEX_TYPE.UNIQUE, "out", "in");
            edge.createIndex("Invokes.polymorphic", OClass.INDEX_TYPE.NOTUNIQUE, "poly");
        }
    }

    @Override
    public Map<String, MethodNode> getPolymorphicNodes() {
        String query = "SELECT FROM Method WHERE poly=TRUE AND in('ContainsMethod').size()>0";
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>)client.command(new OCommandSQL(query)).execute()).iterator();

        Map<String, MethodNode> polyMethods = new HashMap<>();

        while (rs.hasNext()) {
            OrientVertex result = rs.next();
            MethodNode method = resultToMethodNode(result);
            if (method!=null)
                polyMethods.put(method.getId(), method);
        }
        return polyMethods;
    }

    @Override
    public MethodNode addIntermediateNode(MethodNode forActualNode) {
        String query = String.format("SELECT FROM Intermediate WHERE class='%s' AND name='%s' AND descriptor='%s'",
                forActualNode.getClassName(), forActualNode.getMethodName(), forActualNode.getDescriptor());
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>)client.command(new OCommandSQL(query)).execute()).iterator();
        if (rs.hasNext()) return resultToMethodNode(rs.next());

        OrientVertex intermediateVertex = client.addVertex("class:Intermediate");
        intermediateVertex.setProperty("poly",true);
        intermediateVertex.setProperty("class",forActualNode.getClassName());
        intermediateVertex.setProperty("name",forActualNode.getMethodName());
        intermediateVertex.setProperty("descriptor",forActualNode.getDescriptor());
        intermediateVertex.setProperty("synthetic",forActualNode.isSynthetic());
        intermediateVertex.setProperty("parsed",false);
        intermediateVertex.setProperty("abstract",true);
        intermediateVertex.setProperty("qualifiedName", forActualNode.getQualifiedName());
        intermediateVertex.save();

        Iterator<OrientVertex> rs2 = ((Iterable<OrientVertex>)client.command(new OCommandSQL(query)).execute()).iterator();
        return resultToMethodNode(rs2.next());
    }

    @Override
    public boolean removeInvokesEdge(MethodNode source, MethodNode target) {
        String command = String.format("DELETE EDGE Invokes FROM %s TO %s",source.getId(),target.getId());
        client.command(new OCommandSQL(command)).execute();
        return true;
    }

    @Override
    public Map<String, MethodNode> getSyntheticNodes() {
        String query = "SELECT FROM Method WHERE synthetic=TRUE";
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>)client.command(new OCommandSQL(query)).execute()).iterator();

        Map<String, MethodNode> synthMethods = new HashMap<>();

        while (rs.hasNext()) {
            OrientVertex result = rs.next();
            MethodNode method = resultToMethodNode(result);
            if (method!=null)
                synthMethods.put(method.getId(), method);
        }
        return synthMethods;
    }

    @Override
    public Map<String, MethodNode> getCallers(MethodNode calledMethod) {
        Map<String, MethodNode> callers = new HashMap<>();
        String query = String.format("SELECT expand(in('Invokes')) FROM Method WHERE @rid=%s",calledMethod.getId());
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>)client.command(new OCommandSQL(query)).execute()).iterator();
        while (rs.hasNext()) {
            OrientVertex result = rs.next();
            MethodNode method = resultToMethodNode(result);
            callers.put(method.getId(), method);
        }
        return callers;
    }

    @Override
    public ClassNode getClassByName(String name) {
        String query = String.format("SELECT FROM Class WHERE name='%s'", name);
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>) client.command(new OCommandSQL(query)).execute()).iterator();
        if (rs.hasNext()) return resultToClassNode(rs.next());
        else return null;
    }

    @Override
    public Map<String, MethodNode> getCallees(MethodNode callerMethod) {
        Map<String, MethodNode> callees = new HashMap<>();
        String query = String.format("SELECT expand(out('Invokes')) FROM Method WHERE @rid=%s",callerMethod.getId());
        Iterator<OrientVertex> rs = ((Iterable<OrientVertex>)client.command(new OCommandSQL(query)).execute()).iterator();
        while (rs.hasNext()) {
            OrientVertex result = rs.next();
            MethodNode method = resultToMethodNode(result);
            callees.put(method.getId(), method);
        }
        return callees;
    }

    @Override
    public boolean addCallee(MethodNode target, MethodNode callee) {
        String query = String.format("CREATE EDGE Invokes FROM %s TO %s",target.getId(),callee.getId());
        int attempts;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                client.command(new OCommandSQL(query)).execute();
                return true;
            } catch (ORecordDuplicatedException e) {
                EXCEPTIONS_LOGGER.error(e.toString());
                LOGGER.warn("Edge {} - {} already exists",String.join(".",target.getClassName(),target.getMethodName()),
                        String.join(".",target.getClassName(),target.getMethodName()));
                return false;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());
            }
        }
        if (attempts==MAX_RETRIES) LOGGER.error("Failed to create Invokes edge between {} - {}",String.join(".",target.getClassName(),target.getMethodName()),
                String.join(".",target.getClassName(),target.getMethodName()));
        return false;
    }

    @Override
    public Node getVertexById(String id) {
        OrientVertex vertex = client.getVertex(id);
        String vertexType = vertex.getProperty("@class").toString();
        if (vertexType.equals("Class")) return resultToClassNode(vertex);
        if (vertexType.equals("Intermediate") ||
        vertexType.equals("Method")) {
            return resultToMethodNode(vertex);
        }
        EXCEPTIONS_LOGGER.error(String.format("Element with id %s of  is not Class, Intermediate nor Method",id,vertexType));
        return null;
    }

    @Override
    public boolean addCaller(MethodNode caller, MethodNode target) {
        String query = String.format("CREATE EDGE Invokes FROM %s TO %s",caller.getId(),target.getId());
        int attempts;
        for (attempts=0; attempts<MAX_RETRIES; attempts++) {
            try {
                client.command(new OCommandSQL(query)).execute();
                return true;
            } catch (ORecordDuplicatedException e) {
                EXCEPTIONS_LOGGER.error(e.toString());
                LOGGER.warn(String.format("Edge %s - %s already exists",String.join(".",caller.getClassName(),caller.getMethodName()),
                        String.join(".",target.getClassName(),target.getMethodName())));
                return false;
            } catch (OConcurrentModificationException e) {EXCEPTIONS_LOGGER.error(e.toString());
            }
        }
        if (attempts==MAX_RETRIES) LOGGER.error("Failed to create Invokes edge between {} - {}",String.join(".",caller.getClassName(),caller.getMethodName()),
                String.join(".",target.getClassName(),target.getMethodName()));
        return false;
    }

    @Override
    public boolean removeMethodNode(MethodNode target) {
        String command = String.format("DELETE EDGE Invokes WHERE out=%s or in=%s", target.getId(), target.getId());
        client.command(new OCommandSQL(command)).execute();
        command = String.format("DELETE VERTEX Method WHERE @rid=%s", target.getId());
        client.command(new OCommandSQL(command)).execute();
        return true;
    }


    public static MethodNode resultToMethodNode(OrientVertex result) {
        if (result==null) return null;
        OrientVertex vertex = result;

        MethodNode method = new MethodNode(vertex.getIdentity().toString(),
                vertex.getProperty("class").toString(),
                vertex.getProperty("name").toString(),
                vertex.getProperty("descriptor").toString());

        method.setIsPolymorphic(vertex.getProperty("poly"));
        method.setQualifiedName(method.getMethodName()+method.getDescriptor());

        method.setIsParsed(vertex.getProperty("parsed"));
        if (method.isParsed()) {
            method.setIsSynthetic(vertex.getProperty("synthetic"));
            method.setIsAbstract(vertex.getProperty("abstract"));
            method.setCyclomaticComplexity(vertex.getProperty("cyclomaticComplexity"));
            method.setInstructionComplexity(vertex.getProperty("instructionComplexity"));
        }

        return method;
    }

    public Object execute(String command) {
        return client.command(new OCommandSQL(command)).execute();
    }

    private void cleanDatabase() {
        String query;
        if (client.getEdgeType("Extends")!=null) {
            query = "DELETE EDGE Extends";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS Extends";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getEdgeType("DependsOn")!=null) {
            query = "DELETE EDGE DependsOn";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS DependsOn";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getEdgeType("Invokes")!=null) {
            query = "DELETE EDGE Invokes";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS Invokes";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getEdgeType("Implements")!=null) {
            query = "DELETE EDGE Implements";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS Implements";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getEdgeType("CCRelation")!=null) {
            query = "DELETE EDGE CCRelation";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS CCRelation";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getEdgeType("ContainsMethod")!=null) {
            query = "DELETE EDGE ContainsMethod";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS ContainsMethod";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getVertexType("Method")!=null) {
            query = "DELETE VERTEX Method";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS Method";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getVertexType("Class")!=null) {
            query = "DELETE VERTEX Class";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS Class";
            client.command(new OCommandSQL(query)).execute();
        }

        if (client.getVertexType("Intermediate")!=null) {
            query = "DELETE VERTEX Intermediate";
            client.command(new OCommandSQL(query)).execute();
            query = "DROP CLASS Intermediate";
            client.command(new OCommandSQL(query)).execute();
        }
    }

    public static ClassNode resultToClassNode(OrientVertex result) {
        if (result==null) return null;

        ClassNode classNode = new ClassNode(result.getIdentity().toString(),
                result.getProperty("name"),
                result.getProperty("interface"),
                result.getProperty("abstract"),
                result.getProperty("synthetic"));
        classNode.setIsInstantiated(result.getProperty("initialized"));
        return classNode;
    }

}
