package mp.persistence.aop;

import mp.persistence.PersistenceException;

import java.sql.Connection;
import java.sql.SQLException;

public aspect TransactionManagementAspect {

    pointcut atomicMethod():
            execution(@mp.persistence.annotations.Atomic * *(..));

    Object around(): atomicMethod() {
        Connection connection = ReflectivePersistenceManagerAspect.aspectOf().getPersistenceManager().getConnection();
        boolean initialAutoCommitValue = false;
        try {
            initialAutoCommitValue = connection.getAutoCommit();

            connection.setAutoCommit(false);
            Object value = proceed();
            connection.commit();
            System.out.println("committed");

            return value;
        } catch (Exception e) {
            try {
                connection.rollback();
                System.out.println("rolled back");
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            throw new PersistenceException(e.getMessage());
        }
        finally {
            try {
                connection.setAutoCommit(initialAutoCommitValue);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
