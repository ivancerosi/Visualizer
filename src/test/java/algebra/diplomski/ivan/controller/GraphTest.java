package algebra.diplomski.ivan.controller;

import algebra.diplomski.ivan.controller.dao.DaoOrientDB;
import algebra.diplomski.ivan.controller.service.GraphService;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OConcurrentLegacyResultSet;
import com.tinkerpop.blueprints.impls.orient.OrientDynaElementIterable;
import com.tinkerpop.blueprints.impls.orient.OrientGraphNoTx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.lang.reflect.Field;
import java.util.Iterator;


@SpringBootTest
public class GraphTest {
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
    public void dependsOnEdge() {
        Iterator<?> iterator=((OrientDynaElementIterable) client.command(new OCommandSQL("SELECT FROM Invokes")).execute()).iterator();
        while (iterator.hasNext()) {
            Object element = iterator.next();
            int x=9;
        }
    }
}
