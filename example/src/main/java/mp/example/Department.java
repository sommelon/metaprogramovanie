package mp.example;

import javax.persistence.Entity;
import javax.persistence.Id;

@Entity(name = "oddelenie")
public class Department {
    @Id
	private int id;
    private String name;
    private String code;

    public Department() {
    }

    public Department(String name, String code) {
        this.name = name;
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String toString() {
        return String.format("Department %d: %s (%s)", id, name, code);
    }
}