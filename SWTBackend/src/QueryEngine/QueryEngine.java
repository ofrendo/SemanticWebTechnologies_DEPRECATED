package QueryEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import NEREngine.NamedEntity;
import NEREngine.NamedEntity.EntityType;


public interface QueryEngine {
	public Properties getAvailableProperties();
	public List<String> getAvailableProperties(EntityType type);
	
	//TODO Define return type
	public List<HashMap<String,HashMap<String, Integer>>> queryEntityProperties(List<NamedEntity> entities);
	public List<HashMap<String,HashMap<String, Integer>>> queryEntityProperties(List<NamedEntity> entities, QueryProperties props);
}
