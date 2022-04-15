# Metaprogramming
An implementation of a simple ORM using annotation processing and AspectJ.

### Supported annotations:
- From `javax.persistence`:
  - @Entity(name)
  - @Id
  - @Column
  - @ManyToOne(fetch)
- Custom:
  - @Atomic

### How to run
- `mvn clean install`
- `mvn exec:java -Dexec.mainClass="mp.example.Main" -f "example\pom.xml"`
