package mp.example;

import mp.persistence.PersistenceManager;
import mp.persistence.ReflectivePersistenceManager;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:test.db");

        PersistenceManager manager = new ReflectivePersistenceManager(conn);

        manager.createTables(Person.class, Department.class);

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
        System.out.println(hrasko);
        hrasko.setAge(31);
        manager.save(hrasko);

        List<Person> persons = manager.getAll(Person.class);
        for (Person person : persons) {
            System.out.println(person);
            System.out.println("  " + person.getDepartment());
        }

        conn.close();
    }
}

