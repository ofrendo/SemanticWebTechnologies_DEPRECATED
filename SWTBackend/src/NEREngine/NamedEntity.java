package NEREngine;

import java.util.HashMap;

public class NamedEntity {

	  private String name;
	  private EntityType type;
	  private HashMap<String,HashMap<String, Integer>> properties;
	  private String uri;

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
	    this.properties = new HashMap<String,HashMap<String, Integer>>();
	    this.uri = "";
	  }
	  
	  public NamedEntity(NamedEntity template){
		  super();
		  this.name = template.getName();
		  this.type = template.getType();
		  this.properties = new HashMap<String,HashMap<String, Integer>>();
		  this.addProperties(template.getProperties());
		  this.uri = template.getURI();
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
		return type + " '" + name + "' URI: " + uri + "\n Properties: " + properties;
		  
	  }
	  
	  
	  public String getRegexName(){
		  //return ("(^.{0,5}\\\\s+|^)" + name.replace(".", ".*") + "((\\\\s+.{0,5}$)|$)");
		  return ("(^.{0,10}\\\\s+|^)" + name.replace(".", ".*") + "((\\\\s+.{0,5}(\\\\(.*\\\\))?$)|$)");
	  }
	  
	  //add property values via copy
	  public void addProperties(HashMap<String,HashMap<String, Integer>> p){
		  for (String p_key : p.keySet()) {
			  for (String v_key : p.get(p_key).keySet()) {
				  addPropertyValue(p_key, v_key, p.get(p_key).get(v_key).intValue());
			  }			
		  }
	  }
	  
	  public void addPropertyValue(String p_key, String v_key, Integer count){
		  if(!properties.containsKey(p_key)){
			  //Add new property with initial value list
			  properties.put(p_key, new HashMap<String, Integer>());
		  }
		  if(properties.get(p_key).containsKey(v_key)){
			  //Sum old and new count of property value
			  properties.get(p_key).replace(v_key,properties.get(p_key).get(v_key).intValue() + count);
		  }else{
			  //Add new value to property
			  properties.get(p_key).put(v_key, count);
		  }
	  }
	  
	  public void setURI(String uri){
		  this.uri = uri;
	  }
	  
	  public String getURI(){
		 return this.uri;
	  }
	  
	  public HashMap<String,HashMap<String, Integer>> getProperties(){		  
		  //TODO copy necessary?
		  return properties;
	  }
	  
	  
	  public enum EntityType {
		    PERSON, ORGANIZATION, LOCATION 
		}
}