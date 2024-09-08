package algebra.diplomski.ivan.controller.dao;

import java.util.Set;

public interface ClassInitializationDao {
    void recordClassInitialized(String className);
    Set<String> getInitializedClasses();
}
