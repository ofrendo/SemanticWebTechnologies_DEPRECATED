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
	  
	  @Override
	  public boolean equals(Object o){
		  if(o != null && o.getClass() == NamedEntity.class){
			  NamedEntity ne = (NamedEntity)o;
			  if(ne.getName().equals(name) && ne.getType() == type){
				  return true;  
			  }
		  }
			  
		return false;
		  
	  }
	  
	  @Override
	  public String toString(){
		return type + " " + name;
		  
	  }
	  
	  
	  public String getRegexName(){
		  //return ("(^.{0,5}\\\\s+|^)" + name.replace(".", ".*") + "((\\\\s+.{0,5}$)|$)");
		  return ("(^.{0,10}\\\\s+|^)" + name.replace(".", ".*") + "((\\\\s+.{0,5}(\\\\(.*\\\\))?$)|$)");
	  }
	  
	  
	  public enum EntityType {
		    PERSON, ORGANIZATION, LOCATION 
		}
}