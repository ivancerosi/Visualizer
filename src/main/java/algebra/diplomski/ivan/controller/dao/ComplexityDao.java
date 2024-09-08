package algebra.diplomski.ivan.controller.dao;

public interface ComplexityDao {
    void recordInstructionComplexity(String className, String methodName, String descriptor, int complexity);
    void recordCyclomaticComplexity(String className, String methodName, String descriptor, int complexity);
}
