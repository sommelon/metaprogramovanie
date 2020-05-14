package mp.persistence.aop;

import mp.persistence.ReflectivePersistenceManager;

public aspect ReflectivePersistenceManagerAspect {
    private ReflectivePersistenceManager persistenceManager;

    public ReflectivePersistenceManager getPersistenceManager() {
//        System.out.println("Persistence manager: "+ persistenceManager);
        return persistenceManager;
    }

    pointcut reflectivePersistenceManagerInstantiation():
            execution(mp.persistence.ReflectivePersistenceManager.new(..));

    after(): reflectivePersistenceManagerInstantiation() {
        if (persistenceManager == null)
            this.persistenceManager = (ReflectivePersistenceManager) thisJoinPoint.getTarget();
    }
}



