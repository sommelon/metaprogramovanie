package mp.example;

import mp.persistence.PersistenceException;
import mp.persistence.PersistenceManager;
import mp.persistence.ReflectivePersistenceManager;
import mp.persistence.annotations.Atomic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class Main {

    private Connection conn;
    private PersistenceManager manager;

    public static void main(String[] args) throws Exception {
        new Main().test();
    }

    void test() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:test.db");
        manager = new ReflectivePersistenceManager(conn);
        manager.createTables();

        Department development = new Department("Development", "DVLP");
        Department marketing = new Department("Marketing", "MARK");

        Person hrasko = new Person("Janko", "Hrasko", 30);
        hrasko.setDepartment(development);
        Person mrkvicka = new Person("Jozko", "Mrkvicka", 25);
        mrkvicka.setDepartment(development);
        Person novak = new Person("Jan", "Novak", 45);
        novak.setDepartment(marketing);

        manager.save(hrasko);
        manager.save(mrkvicka);
        manager.save(novak);

        hrasko = manager.get(Person.class, 1);
        System.out.println("\nGET: "+ hrasko);
        System.out.println("  "+ hrasko.getDepartment());
        hrasko.setAge(31);
        manager.save(hrasko);
        System.out.println("\nALREADY LOADED "+ hrasko.getDepartment());

        System.out.println("\n\n-----------GET ALL-----------");
        List<Person> persons = manager.getAll(Person.class);
        for (Person person : persons) {
            System.out.println(person);
            System.out.println("  " + person.getDepartment());
        }

        tryWithoutException();

        try {
            tryWithException();
        }catch (PersistenceException e){
            e.printStackTrace();
        }

        conn.close();
    }

    @Atomic
    void tryWithoutException() {
        System.out.println("\n\n-----------TESTING COMMIT-----------");
        Department sales = new Department("Sales", "SLS");
        Person hruska = new Person("Peter", "Hruska", 27);
        hruska.setDepartment(sales);
        Person maco = new Person("Marek", "Maco", 48);
        maco.setDepartment(sales);

        manager.save(hruska);
        manager.save(maco);
    }

    @Atomic
    void tryWithException() {
        System.out.println("\n\n-----------TESTING ROLLBACK-----------");
        Department sales = new Department("Sales", "SLS");
        Person hruska = new Person(null, "Hruska", 27);
        hruska.setDepartment(sales);
        Person maco = new Person(null, "Maco", 48);
        maco.setDepartment(sales);

        manager.save(hruska);
        manager.save(maco);
    }
}

