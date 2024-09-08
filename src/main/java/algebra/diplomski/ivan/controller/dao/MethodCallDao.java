package algebra.diplomski.ivan.controller.dao;

public interface MethodCallDao {
    void recordMethodCall(String callerClass, String callerMethod, String callerDescriptor, String calleeClass,
                          String calleeMethod, String calleeDescriptor, boolean polymorphic);
}
