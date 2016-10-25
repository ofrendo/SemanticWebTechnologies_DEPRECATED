package NEREngine;

public class NamedEntity {

	  private String name;
	  private EntityType type;

	  public String getName() {
	    return name;
	  }

	  public EntityType getType() {
	    return type;
	  }

	  public NamedEntity(String name, EntityType type) {
	    super();
	    this.name = name;
	    this.type = type;
	  }
	  
	  public enum EntityType {
		    PERSON, ORGANIZATION, LOCATION 
		}
}