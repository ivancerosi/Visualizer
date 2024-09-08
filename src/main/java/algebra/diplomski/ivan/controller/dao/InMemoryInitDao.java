package algebra.diplomski.ivan.controller.dao;

import java.util.HashSet;
import java.util.Set;

public class InMemoryInitDao implements ClassInitializationDao {
    static Set<String> initializedClasses = new HashSet<>();

    @Override
    public synchronized void recordClassInitialized(String className) {
        initializedClasses.add(className);
    }

    @Override
    public Set<String> getInitializedClasses() {return InMemoryInitDao.initializedClasses;}
}
