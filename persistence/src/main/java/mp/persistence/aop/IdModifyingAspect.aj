package mp.persistence.aop;

public aspect IdModifyingAspect {

    declare error: set(* *) && @annotation(javax.persistence.Id):
            "Changing a field annotated with @Id is not allowed!";

}
