package algebra.diplomski.ivan.controller;


import algebra.diplomski.ivan.controller.dao.DaoOrientDB;
import algebra.diplomski.ivan.controller.service.GraphService;
import algebra.diplomski.ivan.controller.visitors.CallGraphClassVisitor;
import com.tinkerpop.blueprints.impls.orient.OrientGraphFactory;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;


import java.io.*;
import java.util.ArrayList;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Configuration
public class Startup {
    GraphService graphService;

    public static int depth=0;
    private static final Logger LOGGER =
            LoggerFactory.getLogger("analytics");

    private static OrientGraphFactory factory;

    @Bean
    public GraphService graphService() {
        String path = new File("graph/db").getAbsolutePath();
        File x = new File(path);
        boolean isdir=x.isDirectory();
        try {
            FileUtils.deleteDirectory(x);
        } catch (IOException e) {
            e.printStackTrace();
        }
        factory = new OrientGraphFactory("plocal:graph/db").setupPool(1,10);

        graphService=new GraphService(new DaoOrientDB(factory));
        return graphService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void doSomethingAfterStartup() {
        long start = System.currentTimeMillis();
        String jarLocationo = "D:\\dipl\\code\\evomaster-usage\\evomaster.jar";
        jarLocationo = "D:\\dipl\\sourcecode\\controller\\target\\controller.jar";
        ArrayList<String> jarFilter = new ArrayList<>();
        ArrayList<String> prefixList = new ArrayList<>();
        GraphCreationManager.setGraphService(graphService);
        GraphCreationManager.startAnalysis(jarLocationo, prefixList); // TODO
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            long end = System.currentTimeMillis();
            System.out.println("Run time: " + (end - start) / 1000);
        }));
    }

    public void readJar(String location) throws IOException {
        File jar = new File(location);
        FileInputStream fis = new FileInputStream(jar);
        readJar(new JarInputStream(fis));
        fis.close();
    }

    public void readJar(JarInputStream jarInputStream) throws IOException {
        JarEntry entry = null;
        while ((entry = jarInputStream.getNextJarEntry()) != null) {
            if (!(entry.getName().endsWith(".class") || entry.getName().endsWith(".jar"))) continue;
            if (!entry.getName().startsWith("org/evomaster")) continue;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int word = jarInputStream.read();
            while (word != -1) {
                baos.write(word);
                word = jarInputStream.read();
            }
            InputStream is = new ByteArrayInputStream(baos.toByteArray());
            baos.close();
            if (entry.getName().endsWith(".class")) {
                    process(is);
            }
            else if (entry.getName().endsWith(".jar")) {
                readJar(new JarInputStream(is));
            }
            is.close();
        }
    }

    private void process(InputStream bytes) {
        try {
            CallGraphClassVisitor cv = new CallGraphClassVisitor(bytes,graphService);
            cv.visit();
        } catch (IllegalArgumentException e) {
            System.out.println("Failed to read\n"+e.toString());
        } catch(IOException e) {
            System.out.println("Failed to read\n"+e.toString());
        }
    }
}
