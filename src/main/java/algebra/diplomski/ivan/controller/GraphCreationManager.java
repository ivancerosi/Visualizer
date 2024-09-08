package algebra.diplomski.ivan.controller;

import algebra.diplomski.ivan.controller.domain.ClassNode;
import algebra.diplomski.ivan.controller.domain.MethodNode;
import algebra.diplomski.ivan.controller.service.GraphService;
import algebra.diplomski.ivan.controller.visitors.CallGraphClassVisitor;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class GraphCreationManager {
    private static OrientGraphFactory factory;

    private static GraphService gs;

    private static byte[] BUFFER = new byte[65536];
    private static int OFFSET=0;

    private static boolean isAnalyzingSut=false;
    private static synchronized boolean toggleAnalyzingSUTFlag() {
        boolean isAnalyzing=isAnalyzingSut;
        isAnalyzingSut=true;
        return isAnalyzing;
    }

    public static enum ANALYSIS_STATUS {
        IDLE, PARSING_FILES, PROCESSING_GRAPH, FUZZING
    }

    public static enum ANALYSIS_OUTCOME {
        SUCCESS, ERROR, NONE
    }


    private static ANALYSIS_STATUS status = ANALYSIS_STATUS.IDLE;
    private static ANALYSIS_OUTCOME outcome = ANALYSIS_OUTCOME.NONE;

    private static final Logger LOGGER =
            LoggerFactory.getLogger("analytics");


    public static void setGraphService(GraphService graphService) {
        gs = graphService;
    }


    public static boolean startAnalysis(String jarLocation, List<String> classPrefixes) {
        createGraph(jarLocation, classPrefixes);
        return true;
    }

    public static ANALYSIS_STATUS currentStatus() {
        return status;
    }
    public static ANALYSIS_OUTCOME lastOutcome() {
        return outcome;
    }

    private static void createGraph(String jarLocation, List<String> classPrefixes) {
        analyzeTarget(jarLocation, classPrefixes);
        syncInitializations();
        rollUpMethodInvokes();
        inflatePolymorphicCalls();
        rerouteEdgesToParent();

        //calculateComplexity(); ovo napraviti tek nakon polymorph inflacije
    }

    private static void syncInitializations() {
        gs.persistInitializedClasses(gs.getInitializedClasses());
    }

    private static void rollUpMethodInvokes() {
        gs.rollUpMethodRelations();
    }

    private static void rerouteEdgesToParent() {
        Map<String, MethodNode> candidates = gs.getMethodsDeclaredOnlyInParent();
        candidates.forEach((key,value)->{
            boolean relocated=false;
            MethodNode realLocation = gs.getRealMethodLocationInParent(value);
            if (realLocation!=null) {
                gs.transferCallsToOtherMethod(value,realLocation);
                relocated=true;
            }
            else {
                // try to find in interface
                realLocation = gs.getRealMethodLocationInInterface(value);
                if (realLocation!=null) {
                    gs.transferCallsToOtherMethod(value,realLocation);
                    relocated = true;
                }
            }
            if (relocated) {
                gs.removeMethodNode(value);
            }
        });
    }

    private static void inflatePolymorphicCalls() {
        gs.getPolymorphicNodes().forEach((polyName,polyNode)->{
            // generate candidate list
            ClassNode parentNode = gs.getClassByName(polyNode.getClassName());
            Collection<MethodNode> candidates = gs.getPolymorphicCandidatesForCall(polyNode).values();
            if (candidates.size()>1) gs.createIntermediateCall(polyNode, candidates);
        });
    }

    private static void calculateComplexity() {}


    private static void analyzeTarget(String jarLocation, List<String> classPrefixes) {
        try {
            status = ANALYSIS_STATUS.PARSING_FILES;
            File jar = new File(jarLocation);
            FileInputStream fis = new FileInputStream(jar);
            byte[] bytes=IOUtils.toByteArray(fis);
            fis.close();
            readJar(new JarInputStream(new ByteArrayInputStream(bytes)), classPrefixes);



        } catch(IOException e) {
            status = ANALYSIS_STATUS.IDLE;
            outcome = ANALYSIS_OUTCOME.ERROR;
        }
    }
    public static void readJar(JarInputStream jarInputStream, List<String> classPrefixes) throws IOException {
        JarEntry entry = null;
        while ((entry = jarInputStream.getNextJarEntry()) != null) {
            if (!(entry.getName().endsWith(".class") || entry.getName().endsWith(".jar"))) continue;

            if (entry.getName().endsWith(".jar") && classPrefixes!=null && classPrefixes.size()>0) {
                boolean skip=true;
                Iterator<String> prefixIterator = classPrefixes.iterator();
                while (prefixIterator.hasNext()) {
                    String prefix = prefixIterator.next();
                    if (entry.getName().contains(prefix)) {
                        skip=false;
                        break;
                    }
                }
                if (skip) continue;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            OFFSET=0;
            int bytesRead = jarInputStream.read(BUFFER);

            while (bytesRead != -1) {
                baos.write(BUFFER, 0, bytesRead);
                OFFSET+=bytesRead;
                bytesRead = jarInputStream.read(BUFFER);
            }
            InputStream is = new ByteArrayInputStream(baos.toByteArray());
            baos.close();
            if (entry.getName().endsWith(".class")) {
                System.out.println(entry.getName());
                process(is);
            }
            else if (entry.getName().endsWith(".jar")) {
                readJar(new JarInputStream(is), classPrefixes);
            }
            is.close();
        }
    }
    private static void process(InputStream bytes) {
        try {
            CallGraphClassVisitor cv = new CallGraphClassVisitor(bytes,gs);
            cv.visit();
        } catch (IllegalArgumentException e) {
            System.out.println("Failed to read\n"+e.toString());
        } catch(IOException e) {
            System.out.println("Failed to read\n"+e.toString());
        }
    }
}
